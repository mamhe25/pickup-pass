package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Production lead flow for the public PickupPass demo-request form.
 *
 * Public submissions are NOT visible to Platform Owners until the requester
 * proves ownership of the supplied email address. A cryptographically random
 * verification token is emailed to the requester; Firestore stores only its
 * SHA-256 digest. The verified lead then enters the normal owner inbox and
 * notification pipeline.
 */
@Service
public class DemoRequestService {

    private static final Logger log = LoggerFactory.getLogger(DemoRequestService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{40,90}$");

    public static final Set<String> STATUSES = Set.of(
            "new",
            "contacted",
            "demo_scheduled",
            "converted",
            "closed");

    private static final Set<String> FREE_EMAIL_DOMAINS = Set.of(
            "gmail.com",
            "googlemail.com",
            "outlook.com",
            "hotmail.com",
            "live.com",
            "yahoo.com",
            "ymail.com",
            "icloud.com",
            "me.com",
            "proton.me",
            "protonmail.com",
            "aol.com",
            "gmx.com",
            "mail.com");

    private static final Set<String> DISPOSABLE_EMAIL_DOMAINS = Set.of(
            "10minutemail.com",
            "guerrillamail.com",
            "guerrillamailblock.com",
            "mailinator.com",
            "maildrop.cc",
            "temp-mail.org",
            "tempmail.com",
            "throwawaymail.com",
            "yopmail.com",
            "getnada.com",
            "sharklasers.com");

    private static final long DUPLICATE_WINDOW_MINUTES = 15;
    private static final int VERIFICATION_EXPIRES_MINUTES = 30;
    private static final int VERIFICATION_RESEND_COOLDOWN_SECONDS = 60;
    private static final int MAX_VERIFICATION_SENDS = 5;
    private static final int MAX_VERIFICATION_ATTEMPTS = 5;
    private static final int PENDING_RETENTION_HOURS = 24;

    private final Firestore firestore;
    private final PushNotificationService pushNotifications;
    private final EmailService emailService;
    private final String frontendBaseUrl;

    public DemoRequestService(
            Firestore firestore,
            PushNotificationService pushNotifications,
            EmailService emailService,
            @Value("${app.frontend-base-url:http://localhost:5500}") String frontendBaseUrl) {
        this.firestore = firestore;
        this.pushNotifications = pushNotifications;
        this.emailService = emailService;
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
    }

    /**
     * Creates or refreshes a pending demo request and sends its verification
     * link. Platform Owners are deliberately NOT notified here.
     */
    public Map<String, Object> submit(Map<String, Object> raw) throws Exception {
        String name = clean(raw == null ? null : raw.get("name"), 100);
        String email = normalizeEmail(raw == null ? null : raw.get("email"));
        String school = clean(raw == null ? null : raw.get("school"), 160);
        String phone = clean(raw == null ? null : raw.get("phone"), 50);
        String message = clean(raw == null ? null : raw.get("message"), 1200);
        String honeypot = clean(raw == null ? null : raw.get("website"), 200);

        // Silent success for basic form bots. Never persist or notify.
        if (!honeypot.isBlank()) {
            return Map.of(
                    "accepted", true,
                    "verificationRequired", true,
                    "message", "Check your email to verify the demo request.");
        }

        validate(name, email, school, phone, message);
        DomainAssessment domain = assessEmailDomain(email);
        String fingerprint = fingerprint(email, school);
        Instant now = Instant.now();
        Instant duplicateCutoff = now.minus(DUPLICATE_WINDOW_MINUTES, ChronoUnit.MINUTES);
        Instant pendingCutoff = now.minus(PENDING_RETENTION_HOURS, ChronoUnit.HOURS);

        QueryDocumentSnapshot reusablePending = null;
        List<QueryDocumentSnapshot> related = firestore.collection("demoRequests")
                .whereEqualTo("fingerprint", fingerprint)
                .limit(10)
                .get()
                .get()
                .getDocuments();

        for (QueryDocumentSnapshot document : related) {
            Timestamp createdAt = document.getTimestamp("createdAt");
            boolean verified = Boolean.TRUE.equals(document.getBoolean("emailVerified"));

            if (verified && isAfter(createdAt, duplicateCutoff)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A recent verified demo request from this email and organization is already on file");
            }

            if (!verified && reusablePending == null && isAfter(createdAt, pendingCutoff)) {
                reusablePending = document;
            }
        }

        DocumentReference ref = reusablePending != null
                ? reusablePending.getReference()
                : firestore.collection("demoRequests").document();
        DocumentSnapshot existing = reusablePending;

        Instant lastSentAt = timestampInstant(existing, "verificationLastSentAt");
        if (lastSentAt != null) {
            long elapsed = Duration.between(lastSentAt, now).getSeconds();
            if (elapsed >= 0 && elapsed < VERIFICATION_RESEND_COOLDOWN_SECONDS) {
                return pendingResponse(ref.getId(), name, email, domain, false);
            }
        }

        long sends = existing == null ? 0L : longValue(existing, "verificationSendCount");
        if (sends >= MAX_VERIFICATION_SENDS) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many verification emails were requested for this inquiry");
        }

        issueVerification(
                ref,
                existing,
                name,
                email,
                school,
                phone,
                message,
                fingerprint,
                domain,
                now,
                sends + 1);

        return pendingResponse(ref.getId(), name, email, domain, true);
    }

    /**
     * Verifies the emailed token and atomically transitions the pending request
     * into the Platform Owner lead inbox. Only this transition emits the owner
     * notification, so unverified/spoofed addresses never create owner noise.
     */
    public Map<String, Object> verify(String requestId, Map<String, Object> raw) throws Exception {
        String id = cleanRequestId(requestId);
        String token = clean(raw == null ? null : raw.get("token"), 120);
        if (!TOKEN.matcher(token).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification link");
        }

        DocumentReference ref = firestore.collection("demoRequests").document(id);
        Instant now = Instant.now();
        String suppliedHash = hashToken(token);

        VerificationOutcome outcome = firestore.runTransaction(transaction -> {
            DocumentSnapshot document = transaction.get(ref).get();
            if (!document.exists()) {
                return VerificationOutcome.missing();
            }

            String name = value(document.getString("name"));
            String email = value(document.getString("email"));
            String school = value(document.getString("school"));
            String domainType = value(document.getString("emailDomainType"));

            if (Boolean.TRUE.equals(document.getBoolean("emailVerified"))) {
                return VerificationOutcome.alreadyVerified(name, email, school, domainType);
            }

            Instant deleteAfter = timestampInstant(document, "pendingDeleteAfter");
            if (deleteAfter != null && !deleteAfter.isAfter(now)) {
                transaction.delete(ref);
                return VerificationOutcome.expired();
            }

            Instant expiresAt = timestampInstant(document, "verificationExpiresAt");
            if (expiresAt == null || !expiresAt.isAfter(now)) {
                return VerificationOutcome.expired();
            }

            long attempts = longValue(document, "verificationAttempts");
            if (attempts >= MAX_VERIFICATION_ATTEMPTS) {
                return VerificationOutcome.locked();
            }

            String expectedHash = value(document.getString("verificationTokenHash"));
            if (!constantTimeEquals(expectedHash, suppliedHash)) {
                long nextAttempts = attempts + 1;
                transaction.update(ref, Map.of(
                        "verificationAttempts", nextAttempts,
                        "statusUpdatedAt", FieldValue.serverTimestamp()));
                return VerificationOutcome.invalid(
                        Math.max(0, MAX_VERIFICATION_ATTEMPTS - (int) nextAttempts));
            }

            Map<String, Object> update = new LinkedHashMap<>();
            update.put("emailVerified", true);
            update.put("emailVerifiedAt", FieldValue.serverTimestamp());
            update.put("verifiedAt", FieldValue.serverTimestamp());
            update.put("status", "new");
            update.put("statusUpdatedAt", FieldValue.serverTimestamp());
            update.put("verificationTokenHash", FieldValue.delete());
            update.put("verificationExpiresAt", FieldValue.delete());
            update.put("verificationAttempts", FieldValue.delete());
            update.put("verificationLastSentAt", FieldValue.delete());
            update.put("verificationSendCount", FieldValue.delete());
            update.put("pendingDeleteAfter", FieldValue.delete());
            transaction.update(ref, update);

            return VerificationOutcome.verified(name, email, school, domainType);
        }).get();

        switch (outcome.state()) {
            case "missing" -> throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Demo request not found");
            case "expired" -> throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "Verification link expired. Request a fresh verification email.");
            case "locked" -> throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Verification is temporarily locked. Request a fresh verification email.");
            case "invalid" -> throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Verification link is invalid");
            case "verified" -> notifyPlatformOwners(id, outcome.name(), outcome.school());
            default -> { }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("verified", true);
        result.put("alreadyVerified", "already_verified".equals(outcome.state()));
        result.put("requestId", id);
        result.put("name", outcome.name());
        result.put("email", outcome.email());
        result.put("status", "new");
        result.put("emailDomainType", outcome.domainType());
        result.put("emailTrustLabel", trustLabel(outcome.domainType()));
        return result;
    }

    public Map<String, Object> resendVerification(String requestId) throws Exception {
        String id = cleanRequestId(requestId);
        DocumentReference ref = firestore.collection("demoRequests").document(id);
        DocumentSnapshot document = ref.get().get();
        if (!document.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo request not found");
        }

        if (Boolean.TRUE.equals(document.getBoolean("emailVerified"))) {
            return Map.of(
                    "accepted", true,
                    "verified", true,
                    "alreadyVerified", true,
                    "requestId", id);
        }

        Instant now = Instant.now();
        Instant deleteAfter = timestampInstant(document, "pendingDeleteAfter");
        if (deleteAfter != null && !deleteAfter.isAfter(now)) {
            ref.delete().get();
            throw new ResponseStatusException(
                    HttpStatus.GONE,
                    "This unverified request expired. Submit a new demo request.");
        }

        Instant lastSentAt = timestampInstant(document, "verificationLastSentAt");
        if (lastSentAt != null) {
            long elapsed = Duration.between(lastSentAt, now).getSeconds();
            if (elapsed >= 0 && elapsed < VERIFICATION_RESEND_COOLDOWN_SECONDS) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Please wait before requesting another verification email");
            }
        }

        long sends = longValue(document, "verificationSendCount");
        if (sends >= MAX_VERIFICATION_SENDS) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many verification emails were requested for this inquiry");
        }

        String email = value(document.getString("email"));
        DomainAssessment domain = assessEmailDomain(email);
        issueVerification(
                ref,
                document,
                value(document.getString("name")),
                email,
                value(document.getString("school")),
                value(document.getString("phone")),
                value(document.getString("message")),
                value(document.getString("fingerprint")),
                domain,
                now,
                sends + 1);

        return pendingResponse(
                id,
                value(document.getString("name")),
                email,
                domain,
                true);
    }

    public Map<String, Object> list() throws Exception {
        // orderBy excludes documents that do not contain verifiedAt, so pending
        // public submissions can never leak into the Platform Owner inbox.
        List<QueryDocumentSnapshot> documents = firestore.collection("demoRequests")
                .orderBy("verifiedAt", Query.Direction.DESCENDING)
                .limit(250)
                .get()
                .get()
                .getDocuments();

        List<Map<String, Object>> requests = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        STATUSES.forEach(status -> counts.put(status, 0));

        for (QueryDocumentSnapshot doc : documents) {
            if (!Boolean.TRUE.equals(doc.getBoolean("emailVerified"))) continue;
            Map<String, Object> item = toResponse(doc);
            requests.add(item);
            String status = String.valueOf(item.get("status"));
            counts.compute(status, (key, value) -> value == null ? 1 : value + 1);
        }

        return Map.of(
                "requests", requests,
                "total", requests.size(),
                "counts", counts);
    }

    public Map<String, Object> updateStatus(
            String requestId,
            String rawStatus,
            String actorUid) throws Exception {
        String status = normalizeStatus(rawStatus);
        DocumentReference ref = firestore.collection("demoRequests").document(requestId);
        DocumentSnapshot before = ref.get().get();
        if (!before.exists() || !Boolean.TRUE.equals(before.getBoolean("emailVerified"))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo request not found");
        }

        String previous = normalizeStatus(before.getString("status"));
        if (previous.equals(status)) {
            Map<String, Object> unchanged = toResponse(before);
            unchanged.put("previousStatus", previous);
            return unchanged;
        }

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("status", status);
        update.put("statusUpdatedAt", FieldValue.serverTimestamp());
        update.put("statusUpdatedBy", actorUid);
        if ("contacted".equals(status)) update.put("contactedAt", FieldValue.serverTimestamp());
        if ("demo_scheduled".equals(status)) update.put("demoScheduledAt", FieldValue.serverTimestamp());
        if ("converted".equals(status)) update.put("convertedAt", FieldValue.serverTimestamp());
        if ("closed".equals(status)) update.put("closedAt", FieldValue.serverTimestamp());
        ref.update(update).get();

        DocumentSnapshot after = ref.get().get();
        Map<String, Object> result = toResponse(after);
        result.put("previousStatus", previous);
        return result;
    }

    /**
     * Privacy cleanup for abandoned verification attempts. Verified leads lose
     * pendingDeleteAfter during verification, so this single-field query only
     * targets unverified public data and needs no composite index.
     */
    @Scheduled(
            initialDelayString = "${pickuppass.demo.pending-cleanup-initial-delay-ms:300000}",
            fixedDelayString = "${pickuppass.demo.pending-cleanup-ms:21600000}")
    public void cleanupExpiredPendingRequests() {
        try {
            List<QueryDocumentSnapshot> expired = firestore.collection("demoRequests")
                    .whereLessThan("pendingDeleteAfter", timestamp(Instant.now()))
                    .limit(100)
                    .get()
                    .get()
                    .getDocuments();
            if (expired.isEmpty()) return;

            WriteBatch batch = firestore.batch();
            for (QueryDocumentSnapshot document : expired) {
                batch.delete(document.getReference());
            }
            batch.commit().get();
        } catch (Exception e) {
            log.warn("Could not clean expired unverified demo requests: {}", e.getMessage());
        }
    }

    static String normalizeStatus(String raw) {
        String status = raw == null
                ? "new"
                : raw.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if (!STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported demo request status");
        }
        return status;
    }

    static String normalizeEmail(Object raw) {
        return raw == null ? "" : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
    }

    static DomainAssessment assessEmailDomain(String email) {
        String normalized = normalizeEmail(email);
        int at = normalized.lastIndexOf('@');
        String domain = at >= 0 && at + 1 < normalized.length()
                ? normalized.substring(at + 1)
                : "";

        if (domain.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid work email");
        }
        if (DISPOSABLE_EMAIL_DOMAINS.contains(domain)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Temporary or disposable email addresses cannot be used for demo requests");
        }

        if ("deped.gov.ph".equals(domain) || domain.endsWith(".gov.ph")) {
            return new DomainAssessment(domain, "government");
        }
        if (domain.endsWith(".edu") || domain.endsWith(".edu.ph") || domain.contains(".edu.")) {
            return new DomainAssessment(domain, "education");
        }
        if (FREE_EMAIL_DOMAINS.contains(domain)) {
            return new DomainAssessment(domain, "free");
        }
        return new DomainAssessment(domain, "custom");
    }

    static String trustLabel(String domainType) {
        return switch (value(domainType)) {
            case "education" -> "Verified education email";
            case "government" -> "Verified government email";
            case "custom" -> "Verified custom-domain email";
            case "free" -> "Verified free email · review organization";
            default -> "Verified email";
        };
    }

    private static void validate(
            String name,
            String email,
            String school,
            String phone,
            String message) {
        if (name.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your name is required");
        }
        if (school.length() < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "School or organization is required");
        }
        if (email.length() < 5 || email.length() > 160 || !EMAIL.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid work email");
        }
        if (phone.length() > 50 || message.length() > 1200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Demo request contains an invalid field");
        }
    }

    private void issueVerification(
            DocumentReference ref,
            DocumentSnapshot existing,
            String name,
            String email,
            String school,
            String phone,
            String message,
            String fingerprint,
            DomainAssessment domain,
            Instant now,
            long sendCount) throws Exception {
        String token = newVerificationToken();
        String verificationLink = frontendBaseUrl
                + "/verify-demo.html#request="
                + ref.getId()
                + "&token="
                + token;

        boolean sent = emailService.sendDemoVerification(
                email,
                name,
                school,
                verificationLink,
                VERIFICATION_EXPIRES_MINUTES);
        if (!sent) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Verification email is temporarily unavailable. Please try again later.");
        }

        Instant createdAt = existing == null
                ? now
                : timestampInstant(existing, "createdAt");
        if (createdAt == null) createdAt = now;

        Map<String, Object> write = new LinkedHashMap<>();
        write.put("name", name);
        write.put("email", email);
        write.put("school", school);
        write.put("phone", phone);
        write.put("message", message);
        write.put("status", "pending_verification");
        write.put("source", "landing_page");
        write.put("fingerprint", fingerprint);
        write.put("emailVerified", false);
        write.put("emailDomain", domain.domain());
        write.put("emailDomainType", domain.type());
        write.put("verificationTokenHash", hashToken(token));
        write.put("verificationExpiresAt", timestamp(now.plus(VERIFICATION_EXPIRES_MINUTES, ChronoUnit.MINUTES)));
        write.put("verificationLastSentAt", timestamp(now));
        write.put("verificationAttempts", 0L);
        write.put("verificationSendCount", sendCount);
        write.put("pendingDeleteAfter", timestamp(createdAt.plus(PENDING_RETENTION_HOURS, ChronoUnit.HOURS)));
        write.put("statusUpdatedAt", FieldValue.serverTimestamp());
        if (existing == null) write.put("createdAt", FieldValue.serverTimestamp());

        ref.set(write, SetOptions.merge()).get();
    }

    private Map<String, Object> pendingResponse(
            String requestId,
            String name,
            String email,
            DomainAssessment domain,
            boolean verificationEmailSent) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("verificationRequired", true);
        result.put("verificationEmailSent", verificationEmailSent);
        result.put("requestId", requestId);
        result.put("name", name);
        result.put("email", email);
        result.put("maskedEmail", maskEmail(email));
        result.put("status", "pending_verification");
        result.put("emailDomainType", domain.type());
        result.put("expiresInSeconds", VERIFICATION_EXPIRES_MINUTES * 60);
        result.put("resendAfterSeconds", VERIFICATION_RESEND_COOLDOWN_SECONDS);
        result.put(
                "message",
                verificationEmailSent
                        ? "Check your email and open the verification link before the request is sent to PickupPass."
                        : "A verification email was sent recently. Check your inbox before requesting another link.");
        return result;
    }

    private void notifyPlatformOwners(String requestId, String name, String school) {
        try {
            List<String> recipientUids = firestore.collection("users")
                    .whereEqualTo("role", "master_admin")
                    .get()
                    .get()
                    .getDocuments()
                    .stream()
                    .filter(owner -> !Boolean.FALSE.equals(owner.getBoolean("isActive")))
                    .map(DocumentSnapshot::getId)
                    .distinct()
                    .toList();

            if (recipientUids.isEmpty()) return;

            pushNotifications.notifyUsers(
                    recipientUids,
                    null,
                    "Verified demo request",
                    name + " from " + school + " verified their email and requested a PickupPass walkthrough.",
                    "demo_request_verified",
                    null,
                    school);
        } catch (Exception ignored) {
            // Lead verification is authoritative. Notification delivery must
            // never roll back a successfully verified inquiry.
        }
    }

    private static Map<String, Object> toResponse(DocumentSnapshot doc) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", doc.getId());
        result.put("name", value(doc.getString("name")));
        result.put("email", value(doc.getString("email")));
        result.put("school", value(doc.getString("school")));
        result.put("phone", value(doc.getString("phone")));
        result.put("message", value(doc.getString("message")));
        result.put("status", normalizeStatus(doc.getString("status")));
        result.put("source", value(doc.getString("source")));
        result.put("emailVerified", Boolean.TRUE.equals(doc.getBoolean("emailVerified")));
        result.put("emailDomain", value(doc.getString("emailDomain")));
        result.put("emailDomainType", value(doc.getString("emailDomainType")));
        result.put("emailTrustLabel", trustLabel(doc.getString("emailDomainType")));
        result.put("createdAt", timestamp(doc, "createdAt"));
        result.put("verifiedAt", timestamp(doc, "verifiedAt"));
        result.put("statusUpdatedAt", timestamp(doc, "statusUpdatedAt"));
        return result;
    }

    private static String cleanRequestId(String raw) {
        String id = clean(raw, 200);
        if (id.isBlank() || id.contains("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid demo request reference");
        }
        return id;
    }

    private static String timestamp(DocumentSnapshot doc, String field) {
        Timestamp value = doc.getTimestamp(field);
        return value == null ? "" : value.toDate().toInstant().toString();
    }

    private static Instant timestampInstant(DocumentSnapshot doc, String field) {
        if (doc == null) return null;
        Timestamp value = doc.getTimestamp(field);
        return value == null ? null : value.toDate().toInstant();
    }

    private static boolean isAfter(Timestamp value, Instant cutoff) {
        return value != null && value.toDate().toInstant().isAfter(cutoff);
    }

    private static long longValue(DocumentSnapshot doc, String field) {
        if (doc == null) return 0L;
        Long value = doc.getLong(field);
        return value == null ? 0L : value;
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.of(Date.from(value));
    }

    private static String clean(Object raw, int max) {
        if (raw == null) return "";
        String value = String.valueOf(raw).trim().replaceAll("\\s+", " ");
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String fingerprint(String email, String school) {
        String raw = email + "|" + school.toLowerCase(Locale.ROOT);
        return hashSha256(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String newVerificationToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hashToken(String token) {
        return hashSha256(token.getBytes(StandardCharsets.UTF_8));
    }

    private static String hashSha256(byte[] input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) return false;
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) return email;
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***@" + domain;
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result.isBlank() ? "http://localhost:5500" : result;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    static record DomainAssessment(String domain, String type) { }

    private record VerificationOutcome(
            String state,
            String name,
            String email,
            String school,
            String domainType,
            int attemptsRemaining) {

        static VerificationOutcome missing() {
            return new VerificationOutcome("missing", "", "", "", "", 0);
        }

        static VerificationOutcome expired() {
            return new VerificationOutcome("expired", "", "", "", "", 0);
        }

        static VerificationOutcome locked() {
            return new VerificationOutcome("locked", "", "", "", "", 0);
        }

        static VerificationOutcome invalid(int attemptsRemaining) {
            return new VerificationOutcome("invalid", "", "", "", "", attemptsRemaining);
        }

        static VerificationOutcome verified(String name, String email, String school, String domainType) {
            return new VerificationOutcome("verified", name, email, school, domainType, MAX_VERIFICATION_ATTEMPTS);
        }

        static VerificationOutcome alreadyVerified(String name, String email, String school, String domainType) {
            return new VerificationOutcome("already_verified", name, email, school, domainType, MAX_VERIFICATION_ATTEMPTS);
        }
    }
}

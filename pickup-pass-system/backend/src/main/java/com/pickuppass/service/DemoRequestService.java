package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Production lead flow for the public PickupPass demo-request form.
 *
 * The browser never writes demo leads directly to Firestore. This service
 * validates/sanitizes public input, suppresses obvious bot/retry spam, stores
 * the lead server-side, and notifies active Platform Owners through the same
 * persisted notification + best-effort FCM pipeline used elsewhere.
 */
@Service
public class DemoRequestService {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE);

    public static final Set<String> STATUSES = Set.of(
            "new",
            "contacted",
            "demo_scheduled",
            "converted",
            "closed");

    private static final long DUPLICATE_WINDOW_MINUTES = 15;

    private final Firestore firestore;
    private final PushNotificationService pushNotifications;

    public DemoRequestService(
            Firestore firestore,
            PushNotificationService pushNotifications) {
        this.firestore = firestore;
        this.pushNotifications = pushNotifications;
    }

    public Map<String, Object> submit(Map<String, Object> raw) throws Exception {
        String name = clean(raw == null ? null : raw.get("name"), 100);
        String email = normalizeEmail(raw == null ? null : raw.get("email"));
        String school = clean(raw == null ? null : raw.get("school"), 160);
        String phone = clean(raw == null ? null : raw.get("phone"), 50);
        String message = clean(raw == null ? null : raw.get("message"), 1200);
        String honeypot = clean(raw == null ? null : raw.get("website"), 200);

        // Silent success for basic form-bot submissions. Do not create a lead
        // or notification, and do not reveal the anti-spam field to the caller.
        if (!honeypot.isBlank()) {
            return Map.of(
                    "accepted", true,
                    "message", "Thanks. Your demo request has been received.");
        }

        validate(name, email, school, phone, message);

        String fingerprint = fingerprint(email, school);
        Instant cutoff = Instant.now().minus(DUPLICATE_WINDOW_MINUTES, ChronoUnit.MINUTES);
        List<QueryDocumentSnapshot> duplicates = firestore.collection("demoRequests")
                .whereEqualTo("fingerprint", fingerprint)
                .limit(5)
                .get()
                .get()
                .getDocuments();

        for (QueryDocumentSnapshot duplicate : duplicates) {
            Timestamp createdAt = duplicate.getTimestamp("createdAt");
            if (createdAt != null && createdAt.toDate().toInstant().isAfter(cutoff)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A recent demo request from this email and organization is already on file");
            }
        }

        DocumentReference ref = firestore.collection("demoRequests").document();
        Map<String, Object> lead = new LinkedHashMap<>();
        lead.put("name", name);
        lead.put("email", email);
        lead.put("school", school);
        lead.put("phone", phone);
        lead.put("message", message);
        lead.put("status", "new");
        lead.put("source", "landing_page");
        lead.put("fingerprint", fingerprint);
        lead.put("createdAt", FieldValue.serverTimestamp());
        lead.put("statusUpdatedAt", FieldValue.serverTimestamp());
        ref.set(lead).get();

        notifyPlatformOwners(ref.getId(), name, school);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("requestId", ref.getId());
        result.put("name", name);
        result.put("email", email);
        result.put("status", "new");
        return result;
    }

    public Map<String, Object> list() throws Exception {
        List<QueryDocumentSnapshot> documents = firestore.collection("demoRequests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(250)
                .get()
                .get()
                .getDocuments();

        List<Map<String, Object>> requests = new ArrayList<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        STATUSES.forEach(status -> counts.put(status, 0));

        for (QueryDocumentSnapshot doc : documents) {
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
        if (!before.exists()) {
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
                    "New demo request",
                    name + " from " + school + " requested a PickupPass walkthrough.",
                    "demo_request_received",
                    null,
                    school);
        } catch (Exception ignored) {
            // Lead persistence is authoritative. Notification delivery must
            // never cause a valid public request to fail.
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
        result.put("createdAt", timestamp(doc, "createdAt"));
        result.put("statusUpdatedAt", timestamp(doc, "statusUpdatedAt"));
        return result;
    }

    private static String timestamp(DocumentSnapshot doc, String field) {
        Timestamp value = doc.getTimestamp(field);
        return value == null ? "" : value.toDate().toInstant().toString();
    }

    private static String clean(Object raw, int max) {
        if (raw == null) return "";
        String value = String.valueOf(raw).trim().replaceAll("\\s+", " ");
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String fingerprint(String email, String school) {
        String raw = email + "|" + school.toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}

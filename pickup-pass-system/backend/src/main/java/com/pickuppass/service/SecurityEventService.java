package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import com.pickuppass.security.FirebaseUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Privacy-preserving security telemetry and materialized alerts for the SaaS operator. */
@Service
public class SecurityEventService {
    private static final int AUTH_FAILURE_ALERT_THRESHOLD = 5;
    private static final int REVOKED_DEVICE_ALERT_THRESHOLD = 3;
    private static final long SECURITY_WINDOW_SECONDS = 900L;
    private final Firestore firestore;
    private final byte[] fingerprintSecret;

    public SecurityEventService(Firestore firestore,
            @Value("${pickuppass.security.fingerprint-secret:development-security-fingerprint-secret}") String secret) {
        this.firestore = firestore;
        this.fingerprintSecret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public void recordInvalidToken(HttpServletRequest request, String reason) {
        try {
            String fp = requestFingerprint(request);
            long bucketStart = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond() / 900 * 900;
            String bucketId = sha256("auth_failure:" + fp + ":" + bucketStart);
            DocumentReference ref = firestore.collection("securityAuthWindows").document(bucketId);
            final long[] count = {0};
            firestore.runTransaction(tx -> {
                DocumentSnapshot d = tx.get(ref).get();
                long next = d.exists() && d.getLong("count") != null ? d.getLong("count") + 1 : 1;
                count[0] = next;
                Map<String,Object> data = new HashMap<>();
                data.put("fingerprint", fp);
                data.put("count", next);
                data.put("windowStart", new Date(bucketStart * 1000));
                // Retention hint for an optional Firestore TTL policy. This is telemetry,
                // not an audit ledger, so old aggregation windows do not need to live forever.
                data.put("expiresAt", Date.from(Instant.ofEpochSecond(bucketStart).plus(7, ChronoUnit.DAYS)));
                data.put("lastSeenAt", FieldValue.serverTimestamp());
                data.put("path", safePath(request));
                data.put("reason", safe(reason, 80));
                if (!d.exists()) data.put("firstSeenAt", FieldValue.serverTimestamp());
                tx.set(ref, data, SetOptions.merge());
                return null;
            }).get();
            if (count[0] >= AUTH_FAILURE_ALERT_THRESHOLD) {
                upsertAlert("repeated_invalid_token", "high", null, null, null,
                        "Repeated invalid authentication tokens",
                        "Repeated invalid Firebase token attempts were detected from the same privacy-preserving request fingerprint.",
                        "security", fp, Map.of("occurrencesInWindow", count[0], "path", safePath(request)));
            }
        } catch (Exception ignored) { }
    }

    public void recordAccessDenied(FirebaseUserDetails user, HttpServletRequest request) {
        if (user == null) return;
        String path = safePath(request);
        if (!(path.startsWith("/api/master-admin") || path.startsWith("/api/school-admin"))) return;
        try {
            upsertAlert("privileged_access_denied", "medium", user.getSchoolId(), user.getUid(), user.getRole(),
                    "Privileged API access denied",
                    "An authenticated account attempted an administrative endpoint that its role cannot access.",
                    "security", sha256(user.getUid()+":"+path), Map.of("path", path, "method", request.getMethod()));
        } catch (Exception ignored) { }
    }

    public void recordRevokedDeviceAttempt(
            FirebaseUserDetails user,
            HttpServletRequest request,
            String deviceId) {

        if (user == null) return;

        try {
            String normalizedDeviceId = safe(deviceId, 128);
            String deviceFingerprint = sha256(normalizedDeviceId);
            long bucketStart =
                    Instant.now().getEpochSecond()
                            / SECURITY_WINDOW_SECONDS
                            * SECURITY_WINDOW_SECONDS;

            String bucketId =
                    sha256(
                            "revoked_device:"
                                    + user.getUid()
                                    + ":"
                                    + deviceFingerprint
                                    + ":"
                                    + bucketStart);

            DocumentReference ref =
                    firestore.collection("securityRevokedDeviceWindows")
                            .document(bucketId);

            final long[] count = {0};

            firestore.runTransaction(tx -> {
                DocumentSnapshot d = tx.get(ref).get();
                long next =
                        d.exists() && d.getLong("count") != null
                                ? d.getLong("count") + 1
                                : 1;

                count[0] = next;

                Map<String, Object> data = new HashMap<>();
                data.put("uid", user.getUid());
                data.put("schoolId", user.getSchoolId());
                data.put("role", user.getRole());
                data.put("deviceFingerprint", deviceFingerprint);
                data.put("count", next);
                data.put(
                        "windowStart",
                        new Date(bucketStart * 1000));
                data.put(
                        "expiresAt",
                        Date.from(
                                Instant.ofEpochSecond(bucketStart)
                                        .plus(7, ChronoUnit.DAYS)));
                data.put("lastSeenAt", FieldValue.serverTimestamp());
                data.put("path", safePath(request));

                if (!d.exists()) {
                    data.put(
                            "firstSeenAt",
                            FieldValue.serverTimestamp());
                }

                tx.set(ref, data, SetOptions.merge());
                return null;
            }).get();

            if (shouldSurfaceRevokedDeviceAlert(count[0])) {
                upsertAlert(
                        "revoked_device_attempt",
                        "high",
                        user.getSchoolId(),
                        user.getUid(),
                        user.getRole(),
                        "Revoked device repeatedly attempted access",
                        "A revoked device identity continued to call authenticated PickupPass APIs after revocation was enforced. A single post-revocation request is expected and is not surfaced as an alert.",
                        "revoke_sessions",
                        sha256(
                                user.getUid()
                                        + ":"
                                        + deviceFingerprint),
                        Map.of(
                                "path",
                                safePath(request),
                                "deviceFingerprint",
                                deviceFingerprint,
                                "occurrencesInWindow",
                                count[0]));
            }
        } catch (Exception ignored) { }
    }

    public void recordRateLimit(HttpServletRequest request, String policyName) {
        try {
            String fp = requestFingerprint(request);
            upsertAlert("rate_limit_exceeded", "medium", null, null, null,
                    "API rate limit exceeded",
                    "A client exceeded a protected PickupPass API rate limit.",
                    "security", sha256(policyName+":"+fp), Map.of("policy", policyName, "path", safePath(request)));
        } catch (Exception ignored) { }
    }

    public Map<String,Object> overview(int limit) throws Exception {
        int safeLimit = Math.max(10, Math.min(limit, 200));
        List<Map<String,Object>> alerts = new ArrayList<>();
        QuerySnapshot open = firestore.collection("securityAlerts").whereEqualTo("status", "open").get().get();
        QuerySnapshot acknowledged = firestore.collection("securityAlerts").whereEqualTo("status", "acknowledged").get().get();
        int critical = 0;
        int high = 0;
        int medium = 0;
        int openAlerts = 0;
        int acknowledgedAlerts = 0;

        for (DocumentSnapshot d :
                concat(
                        open.getDocuments(),
                        acknowledged.getDocuments())) {

            Map<String, Object> item = normalize(d);

            if (shouldSuppressLegacyRevokedDeviceAlert(item)) {
                continue;
            }

            String status =
                    Objects.toString(
                            item.get("status"),
                            "open");

            if ("acknowledged".equals(status)) {
                acknowledgedAlerts++;
            } else {
                openAlerts++;
            }

            String sev =
                    Objects.toString(
                            item.get("severity"),
                            "medium");

            if ("critical".equals(sev)) {
                critical++;
            } else if ("high".equals(sev)) {
                high++;
            } else {
                medium++;
            }

            alerts.add(item);
        }

        alerts.sort(
                (x, y) ->
                        Objects.toString(
                                        y.get("lastSeenAt"),
                                        "")
                                .compareTo(
                                        Objects.toString(
                                                x.get("lastSeenAt"),
                                                "")));

        int activeAlerts = alerts.size();

        if (alerts.size() > safeLimit) {
            alerts =
                    new ArrayList<>(
                            alerts.subList(
                                    0,
                                    safeLimit));
        }

        List<Map<String,Object>> actions = new ArrayList<>();
        QuerySnapshot s = firestore.collection("systemAuditEvents").orderBy("timestamp", Query.Direction.DESCENDING).limit(safeLimit).get().get();
        for (DocumentSnapshot d : s.getDocuments()) actions.add(normalize(d));

        Map<String,Object> metrics = new LinkedHashMap<>();
        metrics.put("activeAlerts", activeAlerts);
        metrics.put("openAlerts", openAlerts);
        metrics.put("acknowledged", acknowledgedAlerts);
        metrics.put("critical", critical);
        metrics.put("high", high);
        metrics.put("medium", medium);
        return Map.of("generatedAt", Instant.now().toString(),
                "metrics", metrics,
                "alerts", alerts, "recentPrivilegedActions", actions);
    }

    public boolean setAlertStatus(String alertId, String status, FirebaseUserDetails actor, String note) throws Exception {
        if (!Set.of("open","acknowledged","resolved").contains(status)) throw new IllegalArgumentException("Invalid status");
        DocumentReference ref = firestore.collection("securityAlerts").document(alertId);
        DocumentSnapshot d = ref.get().get();
        if (!d.exists()) return false;
        Map<String,Object> update = new HashMap<>();
        update.put("status", status);
        update.put("statusUpdatedAt", FieldValue.serverTimestamp());
        update.put("statusUpdatedBy", actor == null ? "system" : actor.getUid());
        update.put("statusNote", safe(note, 500));
        ref.update(update).get();
        return true;
    }

    private void upsertAlert(String type, String severity, String schoolId, String uid, String role,
                             String title, String message, String action, String dedupeKey, Map<String,Object> details) throws Exception {
        String id = sha256(type+":"+dedupeKey);
        DocumentReference ref = firestore.collection("securityAlerts").document(id);
        firestore.runTransaction(tx -> {
            DocumentSnapshot d = tx.get(ref).get();
            long occurrences = d.exists() && d.getLong("occurrences") != null ? d.getLong("occurrences") + 1 : 1;
            Map<String,Object> data = new HashMap<>();
            data.put("type", type); data.put("severity", severity); data.put("schoolId", schoolId);
            data.put("uid", uid); data.put("role", role); data.put("title", title); data.put("message", message);
            data.put("action", action); data.put("details", details == null ? Map.of() : details);
            data.put("status", "open"); data.put("occurrences", occurrences); data.put("lastSeenAt", FieldValue.serverTimestamp());
            if (!d.exists()) data.put("firstSeenAt", FieldValue.serverTimestamp());
            tx.set(ref, data, SetOptions.merge());
            return null;
        }).get();
    }


    static boolean shouldSurfaceRevokedDeviceAlert(
            long occurrencesInWindow) {
        return occurrencesInWindow
                >= REVOKED_DEVICE_ALERT_THRESHOLD;
    }

    static boolean shouldSuppressLegacyRevokedDeviceAlert(
            Map<String, Object> item) {

        if (!"revoked_device_attempt".equals(
                Objects.toString(
                        item.get("type"),
                        ""))) {
            return false;
        }

        Object detailsValue = item.get("details");
        if (detailsValue instanceof Map<?, ?> details) {
            Object countValue =
                    details.get("occurrencesInWindow");

            if (countValue instanceof Number count) {
                return !shouldSurfaceRevokedDeviceAlert(
                        count.longValue());
            }
        }

        Object occurrencesValue =
                item.get("occurrences");

        long occurrences =
                occurrencesValue instanceof Number count
                        ? count.longValue()
                        : 1L;

        return !shouldSurfaceRevokedDeviceAlert(
                occurrences);
    }


    private static List<DocumentSnapshot> concat(List<? extends DocumentSnapshot> first, List<? extends DocumentSnapshot> second) {
        List<DocumentSnapshot> out = new ArrayList<>(first.size() + second.size());
        out.addAll(first);
        out.addAll(second);
        return out;
    }

    private Map<String,Object> normalize(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>(d.getData());
        m.put("id", d.getId());
        for (String k : List.of("timestamp","firstSeenAt","lastSeenAt","statusUpdatedAt")) {
            Object v = m.get(k); if (v instanceof Timestamp t) m.put(k, t.toDate().toInstant().toString());
        }
        return m;
    }
    private String requestFingerprint(HttpServletRequest r) { return hmac(clientIp(r)+"|"+safe(r.getHeader("User-Agent"),240)); }
    private String clientIp(HttpServletRequest r) {
        String f=r.getHeader("X-Forwarded-For"); if (f!=null&&!f.isBlank()) return f.split(",")[0].trim();
        return r.getRemoteAddr()==null?"unknown":r.getRemoteAddr();
    }
    private String safePath(HttpServletRequest r) { return safe(r.getRequestURI(),240); }
    private String hmac(String value) {
        try { Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(fingerprintSecret,"HmacSHA256")); return hex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){ throw new IllegalStateException("Could not fingerprint security telemetry", e); }
    }
    private static String sha256(String value) {
        try { var md=java.security.MessageDigest.getInstance("SHA-256"); return hex(md.digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){ throw new IllegalStateException(e); }
    }
    private static String hex(byte[] b){ StringBuilder s=new StringBuilder(); for(byte x:b)s.append(String.format("%02x",x)); return s.toString(); }
    private static String safe(String v,int max){ if(v==null)return""; String x=v.trim(); return x.length()<=max?x:x.substring(0,max); }
}

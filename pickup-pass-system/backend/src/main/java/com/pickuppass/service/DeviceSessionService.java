package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Tracks app installations as revocable sessions. A Firebase ID token can remain valid
 * for a short period after issue, so PickupPass also checks this server-side device
 * registry for requests that provide X-Device-Id.
 */
@Service
public class DeviceSessionService {
    private static final long LAST_SEEN_WRITE_INTERVAL_SECONDS = 300;
    private final Firestore firestore;

    public DeviceSessionService(Firestore firestore) {
        this.firestore = firestore;
    }

    public ValidationResult validateAndTouch(String uid, String schoolId, String role,
                                             String deviceId, String deviceName, String clientVersion) throws Exception {
        if (deviceId == null || deviceId.isBlank()) return ValidationResult.allowed();
        String normalized = normalizeDeviceId(deviceId);
        DocumentReference ref = firestore.collection("deviceSessions").document(docId(uid, normalized));
        DocumentSnapshot doc = ref.get().get();
        if (doc.exists() && doc.getTimestamp("revokedAt") != null) {
            return ValidationResult.revoked();
        }

        if (!doc.exists()) {
            Map<String,Object> data = new HashMap<>();
            data.put("uid", uid);
            data.put("schoolId", schoolId);
            data.put("role", role);
            data.put("deviceId", normalized);
            data.put("deviceName", safe(deviceName, 120));
            data.put("clientVersion", safe(clientVersion, 40));
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("lastSeenAt", FieldValue.serverTimestamp());
            data.put("revokedAt", null);
            ref.set(data).get();
        } else if (shouldRefreshLastSeen(doc.getTimestamp("lastSeenAt"))) {
            Map<String,Object> updates = new HashMap<>();
            updates.put("lastSeenAt", FieldValue.serverTimestamp());
            updates.put("deviceName", safe(deviceName, 120));
            updates.put("clientVersion", safe(clientVersion, 40));
            ref.update(updates).get();
        }
        return ValidationResult.allowed();
    }

    public List<Map<String,Object>> listForUser(String uid, String currentDeviceId) throws Exception {
        String current = currentDeviceId == null ? "" : normalizeDeviceId(currentDeviceId);
        List<Map<String,Object>> out = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("deviceSessions")
                .whereEqualTo("uid", uid).get().get().getDocuments()) {
            Map<String,Object> item = new LinkedHashMap<>();
            String deviceId = Objects.toString(doc.getString("deviceId"), "");
            item.put("deviceId", deviceId);
            item.put("deviceName", Objects.toString(doc.getString("deviceName"), "Unknown device"));
            item.put("clientVersion", Objects.toString(doc.getString("clientVersion"), ""));
            item.put("createdAt", date(doc.getTimestamp("createdAt")));
            item.put("lastSeenAt", date(doc.getTimestamp("lastSeenAt")));
            item.put("revokedAt", date(doc.getTimestamp("revokedAt")));
            item.put("current", !current.isBlank() && current.equals(deviceId));
            item.put("active", doc.getTimestamp("revokedAt") == null);
            out.add(item);
        }
        out.sort((a,b) -> compareDateDesc((Date)a.get("lastSeenAt"),(Date)b.get("lastSeenAt")));
        return out;
    }

    public boolean revokeOwnDevice(String uid, String deviceId) throws Exception {
        String normalized = normalizeDeviceId(deviceId);
        DocumentReference ref = firestore.collection("deviceSessions").document(docId(uid, normalized));
        DocumentSnapshot doc = ref.get().get();
        if (!doc.exists() || !uid.equals(doc.getString("uid"))) return false;
        ref.update("revokedAt", FieldValue.serverTimestamp(), "revokedReason", "user_revoked").get();
        return true;
    }

    public int revokeOtherDevices(String uid, String currentDeviceId) throws Exception {
        String current = currentDeviceId == null ? "" : normalizeDeviceId(currentDeviceId);
        int count = 0;
        for (QueryDocumentSnapshot doc : firestore.collection("deviceSessions")
                .whereEqualTo("uid", uid).get().get().getDocuments()) {
            if (doc.getTimestamp("revokedAt") != null) continue;
            if (!current.isBlank() && current.equals(doc.getString("deviceId"))) continue;
            doc.getReference().update("revokedAt", FieldValue.serverTimestamp(), "revokedReason", "user_revoked_others").get();
            count++;
        }
        return count;
    }

    private static boolean shouldRefreshLastSeen(Timestamp ts) {
        if (ts == null) return true;
        return Instant.now().getEpochSecond() - ts.getSeconds() >= LAST_SEEN_WRITE_INTERVAL_SECONDS;
    }

    private static Date date(Timestamp ts) { return ts == null ? null : ts.toDate(); }
    private static int compareDateDesc(Date a, Date b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }
    private static String normalizeDeviceId(String value) {
        String v = value == null ? "" : value.trim();
        if (!v.matches("[A-Za-z0-9._-]{16,128}")) throw new IllegalArgumentException("Invalid device id");
        return v;
    }
    private static String safe(String value, int max) {
        if (value == null) return "";
        String v = value.trim();
        return v.length() <= max ? v : v.substring(0,max);
    }
    private static String docId(String uid, String deviceId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((uid + ":" + deviceId).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public record ValidationResult(boolean allowed, boolean revoked) {
        public static ValidationResult allowed() { return new ValidationResult(true,false); }
        public static ValidationResult revoked() { return new ValidationResult(false,true); }
    }
}

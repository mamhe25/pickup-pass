package com.pickuppass.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.exception.ConflictException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Stores replay-safe results for mutating API calls that may be retried after a
 * network timeout. Idempotency scope is tenant + actor + operation + key.
 */
@Service
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 128;
    private final Firestore firestore;

    public IdempotencyService(Firestore firestore) {
        this.firestore = firestore;
    }

    public Optional<String> findExisting(String schoolId,
                                         String actorUid,
                                         String operation,
                                         String key,
                                         String requestFingerprint)
            throws ExecutionException, InterruptedException {
        if (isMissing(key)) return Optional.empty();
        validateKey(key);

        DocumentSnapshot snapshot = ref(schoolId, actorUid, operation, key).get().get();
        if (!snapshot.exists()) return Optional.empty();

        String storedFingerprint = snapshot.getString("requestFingerprint");
        if (storedFingerprint != null && !storedFingerprint.equals(requestFingerprint)) {
            throw new ConflictException("Idempotency-Key was already used for a different request");
        }
        String exitLogId = snapshot.getString("exitLogId");
        return exitLogId == null || exitLogId.isBlank() ? Optional.empty() : Optional.of(exitLogId);
    }

    public void storeResult(String schoolId,
                            String actorUid,
                            String operation,
                            String key,
                            String requestFingerprint,
                            String exitLogId)
            throws ExecutionException, InterruptedException {
        if (isMissing(key)) return;
        validateKey(key);

        DocumentReference ref = ref(schoolId, actorUid, operation, key);
        firestore.runTransaction(tx -> {
            DocumentSnapshot existing = tx.get(ref).get();
            if (existing.exists()) {
                String storedFingerprint = existing.getString("requestFingerprint");
                if (storedFingerprint != null && !storedFingerprint.equals(requestFingerprint)) {
                    throw new ConflictException("Idempotency-Key was already used for a different request");
                }
                return null;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("schoolId", schoolId);
            data.put("actorUid", actorUid);
            data.put("operation", operation);
            data.put("requestFingerprint", requestFingerprint);
            data.put("exitLogId", exitLogId);
            data.put("createdAt", FieldValue.serverTimestamp());
            // Safe TTL candidate: this record only protects a short network-retry window.
            // Configure Firestore TTL on expiresAt rather than running destructive cleanup in-app.
            data.put("expiresAt", Date.from(Instant.now().plus(7, ChronoUnit.DAYS)));
            tx.set(ref, data);
            return null;
        }).get();
    }

    public String fingerprint(String value) {
        return sha256(value == null ? "" : value);
    }

    private DocumentReference ref(String schoolId, String actorUid, String operation, String key) {
        String scope = schoolId + "\n" + actorUid + "\n" + operation + "\n" + key;
        return firestore.collection("idempotencyKeys").document(sha256(scope));
    }

    private void validateKey(String key) {
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key must be 128 characters or fewer");
        }
    }

    private boolean isMissing(String key) {
        return key == null || key.isBlank();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}

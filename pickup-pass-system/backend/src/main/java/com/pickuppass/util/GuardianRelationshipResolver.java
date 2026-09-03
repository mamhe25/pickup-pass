package com.pickuppass.util;

import com.google.cloud.firestore.DocumentSnapshot;

import java.util.List;
import java.util.Map;

/**
 * Resolves the protected primary-guardian relationship for both current and
 * historical PickupPass student records.
 *
 * guardianUids is the authoritative linked-guardian list. Current records use
 * guardians.{uid}.isPrimary=true. Historical records can predate that field,
 * so the first linked guardian remains the guardian of record only when its
 * entry is absent or does not contain isPrimary. Explicit false is never
 * promoted to primary.
 *
 * This class is read-only and never mutates Firestore.
 */
public final class GuardianRelationshipResolver {

    private GuardianRelationshipResolver() {
    }

    @SuppressWarnings("unchecked")
    public static String resolvePrimaryUid(DocumentSnapshot student) {
        if (student == null || !student.exists()) return null;

        List<String> guardianUids =
                (List<String>) student.get("guardianUids");
        Map<String, Object> guardians =
                (Map<String, Object>) student.get("guardians");

        return resolvePrimaryUid(guardianUids, guardians);
    }

    static String resolvePrimaryUid(
            List<String> guardianUids,
            Map<String, Object> guardians) {

        if (guardianUids == null || guardianUids.isEmpty()) return null;

        // An explicit primary wins, regardless of guardianUids ordering.
        for (String uid : guardianUids) {
            if (uid == null || uid.isBlank()) continue;
            Object raw = guardians == null ? null : guardians.get(uid);
            if (raw instanceof Map<?, ?> entry
                    && Boolean.TRUE.equals(entry.get("isPrimary"))) {
                return uid;
            }
        }

        // Historical PickupPass onboarding established the first linked
        // guardian before isPrimary metadata existed.
        for (String uid : guardianUids) {
            if (uid == null || uid.isBlank()) continue;

            Object raw = guardians == null ? null : guardians.get(uid);
            if (raw instanceof Map<?, ?> entry
                    && entry.containsKey("isPrimary")
                    && Boolean.FALSE.equals(entry.get("isPrimary"))) {
                return null;
            }

            return uid;
        }

        return null;
    }

    public static boolean hasPrimaryGuardian(DocumentSnapshot student) {
        return resolvePrimaryUid(student) != null;
    }
}

package com.pickuppass.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GuardianRelationshipResolverTest {

    @Test
    void explicitPrimaryWinsEvenWhenItIsNotFirst() {
        Map<String, Object> guardians = new LinkedHashMap<>();
        guardians.put("backup", Map.of("isPrimary", false));
        guardians.put("primary", Map.of("isPrimary", true));

        assertEquals(
                "primary",
                GuardianRelationshipResolver.resolvePrimaryUid(
                        List.of("backup", "primary"),
                        guardians));
    }

    @Test
    void guardianUidsOnlyLegacyRecordPreservesPrimary() {
        assertEquals(
                "legacy-primary",
                GuardianRelationshipResolver.resolvePrimaryUid(
                        List.of("legacy-primary"),
                        Map.of()));
    }

    @Test
    void legacyEntryWithoutIsPrimaryPreservesPrimary() {
        Map<String, Object> guardians = new LinkedHashMap<>();
        guardians.put(
                "legacy-primary",
                Map.of("relationship", "parent/guardian"));

        assertEquals(
                "legacy-primary",
                GuardianRelationshipResolver.resolvePrimaryUid(
                        List.of("legacy-primary"),
                        guardians));
    }

    @Test
    void explicitFalseIsNeverPromotedAsLegacyPrimary() {
        assertNull(
                GuardianRelationshipResolver.resolvePrimaryUid(
                        List.of("backup-only"),
                        Map.of(
                                "backup-only",
                                Map.of("isPrimary", false))));
    }

    @Test
    void emptyLinkedGuardianListHasNoPrimary() {
        assertNull(
                GuardianRelationshipResolver.resolvePrimaryUid(
                        List.of(),
                        Map.of("orphan", Map.of("isPrimary", true))));
    }
}

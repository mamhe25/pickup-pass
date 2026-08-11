package com.pickuppass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class FirestoreDisasterRecoveryServiceTest {

    @Test
    void disabledIntegrationReturnsSafeReadinessWithoutCloudCall() {
        FirestoreDisasterRecoveryService service = new FirestoreDisasterRecoveryService(
                mock(Firestore.class), new ObjectMapper(), mock(AuditService.class),
                false, false, "", "(default)", "", 14, 84, "SUNDAY", 48);

        Map<String, Object> overview = service.overview();
        assertFalse((Boolean) overview.get("enabled"));
        assertFalse((Boolean) overview.get("configured"));
        assertEquals(48, overview.get("maxBackupAgeHours"));
        assertEquals(List.of(), overview.get("backups"));
    }

    @Test
    void disabledIntegrationCannotApplyProtection() {
        FirestoreDisasterRecoveryService service = new FirestoreDisasterRecoveryService(
                mock(Firestore.class), new ObjectMapper(), mock(AuditService.class),
                false, false, "", "(default)", "", 14, 84, "SUNDAY", 48);

        assertThrows(IllegalStateException.class,
                () -> service.applyRecommendedProtection("ENABLE BACKUP PROTECTION", null));
    }

    @Test
    void retentionRecommendationsAreClampedToFirestoreSupportedWindow() {
        FirestoreDisasterRecoveryService service = new FirestoreDisasterRecoveryService(
                mock(Firestore.class), new ObjectMapper(), mock(AuditService.class),
                false, false, "", "(default)", "", 500, 500, "not-a-day", 48);

        Map<String, Object> overview = service.overview();
        assertEquals(98, overview.get("recommendedDailyRetentionDays"));
        assertEquals(98, overview.get("recommendedWeeklyRetentionDays"));
        assertEquals("SUNDAY", overview.get("recommendedWeeklyDay"));
    }
}

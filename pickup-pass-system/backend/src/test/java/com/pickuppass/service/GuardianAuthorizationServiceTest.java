package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuardianAuthorizationServiceTest {

    private final GuardianAuthorizationService service = new GuardianAuthorizationService("Asia/Manila");

    @Test
    void permanentGuardianIsAllowed() {
        DocumentSnapshot student = studentWithGuardian(Map.of(
                "authorizationType", "permanent",
                "relationship", "parent/guardian"
        ));

        var result = service.check(student, "guardian-1");

        assertTrue(result.allowed());
        assertFalse(result.temporary());
    }


    @Test
    void scheduledPermanentGuardianIsAllowedOnConfiguredDay() {
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Manila")).getDayOfWeek().name();
        DocumentSnapshot student = studentWithGuardian(Map.of(
                "authorizationType", "permanent",
                "pickupScheduleEnabled", true,
                "pickupDays", List.of(today)
        ));

        var result = service.check(student, "guardian-1");
        assertTrue(result.allowed());
    }

    @Test
    void scheduledPermanentGuardianIsDeniedOnOtherDay() {
        String today = LocalDate.now(java.time.ZoneId.of("Asia/Manila")).getDayOfWeek().name();
        String otherDay = java.time.DayOfWeek.valueOf(today).plus(1).name();
        DocumentSnapshot student = studentWithGuardian(Map.of(
                "authorizationType", "permanent",
                "pickupScheduleEnabled", true,
                "pickupDays", List.of(otherDay)
        ));

        var result = service.check(student, "guardian-1");
        assertFalse(result.allowed());
        assertTrue(result.reason().contains("not authorized"));
    }

    @Test
    void temporaryGuardianIsAllowedOnlyOnAuthorizedDate() {
        DocumentSnapshot student = studentWithGuardian(Map.of(
                "authorizationType", "temporary",
                "validDate", LocalDate.now(java.time.ZoneId.of("Asia/Manila")).toString(),
                "remainingUses", 1
        ));

        var result = service.check(student, "guardian-1");

        assertTrue(result.allowed());
        assertTrue(result.temporary());
    }

    @Test
    void expiredTemporaryGuardianIsDenied() {
        DocumentSnapshot student = studentWithGuardian(Map.of(
                "authorizationType", "temporary",
                "validDate", LocalDate.now(java.time.ZoneId.of("Asia/Manila")).minusDays(1).toString(),
                "remainingUses", 1
        ));

        var result = service.check(student, "guardian-1");

        assertFalse(result.allowed());
        assertTrue(result.reason().toLowerCase().contains("expired"));
    }

    @Test
    void consumedTemporaryGuardianIsDenied() {
        DocumentSnapshot student = studentWithGuardian(Map.of(
                "authorizationType", "temporary",
                "validDate", LocalDate.now(java.time.ZoneId.of("Asia/Manila")).toString(),
                "remainingUses", 0
        ));

        var result = service.check(student, "guardian-1");

        assertFalse(result.allowed());
        assertTrue(result.reason().toLowerCase().contains("already been used"));
    }

    private DocumentSnapshot studentWithGuardian(Map<String, Object> guardianEntry) {
        DocumentSnapshot student = mock(DocumentSnapshot.class);
        when(student.exists()).thenReturn(true);
        when(student.get("guardianUids")).thenReturn(List.of("guardian-1"));
        when(student.get("guardians")).thenReturn(Map.of("guardian-1", guardianEntry));
        return student;
    }
}

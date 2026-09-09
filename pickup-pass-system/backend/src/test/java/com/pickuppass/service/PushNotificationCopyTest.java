package com.pickuppass.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PushNotificationCopyTest {

    @Test
    void structuredFirstNameIsPreferred() {
        assertEquals(
                "Mary Ann",
                PushNotificationService.firstNameForNotification(
                        " Mary Ann ",
                        "Dela Cruz, Mary Ann P.",
                        "Guardian"));
    }

    @Test
    void legacyLastNameFirstDisplayUsesOnlyFirstName() {
        assertEquals(
                "Juan",
                PushNotificationService.firstNameForNotification(
                        null,
                        "Dela Cruz, Juan Miguel P.",
                        "Student"));
    }

    @Test
    void legacyFirstNameFirstDisplayUsesOnlyFirstToken() {
        assertEquals(
                "Juan",
                PushNotificationService.firstNameForNotification(
                        "",
                        "Juan Miguel Dela Cruz",
                        "Student"));
    }

    @Test
    void pickerReceivesSecondPersonPickupCopy() {
        String body =
                PushNotificationService.pickupBody(
                        "Juan",
                        "Maria",
                        true,
                        false);

        assertEquals(
                "You picked up Juan from school.",
                body);
        assertFalse(body.contains("Maria"));
    }

    @Test
    void otherGuardianReceivesFirstNamesOnlyPickupCopy() {
        assertEquals(
                "Maria picked up Juan from school.",
                PushNotificationService.pickupBody(
                        "Juan",
                        "Maria",
                        false,
                        false));

        assertEquals(
                "Juan has been picked up",
                PushNotificationService.pickupTitle(
                        "Juan",
                        false));
    }

    @Test
    void prelaunchCopyRemainsExplicitlyTestOnly() {
        assertEquals(
                "Pre-launch test only. You completed a simulated release for Juan. "
                        + "This is not a production dismissal.",
                PushNotificationService.pickupBody(
                        "Juan",
                        "Maria",
                        true,
                        true));

        assertEquals(
                "Pre-launch test only. Maria completed a simulated release for Juan. "
                        + "This is not a production dismissal.",
                PushNotificationService.pickupBody(
                        "Juan",
                        "Maria",
                        false,
                        true));
    }
}

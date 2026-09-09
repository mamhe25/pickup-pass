package com.pickuppass.service;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityEventServiceTest {

    @Test
    void singleRevokedDeviceRequestDoesNotSurfaceAlert() {
        assertFalse(
                SecurityEventService
                        .shouldSurfaceRevokedDeviceAlert(1));
        assertFalse(
                SecurityEventService
                        .shouldSurfaceRevokedDeviceAlert(2));
    }

    @Test
    void repeatedRevokedDeviceRequestsSurfaceAlert() {
        assertTrue(
                SecurityEventService
                        .shouldSurfaceRevokedDeviceAlert(3));
        assertTrue(
                SecurityEventService
                        .shouldSurfaceRevokedDeviceAlert(5));
    }

    @Test
    void legacySingleOccurrenceRevokedAlertIsSuppressed() {
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "revoked_device_attempt");
        alert.put("occurrences", 1L);

        assertTrue(
                SecurityEventService
                        .shouldSuppressLegacyRevokedDeviceAlert(alert));
    }

    @Test
    void repeatedWindowRevokedAlertRemainsVisible() {
        Map<String, Object> details = new HashMap<>();
        details.put("occurrencesInWindow", 3L);

        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "revoked_device_attempt");
        alert.put("occurrences", 1L);
        alert.put("details", details);

        assertFalse(
                SecurityEventService
                        .shouldSuppressLegacyRevokedDeviceAlert(alert));
    }

    @Test
    void unrelatedSecurityAlertsAreNeverSuppressed() {
        Map<String, Object> alert = new HashMap<>();
        alert.put("type", "privileged_access_denied");
        alert.put("occurrences", 1L);

        assertFalse(
                SecurityEventService
                        .shouldSuppressLegacyRevokedDeviceAlert(alert));
    }
}

package com.pickuppass.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservabilityAlertPolicyTest {

    @Test
    void elevatedRateDoesNotAlertBeforeMinimumTraffic() {
        assertFalse(ObservabilityAlertPolicy.rateExceeded(19, 20, 100.0, 10.0));
    }

    @Test
    void exactTrafficAndRateThresholdActivatesAlert() {
        assertTrue(ObservabilityAlertPolicy.rateExceeded(20, 20, 10.0, 10.0));
    }

    @Test
    void recoveredRateClearsAlertDecision() {
        assertFalse(ObservabilityAlertPolicy.rateExceeded(100, 20, 9.9, 10.0));
    }

    @Test
    void memoryRequiresKnownCapacityAndThresholdUsage() {
        assertFalse(ObservabilityAlertPolicy.memoryExceeded(0, 100, 85));
        assertFalse(ObservabilityAlertPolicy.memoryExceeded(1024, 84, 85));
        assertTrue(ObservabilityAlertPolicy.memoryExceeded(1024, 85, 85));
    }

    @Test
    void repeatedFirestoreFailuresActivateAtConfiguredCount() {
        assertFalse(ObservabilityAlertPolicy.consecutiveFailuresExceeded(1, 2));
        assertTrue(ObservabilityAlertPolicy.consecutiveFailuresExceeded(2, 2));
    }
}

package com.pickuppass.observability;

import org.junit.jupiter.api.Test;

import static com.pickuppass.observability.ObservabilityIncidentPolicy.Action.NONE;
import static com.pickuppass.observability.ObservabilityIncidentPolicy.Action.OPEN;
import static com.pickuppass.observability.ObservabilityIncidentPolicy.Action.RESOLVE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservabilityIncidentPolicyTest {

    @Test
    void newActiveConditionOpensIncident() {
        assertEquals(OPEN, ObservabilityIncidentPolicy.decide(false, null, true));
    }

    @Test
    void activeOpenIncidentDoesNotCreateRecurringWrites() {
        assertEquals(NONE, ObservabilityIncidentPolicy.decide(true, "open", true));
    }

    @Test
    void acknowledgedIncidentRemainsAcknowledgedWhileConditionPersists() {
        assertEquals(NONE, ObservabilityIncidentPolicy.decide(true, "acknowledged", true));
    }

    @Test
    void recoveredOpenOrAcknowledgedIncidentAutoResolves() {
        assertEquals(RESOLVE, ObservabilityIncidentPolicy.decide(true, "open", false));
        assertEquals(RESOLVE, ObservabilityIncidentPolicy.decide(true, "acknowledged", false));
    }

    @Test
    void healthyConditionWithoutActiveIncidentDoesNothing() {
        assertEquals(NONE, ObservabilityIncidentPolicy.decide(false, null, false));
        assertEquals(NONE, ObservabilityIncidentPolicy.decide(true, "resolved", false));
    }

    @Test
    void recurringConditionReopensResolvedIncident() {
        assertEquals(OPEN, ObservabilityIncidentPolicy.decide(true, "resolved", true));
    }
}

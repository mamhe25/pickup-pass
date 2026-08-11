package com.pickuppass.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PickupMetricsServiceTest {

    @Test
    void recordsPickupCounters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PickupMetricsService metrics = new PickupMetricsService(registry);

        metrics.verificationSucceeded();
        metrics.verificationFailed();
        metrics.approvalSucceeded();
        metrics.approvalFailed();
        metrics.manualOverrideSucceeded();
        metrics.manualOverrideFailed();

        assertEquals(1.0, registry.get("pickuppass.pickup.verify.success").counter().count());
        assertEquals(1.0, registry.get("pickuppass.pickup.verify.failure").counter().count());
        assertEquals(1.0, registry.get("pickuppass.pickup.approve.success").counter().count());
        assertEquals(1.0, registry.get("pickuppass.pickup.approve.failure").counter().count());
        assertEquals(1.0, registry.get("pickuppass.pickup.manual_override.success").counter().count());
        assertEquals(1.0, registry.get("pickuppass.pickup.manual_override.failure").counter().count());
    }

    @Test
    void createsOperationalLatencyTimers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PickupMetricsService metrics = new PickupMetricsService(registry);

        var sample = metrics.startTimer();
        metrics.stopVerificationTimer(sample);

        assertNotNull(registry.find("pickuppass.pickup.verify.latency").timer());
        assertEquals(1L, registry.get("pickuppass.pickup.verify.latency").timer().count());
    }
}

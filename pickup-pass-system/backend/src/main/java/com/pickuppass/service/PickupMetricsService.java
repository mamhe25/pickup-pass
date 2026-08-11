package com.pickuppass.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;

/** Centralized operational metrics for the safety-critical dismissal flow. */
@Service
public class PickupMetricsService {

    private final MeterRegistry registry;
    private final Counter verificationSuccess;
    private final Counter verificationFailure;
    private final Counter approvalSuccess;
    private final Counter approvalFailure;
    private final Counter manualOverrideSuccess;
    private final Counter manualOverrideFailure;
    private final Timer verificationLatency;
    private final Timer approvalLatency;

    public PickupMetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.verificationSuccess = Counter.builder("pickuppass.pickup.verify.success").register(registry);
        this.verificationFailure = Counter.builder("pickuppass.pickup.verify.failure").register(registry);
        this.approvalSuccess = Counter.builder("pickuppass.pickup.approve.success").register(registry);
        this.approvalFailure = Counter.builder("pickuppass.pickup.approve.failure").register(registry);
        this.manualOverrideSuccess = Counter.builder("pickuppass.pickup.manual_override.success").register(registry);
        this.manualOverrideFailure = Counter.builder("pickuppass.pickup.manual_override.failure").register(registry);
        this.verificationLatency = Timer.builder("pickuppass.pickup.verify.latency")
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2))
                .register(registry);
        this.approvalLatency = Timer.builder("pickuppass.pickup.approve.latency")
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(250), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(2))
                .register(registry);
    }

    public Timer.Sample startTimer() { return Timer.start(registry); }
    public void stopVerificationTimer(Timer.Sample sample) { sample.stop(verificationLatency); }
    public void stopApprovalTimer(Timer.Sample sample) { sample.stop(approvalLatency); }

    public void verificationSucceeded() { verificationSuccess.increment(); }
    public void verificationFailed() { verificationFailure.increment(); }
    public void approvalSucceeded() { approvalSuccess.increment(); }
    public void approvalFailed() { approvalFailure.increment(); }
    public void manualOverrideSucceeded() { manualOverrideSuccess.increment(); }
    public void manualOverrideFailed() { manualOverrideFailure.increment(); }
}

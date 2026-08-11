package com.pickuppass.observability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollingHttpMetricsTest {
    @Test
    void aggregatesRequestsWithoutExternalStorage() {
        RollingHttpMetrics metrics = new RollingHttpMetrics();
        metrics.record(200, 100, false);
        metrics.record(404, 250, false);
        metrics.record(503, 2500, true);

        RollingHttpMetrics.Snapshot s = metrics.snapshot(15);
        assertEquals(3, s.requests());
        assertEquals(1, s.errors4xx());
        assertEquals(1, s.errors5xx());
        assertEquals(1, s.slowRequests());
        assertEquals(33.3, s.serverErrorRatePercent());
        assertEquals(33.3, s.slowRequestRatePercent());
        assertEquals(2500, s.maxDurationMs());
    }
}

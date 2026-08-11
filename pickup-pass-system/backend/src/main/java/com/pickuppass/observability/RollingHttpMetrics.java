package com.pickuppass.observability;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Small in-memory rolling HTTP metric store.
 *
 * This intentionally avoids a Firestore write per request. Metrics reset when
 * the application instance restarts; durable incident records are handled by
 * PlatformObservabilityService only when a threshold changes state.
 */
public final class RollingHttpMetrics {
    private static final int RETAIN_MINUTES = 180;
    private final TreeMap<Long, Bucket> buckets = new TreeMap<>();

    public synchronized void record(int status, long durationMs, boolean slow) {
        long minute = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond() / 60;
        Bucket bucket = buckets.computeIfAbsent(minute, ignored -> new Bucket());
        bucket.requests++;
        if (status >= 400 && status < 500) bucket.errors4xx++;
        if (status >= 500) bucket.errors5xx++;
        if (slow) bucket.slowRequests++;
        bucket.totalDurationMs += Math.max(0, durationMs);
        bucket.maxDurationMs = Math.max(bucket.maxDurationMs, Math.max(0, durationMs));
        prune(minute);
    }

    public synchronized Snapshot snapshot(int requestedWindowMinutes) {
        int windowMinutes = Math.max(1, Math.min(requestedWindowMinutes, 120));
        long currentMinute = Instant.now().truncatedTo(ChronoUnit.MINUTES).getEpochSecond() / 60;
        long firstMinute = currentMinute - windowMinutes + 1;
        long requests = 0, errors4xx = 0, errors5xx = 0, slow = 0, duration = 0, max = 0;
        for (Map.Entry<Long, Bucket> entry : buckets.tailMap(firstMinute, true).entrySet()) {
            Bucket b = entry.getValue();
            requests += b.requests;
            errors4xx += b.errors4xx;
            errors5xx += b.errors5xx;
            slow += b.slowRequests;
            duration += b.totalDurationMs;
            max = Math.max(max, b.maxDurationMs);
        }
        double serverErrorRate = requests == 0 ? 0 : (errors5xx * 100.0) / requests;
        double slowRate = requests == 0 ? 0 : (slow * 100.0) / requests;
        long average = requests == 0 ? 0 : duration / requests;
        return new Snapshot(windowMinutes, requests, errors4xx, errors5xx, slow,
                average, max, round1(serverErrorRate), round1(slowRate));
    }

    public synchronized Map<String, Object> asMap(int windowMinutes) {
        Snapshot s = snapshot(windowMinutes);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("windowMinutes", s.windowMinutes());
        out.put("requests", s.requests());
        out.put("errors4xx", s.errors4xx());
        out.put("errors5xx", s.errors5xx());
        out.put("slowRequests", s.slowRequests());
        out.put("averageDurationMs", s.averageDurationMs());
        out.put("maxDurationMs", s.maxDurationMs());
        out.put("serverErrorRatePercent", s.serverErrorRatePercent());
        out.put("slowRequestRatePercent", s.slowRequestRatePercent());
        return out;
    }

    private void prune(long currentMinute) {
        long cutoff = currentMinute - RETAIN_MINUTES;
        Iterator<Long> it = buckets.keySet().iterator();
        while (it.hasNext()) {
            if (it.next() < cutoff) it.remove();
            else break;
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class Bucket {
        long requests;
        long errors4xx;
        long errors5xx;
        long slowRequests;
        long totalDurationMs;
        long maxDurationMs;
    }

    public record Snapshot(
            int windowMinutes,
            long requests,
            long errors4xx,
            long errors5xx,
            long slowRequests,
            long averageDurationMs,
            long maxDurationMs,
            double serverErrorRatePercent,
            double slowRequestRatePercent) { }
}

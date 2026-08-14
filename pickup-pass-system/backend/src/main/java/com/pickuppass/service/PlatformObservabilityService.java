package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.pickuppass.observability.RollingHttpMetrics;
import com.pickuppass.observability.ObservabilityAlertPolicy;
import com.pickuppass.security.FirebaseUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Startup-first observability for PickupPass.
 *
 * HTTP counters remain in memory and therefore cost no Firestore writes per
 * request. Firestore is used only for durable incident state transitions and
 * operator acknowledgement/resolution. This gives a useful operations view
 * without requiring a paid APM product.
 */
@Service
public class PlatformObservabilityService {
    private static final Logger log = LoggerFactory.getLogger(PlatformObservabilityService.class);
    private static final Set<String> INCIDENT_STATUSES = Set.of("open", "acknowledged", "resolved");

    private final Firestore firestore;
    private final RollingHttpMetrics httpMetrics = new RollingHttpMetrics();
    private final Instant startedAt = Instant.now();
    private final int windowMinutes;
    private final int minimumRequests;
    private final double serverErrorRateThreshold;
    private final double slowRateThreshold;
    private final long slowRequestMs;
    private final int memoryWarningPercent;
    private final int firestoreFailureThreshold;
    private final boolean durableIncidentsEnabled;
    private final AtomicInteger consecutiveFirestoreFailures = new AtomicInteger(0);

    private volatile boolean firestoreReachable = true;
    private volatile Instant lastFirestoreCheckAt;
    private volatile Instant lastFirestoreSuccessAt;
    private volatile String lastFirestoreError = "";

    public PlatformObservabilityService(
            Firestore firestore,
            @Value("${pickuppass.observability.window-minutes:15}") int windowMinutes,
            @Value("${pickuppass.observability.minimum-requests:20}") int minimumRequests,
            @Value("${pickuppass.observability.server-error-rate-percent:10}") double serverErrorRateThreshold,
            @Value("${pickuppass.observability.slow-request-rate-percent:25}") double slowRateThreshold,
            @Value("${pickuppass.observability.slow-request-ms:2000}") long slowRequestMs,
            @Value("${pickuppass.observability.memory-warning-percent:85}") int memoryWarningPercent,
            @Value("${pickuppass.observability.firestore-failure-threshold:2}") int firestoreFailureThreshold,
            @Value("${pickuppass.observability.durable-incidents-enabled:true}") boolean durableIncidentsEnabled) {
        this.firestore = firestore;
        this.windowMinutes = Math.max(5, Math.min(windowMinutes, 60));
        this.minimumRequests = Math.max(5, minimumRequests);
        this.serverErrorRateThreshold = Math.max(1, Math.min(serverErrorRateThreshold, 100));
        this.slowRateThreshold = Math.max(1, Math.min(slowRateThreshold, 100));
        this.slowRequestMs = Math.max(250, slowRequestMs);
        this.memoryWarningPercent = Math.max(50, Math.min(memoryWarningPercent, 98));
        this.firestoreFailureThreshold = Math.max(1, Math.min(firestoreFailureThreshold, 10));
        this.durableIncidentsEnabled = durableIncidentsEnabled;
    }

    /** Called by RequestLoggingFilter. No network/database access is performed here. */
    public void recordHttp(int status, long durationMs, String path) {
        if (path != null && path.startsWith("/actuator/health")) return;
        httpMetrics.record(status, durationMs, durationMs >= slowRequestMs);
    }

    @Scheduled(
            initialDelayString = "${pickuppass.observability.initial-delay-ms:60000}",
            fixedDelayString = "${pickuppass.observability.evaluate-ms:300000}")
    public void scheduledEvaluate() {
        try {
            evaluate();
        } catch (Exception e) {
            log.warn("Platform observability evaluation failed; application traffic is unaffected", e);
        }
    }

    public Map<String, Object> evaluate() {
        checkFirestore();
        RollingHttpMetrics.Snapshot http = httpMetrics.snapshot(windowMinutes);
        MemorySnapshot memory = memorySnapshot();

        transitionIncident(
                "http_5xx_rate_high",
                ObservabilityAlertPolicy.rateExceeded(http.requests(), minimumRequests,
                        http.serverErrorRatePercent(), serverErrorRateThreshold),
                "high",
                "Backend server-error rate is elevated",
                "HTTP 5xx responses exceeded the configured startup alert threshold.",
                Map.of("windowMinutes", windowMinutes,
                        "requests", http.requests(),
                        "errors5xx", http.errors5xx(),
                        "errorRatePercent", http.serverErrorRatePercent(),
                        "thresholdPercent", serverErrorRateThreshold));

        transitionIncident(
                "http_slow_rate_high",
                ObservabilityAlertPolicy.rateExceeded(http.requests(), minimumRequests,
                        http.slowRequestRatePercent(), slowRateThreshold),
                "medium",
                "Backend requests are frequently slow",
                "Slow requests exceeded the configured threshold in the rolling runtime window.",
                Map.of("windowMinutes", windowMinutes,
                        "requests", http.requests(),
                        "slowRequests", http.slowRequests(),
                        "slowRatePercent", http.slowRequestRatePercent(),
                        "slowRequestMs", slowRequestMs));

        transitionIncident(
                "runtime_memory_high",
                ObservabilityAlertPolicy.memoryExceeded(memory.maxBytes(), memory.usedPercent(), memoryWarningPercent),
                "medium",
                "Backend memory usage is high",
                "JVM memory usage is approaching the configured startup safety threshold.",
                Map.of("usedPercent", memory.usedPercent(), "thresholdPercent", memoryWarningPercent));

        // Firestore may be unavailable precisely when we want to persist an incident.
        // The live overview therefore always reports connectivity directly. If writes
        // still work during a degraded condition, this transition will also persist it.
        transitionIncident(
                "firestore_connectivity_degraded",
                ObservabilityAlertPolicy.consecutiveFailuresExceeded(
                        consecutiveFirestoreFailures.get(), firestoreFailureThreshold),
                "critical",
                "Firestore connectivity is degraded",
                "The backend has failed repeated Firestore reachability checks.",
                Map.of("consecutiveFailures", consecutiveFirestoreFailures.get(),
                        "threshold", firestoreFailureThreshold));

        return overview();
    }

    public Map<String, Object> overview() {
        RollingHttpMetrics.Snapshot http = httpMetrics.snapshot(windowMinutes);
        MemorySnapshot memory = memorySnapshot();
        List<Map<String, Object>> incidents = new ArrayList<>();
        boolean incidentStorageAvailable = true;
        try {
            incidents.addAll(loadActiveIncidents());
        } catch (Exception e) {
            incidentStorageAvailable = false;
        }

        boolean firestoreDegraded = ObservabilityAlertPolicy.consecutiveFailuresExceeded(
                consecutiveFirestoreFailures.get(), firestoreFailureThreshold);
        if (firestoreDegraded && incidents.stream().noneMatch(i -> "firestore_connectivity_degraded".equals(i.get("type")))) {
            Map<String, Object> runtime = new LinkedHashMap<>();
            runtime.put("id", "runtime-firestore-connectivity");
            runtime.put("type", "firestore_connectivity_degraded");
            runtime.put("severity", "critical");
            runtime.put("status", "runtime");
            runtime.put("title", "Firestore connectivity is degraded");
            runtime.put("message", "The current backend instance cannot reliably reach Firestore. This live incident may not be durable while Firestore is unavailable.");
            runtime.put("source", "runtime");
            runtime.put("firstSeenAt", lastFirestoreCheckAt == null ? null : lastFirestoreCheckAt.toString());
            incidents.add(0, runtime);
        }

        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("instanceStartedAt", startedAt.toString());
        runtime.put("uptimeSeconds", Math.max(0, Duration.between(startedAt, Instant.now()).getSeconds()));
        runtime.put("processorCount", Runtime.getRuntime().availableProcessors());
        runtime.put("threadCount", ManagementFactory.getThreadMXBean().getThreadCount());

        Map<String, Object> firestoreState = new LinkedHashMap<>();
        firestoreState.put("checked", lastFirestoreCheckAt != null);
        firestoreState.put("reachable", firestoreReachable);
        firestoreState.put("consecutiveFailures", consecutiveFirestoreFailures.get());
        firestoreState.put("lastCheckAt", text(lastFirestoreCheckAt));
        firestoreState.put("lastSuccessAt", text(lastFirestoreSuccessAt));
        firestoreState.put("lastError", lastFirestoreError);

        Map<String, Object> thresholds = new LinkedHashMap<>();
        thresholds.put("minimumRequests", minimumRequests);
        thresholds.put("serverErrorRatePercent", serverErrorRateThreshold);
        thresholds.put("slowRequestRatePercent", slowRateThreshold);
        thresholds.put("slowRequestMs", slowRequestMs);
        thresholds.put("memoryWarningPercent", memoryWarningPercent);
        thresholds.put("firestoreFailureThreshold", firestoreFailureThreshold);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", Instant.now().toString());
        out.put("mode", "startup_low_cost");
        out.put("externalApmRequired", false);
        out.put("durableIncidentsEnabled", durableIncidentsEnabled);
        out.put("incidentStorageAvailable", incidentStorageAvailable);
        out.put("runtime", runtime);
        out.put("http", httpMetrics.asMap(windowMinutes));
        out.put("memory", memory.asMap());
        out.put("firestore", firestoreState);
        out.put("thresholds", thresholds);
        out.put("incidents", incidents);
        return out;
    }

    public boolean setIncidentStatus(String incidentId, String status, FirebaseUserDetails actor, String note) throws Exception {
        if (!INCIDENT_STATUSES.contains(status)) throw new IllegalArgumentException("Invalid incident status");
        if (incidentId == null || incidentId.startsWith("runtime-")) return false;
        DocumentReference ref = firestore.collection("platformIncidents").document(incidentId);
        DocumentSnapshot snap = ref.get().get();
        if (!snap.exists()) return false;
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        update.put("statusUpdatedAt", FieldValue.serverTimestamp());
        update.put("statusUpdatedBy", actor == null ? "system" : actor.getUid());
        update.put("statusNote", safe(note, 500));
        if ("resolved".equals(status)) update.put("resolvedAt", FieldValue.serverTimestamp());
        ref.update(update).get();
        return true;
    }

    private void checkFirestore() {
        lastFirestoreCheckAt = Instant.now();
        try {
            firestore.collection("schools").limit(1).get().get(2, TimeUnit.SECONDS);
            firestoreReachable = true;
            lastFirestoreSuccessAt = Instant.now();
            lastFirestoreError = "";
            consecutiveFirestoreFailures.set(0);
        } catch (Exception e) {
            firestoreReachable = false;
            consecutiveFirestoreFailures.incrementAndGet();
            lastFirestoreError = safe(rootMessage(e), 180);
        }
    }

    private List<Map<String, Object>> loadActiveIncidents() throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String status : List.of("open", "acknowledged")) {
            QuerySnapshot snap = firestore.collection("platformIncidents").whereEqualTo("status", status).limit(100).get().get();
            for (DocumentSnapshot d : snap.getDocuments()) out.add(normalize(d));
        }
        out.sort((a, b) -> Objects.toString(b.get("activatedAt"), "").compareTo(Objects.toString(a.get("activatedAt"), "")));
        return out;
    }

    private void transitionIncident(String type, boolean active, String severity,
                                    String title, String message, Map<String, Object> details) {
        if (!durableIncidentsEnabled) return;
        String id = sha256(type);
        try {
            DocumentReference ref = firestore.collection("platformIncidents").document(id);
            DocumentSnapshot current = ref.get().get();
            String status = current.exists() ? Objects.toString(current.getString("status"), "resolved") : "resolved";

            if (active) {
                if ("open".equals(status) || "acknowledged".equals(status)) return; // no recurring writes
                long occurrences = current.exists() && current.getLong("occurrences") != null ? current.getLong("occurrences") + 1 : 1;
                Map<String, Object> data = new HashMap<>();
                data.put("type", type);
                data.put("severity", severity);
                data.put("title", title);
                data.put("message", message);
                data.put("details", details == null ? Map.of() : details);
                data.put("status", "open");
                data.put("source", "runtime_threshold");
                data.put("costMode", "startup_low_cost");
                data.put("occurrences", occurrences);
                data.put("activatedAt", FieldValue.serverTimestamp());
                data.put("resolvedAt", FieldValue.delete());
                if (!current.exists()) data.put("firstSeenAt", FieldValue.serverTimestamp());
                ref.set(data, SetOptions.merge()).get();
            } else if (current.exists() && ("open".equals(status) || "acknowledged".equals(status))) {
                ref.update(Map.of(
                        "status", "resolved",
                        "autoResolved", true,
                        "resolvedAt", FieldValue.serverTimestamp(),
                        "statusUpdatedAt", FieldValue.serverTimestamp(),
                        "statusUpdatedBy", "system"
                )).get();
            }
        } catch (Exception e) {
            // Observability must never become an availability dependency.
            log.debug("Could not persist observability incident transition type={}", type, e);
        }
    }

    private static MemorySnapshot memorySnapshot() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long committed = runtime.totalMemory();
        long freeWithinCommitted = runtime.freeMemory();
        long used = Math.max(0, committed - freeWithinCommitted);
        int usedPercent = max <= 0 ? 0 : (int) Math.min(100, Math.round((used * 100.0) / max));
        return new MemorySnapshot(used, committed, max, usedPercent);
    }

    private Map<String, Object> normalize(DocumentSnapshot d) {
        Map<String, Object> out = new LinkedHashMap<>(d.getData());
        out.put("id", d.getId());
        for (String key : List.of("firstSeenAt", "activatedAt", "resolvedAt", "statusUpdatedAt")) {
            Object value = out.get(key);
            if (value instanceof Timestamp ts) out.put(key, ts.toDate().toInstant().toString());
        }
        return out;
    }

    private static String sha256(String value) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : bytes) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String safe(String value, int max) {
        if (value == null) return "";
        String trimmed = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private static String text(Instant value) { return value == null ? null : value.toString(); }

    private record MemorySnapshot(long usedBytes, long committedBytes, long maxBytes, int usedPercent) {
        Map<String, Object> asMap() {
            return Map.of(
                    "usedBytes", usedBytes,
                    "committedBytes", committedBytes,
                    "maxBytes", maxBytes,
                    "usedPercent", usedPercent
            );
        }
    }
}

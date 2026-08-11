package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.time.Instant;

@Service
public class SubscriptionFeatureService {

    public static final String TRIAL = "trial";
    public static final String STARTER = "starter";
    public static final String SCHOOL = "school";
    public static final String ENTERPRISE = "enterprise";

    public static final Set<String> PLANS = Set.of(TRIAL, STARTER, SCHOOL, ENTERPRISE);
    public static final Set<String> FEATURES = Set.of(
            "bulk_student_import",
            "advanced_reporting",
            "scheduled_announcements",
            "multi_campus",
            "guardian_verification",
            "temporary_guardians",
            "guardian_pickup_schedules",
            "manual_override",
            "staff_gate_restrictions",
            "device_session_management"
    );

    private final Firestore firestore;

    public SubscriptionFeatureService(Firestore firestore) {
        this.firestore = firestore;
    }

    public Map<String, Object> getCatalog() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(TRIAL, plan("Trial", 100, 15, 3, Map.ofEntries(
                Map.entry("bulk_student_import", true),
                Map.entry("advanced_reporting", true),
                Map.entry("scheduled_announcements", true),
                Map.entry("multi_campus", true),
                Map.entry("guardian_verification", true),
                Map.entry("temporary_guardians", true),
                Map.entry("guardian_pickup_schedules", true),
                Map.entry("manual_override", true),
                Map.entry("staff_gate_restrictions", true),
                Map.entry("device_session_management", true)
        )));
        result.put(STARTER, plan("Starter", 300, 30, 1, Map.ofEntries(
                Map.entry("bulk_student_import", false),
                Map.entry("advanced_reporting", false),
                Map.entry("scheduled_announcements", false),
                Map.entry("multi_campus", false),
                Map.entry("guardian_verification", true),
                Map.entry("temporary_guardians", true),
                Map.entry("guardian_pickup_schedules", true),
                Map.entry("manual_override", true),
                Map.entry("staff_gate_restrictions", false),
                Map.entry("device_session_management", true)
        )));
        result.put(SCHOOL, plan("School", 1500, 150, 3, Map.ofEntries(
                Map.entry("bulk_student_import", true),
                Map.entry("advanced_reporting", true),
                Map.entry("scheduled_announcements", true),
                Map.entry("multi_campus", true),
                Map.entry("guardian_verification", true),
                Map.entry("temporary_guardians", true),
                Map.entry("guardian_pickup_schedules", true),
                Map.entry("manual_override", true),
                Map.entry("staff_gate_restrictions", true),
                Map.entry("device_session_management", true)
        )));
        result.put(ENTERPRISE, plan("Enterprise", -1, -1, -1, FEATURES.stream()
                .collect(java.util.stream.Collectors.toMap(k -> k, k -> true))));
        return result;
    }

    private Map<String, Object> plan(String displayName, int maxStudents, int maxStaff, int maxCampuses,
                                     Map<String, Boolean> features) {
        return Map.of(
                "displayName", displayName,
                "maxStudents", maxStudents,
                "maxStaff", maxStaff,
                "maxCampuses", maxCampuses,
                "features", features
        );
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> effectiveEntitlements(DocumentSnapshot school) {
        String plan = normalizePlan(school.getString("plan"));
        Map<String, Object> catalogPlan = (Map<String, Object>) getCatalog().get(plan);
        Map<String, Boolean> defaults = new LinkedHashMap<>((Map<String, Boolean>) catalogPlan.get("features"));
        Object rawOverrides = school.get("featureOverrides");
        if (rawOverrides instanceof Map<?, ?> overrides) {
            for (Map.Entry<?, ?> entry : overrides.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (FEATURES.contains(key) && entry.getValue() instanceof Boolean enabled) {
                    defaults.put(key, enabled);
                }
            }
        }
        String subscriptionStatus = normalizeStatus(school.getString("subscriptionStatus"), plan);
        Instant now = Instant.now();
        Instant trialEndsAt = timestampInstant(school, "trialEndsAt");
        Instant currentPeriodStart = timestampInstant(school, "currentPeriodStart");
        Instant currentPeriodEnd = timestampInstant(school, "currentPeriodEnd");
        Instant graceEndsAt = timestampInstant(school, "graceEndsAt");
        boolean autoRenew = !Boolean.FALSE.equals(school.getBoolean("autoRenew"));
        boolean cancelAtPeriodEnd = Boolean.TRUE.equals(school.getBoolean("cancelAtPeriodEnd"));

        // Subscription state only gates optional SaaS features. QR generation,
        // verification, pickup approval, and immutable exit logging are core
        // safety functions and are intentionally not part of FEATURES.
        boolean subscriptionAccessActive = subscriptionAccessActive(
                subscriptionStatus, trialEndsAt, graceEndsAt, now);
        if (!subscriptionAccessActive) {
            defaults.replaceAll((key, value) -> false);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("plan", plan);
        response.put("subscriptionStatus", subscriptionStatus);
        response.put("subscriptionAccessActive", subscriptionAccessActive);
        response.put("trialEndsAt", instantString(trialEndsAt));
        response.put("currentPeriodStart", instantString(currentPeriodStart));
        response.put("currentPeriodEnd", instantString(currentPeriodEnd));
        response.put("graceEndsAt", instantString(graceEndsAt));
        response.put("autoRenew", autoRenew);
        response.put("cancelAtPeriodEnd", cancelAtPeriodEnd);
        response.put("features", defaults);
        response.put("limits", Map.of(
                "maxStudents", catalogPlan.get("maxStudents"),
                "maxStaff", catalogPlan.get("maxStaff"),
                "maxCampuses", catalogPlan.get("maxCampuses")
        ));
        return response;
    }

    public Map<String, Object> effectiveEntitlements(String schoolId) throws ExecutionException, InterruptedException {
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        if (!school.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found");
        return effectiveEntitlements(school);
    }

    @SuppressWarnings("unchecked")
    public boolean isFeatureEnabled(String schoolId, String feature) throws ExecutionException, InterruptedException {
        if (!FEATURES.contains(feature)) return false;
        Map<String, Object> entitlements = effectiveEntitlements(schoolId);
        Map<String, Boolean> features = (Map<String, Boolean>) entitlements.get("features");
        return Boolean.TRUE.equals(features.get(feature));
    }

    public void requireFeature(String schoolId, String feature) throws ExecutionException, InterruptedException {
        if (!isFeatureEnabled(schoolId, feature)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This feature is not enabled for the school's current plan: " + feature);
        }
    }


    static boolean subscriptionAccessActive(String status, Instant trialEndsAt, Instant graceEndsAt, Instant now) {
        if ("active".equals(status)) return true;
        if ("cancelled".equals(status)) return false;
        if ("past_due".equals(status)) {
            return graceEndsAt != null && now.isBefore(graceEndsAt);
        }
        if ("trialing".equals(status)) {
            if (trialEndsAt == null || now.isBefore(trialEndsAt)) return true;
            // The lifecycle scheduler normally moves an expired trial to
            // past_due and writes graceEndsAt. This fallback keeps behavior
            // deterministic during the short interval before that job runs.
            Instant fallbackGrace = trialEndsAt.plusSeconds(7L * 24L * 60L * 60L);
            return now.isBefore(fallbackGrace);
        }
        return false;
    }

    private Instant timestampInstant(DocumentSnapshot school, String field) {
        return school.getTimestamp(field) == null ? null : school.getTimestamp(field).toDate().toInstant();
    }

    private String instantString(Instant value) {
        return value == null ? null : value.toString();
    }

    public String normalizePlan(String value) {
        if (value == null || value.isBlank()) return TRIAL;
        String plan = value.trim().toLowerCase();
        return PLANS.contains(plan) ? plan : TRIAL;
    }

    public String normalizeStatus(String value, String plan) {
        if (value != null && !value.isBlank()) return value.trim().toLowerCase();
        return TRIAL.equals(plan) ? "trialing" : "active";
    }
}

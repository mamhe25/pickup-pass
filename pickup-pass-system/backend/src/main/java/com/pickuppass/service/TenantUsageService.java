package com.pickuppass.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.SetOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Maintains small per-tenant counters used for SaaS plan enforcement.
 * Quota reservations are Firestore transactions so two application instances
 * cannot both consume the final seat/student/campus slot.
 *
 * Existing tenants are reconciled lazily once. After that, all supported
 * lifecycle mutations update the counters through this service.
 */
@Service
public class TenantUsageService {
    public static final String STUDENTS = "students";
    public static final String STAFF = "staff";
    public static final String CAMPUSES = "campuses";

    private final Firestore firestore;
    private final SubscriptionFeatureService subscriptions;

    public TenantUsageService(Firestore firestore, SubscriptionFeatureService subscriptions) {
        this.firestore = firestore;
        this.subscriptions = subscriptions;
    }

    public void initializeNewTenant(String schoolId) throws ExecutionException, InterruptedException {
        Map<String,Object> data = new HashMap<>();
        data.put("activeStudents", 0L);
        data.put("activeStaff", 0L);
        data.put("activeCampuses", 0L);
        data.put("totalQrPickups", 0L);
        data.put("totalManualPickups", 0L);
        data.put("initialized", true);
        data.put("initializedAt", FieldValue.serverTimestamp());
        firestore.collection("tenantUsage").document(schoolId).set(data, SetOptions.merge()).get();
    }

    public Map<String,Object> snapshot(String schoolId) throws ExecutionException, InterruptedException {
        ensureInitialized(schoolId);
        DocumentSnapshot usage = usageRef(schoolId).get().get();
        Map<String,Object> entitlements = subscriptions.effectiveEntitlements(schoolId);
        @SuppressWarnings("unchecked") Map<String,Object> limits = (Map<String,Object>) entitlements.get("limits");
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("activeStudents", number(usage, "activeStudents"));
        result.put("activeStaff", number(usage, "activeStaff"));
        result.put("activeCampuses", number(usage, "activeCampuses"));
        result.put("totalQrPickups", number(usage, "totalQrPickups"));
        result.put("totalManualPickups", number(usage, "totalManualPickups"));
        result.put("studentLimit", asLong(limits.get("maxStudents")));
        result.put("staffLimit", asLong(limits.get("maxStaff")));
        result.put("campusLimit", asLong(limits.get("maxCampuses")));
        result.put("studentsOverLimit", over(number(usage,"activeStudents"), asLong(limits.get("maxStudents"))));
        result.put("staffOverLimit", over(number(usage,"activeStaff"), asLong(limits.get("maxStaff"))));
        result.put("campusesOverLimit", over(number(usage,"activeCampuses"), asLong(limits.get("maxCampuses"))));
        return result;
    }

    public void reserve(String schoolId, String resource, long amount) throws Exception {
        if (amount <= 0) return;
        ensureInitialized(schoolId);
        String field = usageField(resource);
        long limit = limitFor(schoolId, resource);
        DocumentReference ref = usageRef(schoolId);
        try {
            firestore.runTransaction(tx -> {
                DocumentSnapshot current = tx.get(ref).get();
                long used = number(current, field);
                long next = used + amount;
                if (limit >= 0 && next > limit) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            quotaMessage(resource, used, amount, limit));
                }
                Map<String,Object> update = new HashMap<>();
                update.put(field, next);
                update.put("updatedAt", FieldValue.serverTimestamp());
                tx.set(ref, update, SetOptions.merge());
                return null;
            }).get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ResponseStatusException quotaError) throw quotaError;
            throw e;
        }
    }

    public void release(String schoolId, String resource, long amount) throws Exception {
        if (amount <= 0) return;
        ensureInitialized(schoolId);
        String field = usageField(resource);
        DocumentReference ref = usageRef(schoolId);
        firestore.runTransaction(tx -> {
            DocumentSnapshot current = tx.get(ref).get();
            long next = Math.max(0L, number(current, field) - amount);
            Map<String,Object> update = new HashMap<>();
            update.put(field, next);
            update.put("updatedAt", FieldValue.serverTimestamp());
            tx.set(ref, update, SetOptions.merge());
            return null;
        }).get();
    }

    public void recordQrPickup(String schoolId) { recordEvent(schoolId, "qrPickups", "totalQrPickups"); }
    public void recordManualPickup(String schoolId) { recordEvent(schoolId, "manualPickups", "totalManualPickups"); }

    private void recordEvent(String schoolId, String monthlyField, String lifetimeField) {
        try {
            ensureInitialized(schoolId);
            usageRef(schoolId).set(Map.of(
                    lifetimeField, FieldValue.increment(1),
                    "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
            String month = YearMonth.now(ZoneOffset.UTC).toString();
            firestore.collection("tenantUsage").document(schoolId).collection("months").document(month)
                    .set(Map.of(monthlyField, FieldValue.increment(1), "updatedAt", FieldValue.serverTimestamp()), SetOptions.merge());
        } catch (Exception ignored) {
            // Metering must never turn an already-approved student release into a failure.
        }
    }

    public void ensureInitialized(String schoolId) throws ExecutionException, InterruptedException {
        DocumentReference ref = usageRef(schoolId);
        DocumentSnapshot existing = ref.get().get();
        if (existing.exists() && Boolean.TRUE.equals(existing.getBoolean("initialized"))) return;
        reconcile(schoolId);
    }

    /** Rebuilds resource counters from source-of-truth collections without resetting pickup totals. */
    public void reconcile(String schoolId) throws ExecutionException, InterruptedException {
        DocumentReference ref = usageRef(schoolId);
        DocumentSnapshot existing = ref.get().get();
        long students = 0;
        for (QueryDocumentSnapshot d : firestore.collection("students").whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            String status = d.getString("status");
            if (status == null || status.isBlank() || "active".equalsIgnoreCase(status)) students++;
        }
        long staff = 0;
        for (QueryDocumentSnapshot d : firestore.collection("users").whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            String role = d.getString("role");
            if (!("teacher".equals(role) || "school_admin".equals(role))) continue;
            boolean suspendedBySchool = Boolean.TRUE.equals(d.getBoolean("suspendedBySchool"));
            if (!Boolean.FALSE.equals(d.getBoolean("isActive")) || suspendedBySchool) staff++;
        }
        long campuses = 0;
        for (QueryDocumentSnapshot d : firestore.collection("campuses").whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (!Boolean.FALSE.equals(d.getBoolean("active"))) campuses++;
        }
        Map<String,Object> data = new HashMap<>();
        data.put("activeStudents", students);
        data.put("activeStaff", staff);
        data.put("activeCampuses", campuses);
        data.put("totalQrPickups", existing.exists() ? number(existing,"totalQrPickups") : 0L);
        data.put("totalManualPickups", existing.exists() ? number(existing,"totalManualPickups") : 0L);
        data.put("initialized", true);
        data.put("initializedAt", FieldValue.serverTimestamp());
        ref.set(data, SetOptions.merge()).get();
    }

    private long limitFor(String schoolId, String resource) throws ExecutionException, InterruptedException {
        Map<String,Object> entitlements = subscriptions.effectiveEntitlements(schoolId);
        @SuppressWarnings("unchecked") Map<String,Object> limits = (Map<String,Object>) entitlements.get("limits");
        return switch (resource) {
            case STUDENTS -> asLong(limits.get("maxStudents"));
            case STAFF -> asLong(limits.get("maxStaff"));
            case CAMPUSES -> asLong(limits.get("maxCampuses"));
            default -> throw new IllegalArgumentException("Unsupported quota resource: " + resource);
        };
    }

    private static String usageField(String resource) {
        return switch (resource) {
            case STUDENTS -> "activeStudents";
            case STAFF -> "activeStaff";
            case CAMPUSES -> "activeCampuses";
            default -> throw new IllegalArgumentException("Unsupported quota resource: " + resource);
        };
    }

    private static String quotaMessage(String resource, long used, long requested, long limit) {
        String label = switch (resource) {
            case STUDENTS -> "student";
            case STAFF -> "staff";
            case CAMPUSES -> "campus";
            default -> resource;
        };
        return "Plan " + label + " limit reached (" + used + "/" + limit + "). " +
                "This operation needs " + requested + " additional slot(s).";
    }

    private DocumentReference usageRef(String schoolId) { return firestore.collection("tenantUsage").document(schoolId); }
    private static long number(DocumentSnapshot doc, String field) { Long v = doc.getLong(field); return v == null ? 0L : v; }
    private static long asLong(Object v) { return v instanceof Number n ? n.longValue() : -1L; }
    private static boolean over(long current, long limit) { return limit >= 0 && current > limit; }
}

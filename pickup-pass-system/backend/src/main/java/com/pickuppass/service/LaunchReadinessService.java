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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Low-cost, on-demand launch-readiness assessment for a tenant.
 *
 * The service deliberately avoids background scans. It only reads the tenant
 * when a school admin or platform owner opens the readiness wizard. This is a
 * better startup trade-off than continuously recomputing onboarding state.
 *
 * Infrastructure/cloud settings are NOT delegated to the tenant. School
 * admins can complete tenant-scoped configuration and manual launch checks;
 * only master_admin may grant final launch approval.
 */
@Service
public class LaunchReadinessService {

    public static final String DRAFT = "draft";
    public static final String REVIEW_REQUESTED = "review_requested";
    public static final String APPROVED = "approved";

    public static final Set<String> MANUAL_CHECK_KEYS = Set.of(
            "scannerDeviceTested",
            "guardianQrTested",
            "dismissalStaffBriefed",
            "emergencyProcedureReviewed"
    );

    private final Firestore firestore;
    private final SubscriptionFeatureService subscriptionFeatureService;

    public LaunchReadinessService(Firestore firestore,
                                  SubscriptionFeatureService subscriptionFeatureService) {
        this.firestore = firestore;
        this.subscriptionFeatureService = subscriptionFeatureService;
    }

    public Map<String, Object> assess(String schoolId) throws Exception {
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        if (!school.exists()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "School not found");

        DocumentSnapshot stateDoc = readinessRef(schoolId).get().get();
        Map<String, Boolean> manualChecks = manualChecks(stateDoc);
        String storedStatus = normalizeStatus(stateDoc.getString("reviewStatus"));

        Map<String, Object> entitlements = subscriptionFeatureService.effectiveEntitlements(school);
        boolean subscriptionAccessActive = Boolean.TRUE.equals(entitlements.get("subscriptionAccessActive"));

        List<QueryDocumentSnapshot> academicYears = firestore.collection("academicYears")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments();
        String currentAcademicYearId = "";
        String currentAcademicYearName = "";
        for (QueryDocumentSnapshot year : academicYears) {
            if (Boolean.TRUE.equals(year.getBoolean("isCurrent")) && !"archived".equalsIgnoreCase(safe(year.getString("status")))) {
                currentAcademicYearId = year.getId();
                currentAcademicYearName = safe(year.getString("name"));
                break;
            }
        }

        List<QueryDocumentSnapshot> sections = firestore.collection("gradeSections")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments();
        final String activeAcademicYearId = currentAcademicYearId;
        long activeSections = sections.stream()
                .filter(d -> !Boolean.FALSE.equals(d.getBoolean("active")))
                .filter(d -> activeAcademicYearId.isBlank() || activeAcademicYearId.equals(safe(d.getString("academicYearId"))))
                .count();

        List<QueryDocumentSnapshot> students = firestore.collection("students")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments();
        long activeStudents = 0;
        long studentsWithoutGuardian = 0;
        long studentsWithoutPlacement = 0;
        List<String> missingGuardianSample = new ArrayList<>();
        for (QueryDocumentSnapshot student : students) {
            if (!isActiveStudent(student)) continue;
            activeStudents++;
            Object guardianRaw = student.get("guardianUids");
            boolean hasGuardian = guardianRaw instanceof List<?> list && !list.isEmpty();
            if (!hasGuardian) {
                studentsWithoutGuardian++;
                if (missingGuardianSample.size() < 8) missingGuardianSample.add(safe(student.getString("fullName")));
            }
            String gradeSectionId = safe(student.getString("gradeSectionId"));
            String grade = safe(student.getString("grade"));
            String section = safe(student.getString("section"));
            if (gradeSectionId.isBlank() && (grade.isBlank() || section.isBlank())) studentsWithoutPlacement++;
        }

        List<QueryDocumentSnapshot> users = firestore.collection("users")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments();
        long activeSchoolAdmins = 0;
        long activeTeachers = 0;
        for (QueryDocumentSnapshot user : users) {
            if (Boolean.FALSE.equals(user.getBoolean("isActive"))) continue;
            String role = safe(user.getString("role"));
            if ("school_admin".equals(role)) activeSchoolAdmins++;
            if ("teacher".equals(role)) activeTeachers++;
        }

        long activeCampuses = firestore.collection("campuses")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments().stream()
                .filter(d -> !Boolean.FALSE.equals(d.getBoolean("active"))).count();
        long activeGates = firestore.collection("pickupGates")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments().stream()
                .filter(d -> !Boolean.FALSE.equals(d.getBoolean("active"))).count();

        List<Map<String, Object>> checks = new ArrayList<>();
        required(checks, "tenant_active", "School tenant is active",
                !"suspended".equalsIgnoreCase(safe(school.getString("status"))),
                "The tenant must be active before launch review.", "platform_owner");
        required(checks, "subscription_access", "Trial/subscription is usable",
                subscriptionAccessActive,
                "A valid trial, active subscription, or grace-period access is required to launch optional SaaS features.", "billing");
        required(checks, "school_admin", "School administrator account exists",
                activeSchoolAdmins > 0,
                activeSchoolAdmins + " active school administrator(s).", "staff");
        required(checks, "current_academic_year", "Current school year is configured",
                !currentAcademicYearId.isBlank(),
                currentAcademicYearId.isBlank() ? "Set one academic year as current." : currentAcademicYearName,
                "academic");
        required(checks, "active_sections", "At least one active grade/section exists",
                activeSections > 0,
                activeSections + " active section(s) in the current school year.", "academic");
        required(checks, "active_students", "Student roster is loaded",
                activeStudents > 0,
                activeStudents + " active student(s).", "students");
        required(checks, "guardian_coverage", "Every active student has an authorized guardian",
                studentsWithoutGuardian == 0,
                activeStudents == 0
                        ? "No active students are available to assess yet."
                        : studentsWithoutGuardian == 0
                        ? "All active students have at least one guardian."
                        : studentsWithoutGuardian + " active student(s) still need a guardian" + sampleSuffix(missingGuardianSample),
                "guardians");

        if (studentsWithoutPlacement > 0) {
            warning(checks, "academic_placement", "Some students have incomplete academic placement",
                    studentsWithoutPlacement + " active student(s) do not have a complete grade/section placement.", "students");
        } else {
            pass(checks, "academic_placement", "Student academic placement is complete",
                    activeStudents + " active student record(s) checked.", false, "students");
        }

        if (activeTeachers == 0) {
            warning(checks, "pickup_staff", "No teacher/staff account is ready yet",
                    "School admins can scan, but adding at least one dedicated pickup staff account is recommended before daily operations.", "staff");
        } else {
            pass(checks, "pickup_staff", "Pickup staff account is available",
                    activeTeachers + " active teacher/staff account(s).", false, "staff");
        }

        boolean logoConfigured = !safe(school.getString("logoUrl")).isBlank();
        if (logoConfigured) pass(checks, "branding", "School identity is configured", "School logo is available.", false, "branding");
        else warning(checks, "branding", "School logo is not configured", "Optional, but recommended so parents and staff can confirm the correct tenant.", "branding");

        // Gates remain optional by product design. 0 gates is a fully supported
        // scanner mode; 1 active gate auto-selects; 2+ gates enable selection.
        String gateDetail = activeGates == 0
                ? "No gate configured — supported. Scanner will work normally without gate selection."
                : activeGates == 1
                ? "1 active gate — it will be selected automatically."
                : activeGates + " active gates across " + activeCampuses + " active campus/campuses.";
        pass(checks, "pickup_locations", "Pickup-location configuration is valid", gateDetail, false, "campus_gates");

        requiredManual(checks, "scanner_device_test", "Scanner device tested", manualChecks.get("scannerDeviceTested"),
                "Test camera permission, QR scanning, connectivity, and staff sign-in on the actual dismissal device.");
        requiredManual(checks, "guardian_qr_test", "End-to-end guardian QR test completed", manualChecks.get("guardianQrTested"),
                "Run one controlled test from guardian QR generation through staff verification and approval.");
        requiredManual(checks, "staff_briefing", "Dismissal staff briefing completed", manualChecks.get("dismissalStaffBriefed"),
                "Confirm staff know the QR approval flow, duplicate-pass behavior, and when to escalate.");
        requiredManual(checks, "emergency_procedure", "Emergency pickup procedure reviewed", manualChecks.get("emergencyProcedureReviewed"),
                "Confirm the school knows its fallback process for device/network problems and authorized manual pickup.");

        long blockers = checks.stream().filter(c -> "blocker".equals(c.get("status"))).count();
        long warnings = checks.stream().filter(c -> "warning".equals(c.get("status"))).count();
        long passed = checks.stream().filter(c -> "pass".equals(c.get("status"))).count();
        boolean readyForReview = blockers == 0;
        boolean launchApproved = APPROVED.equals(storedStatus);
        String effectiveStatus = storedStatus;
        if (launchApproved && !readyForReview) effectiveStatus = "approved_needs_attention";
        if (REVIEW_REQUESTED.equals(storedStatus) && !readyForReview) effectiveStatus = "review_requested_needs_attention";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schoolId", schoolId);
        result.put("schoolName", value(school.getString("schoolName"), "Unnamed school"));
        result.put("reviewStatus", storedStatus);
        result.put("effectiveStatus", effectiveStatus);
        result.put("readyForReview", readyForReview);
        result.put("launchApproved", launchApproved);
        result.put("readyForLaunch", launchApproved && readyForReview);
        result.put("blockerCount", blockers);
        result.put("warningCount", warnings);
        result.put("passedCount", passed);
        result.put("activeStudents", activeStudents);
        result.put("studentsWithoutGuardian", studentsWithoutGuardian);
        result.put("activeTeachers", activeTeachers);
        result.put("activeSchoolAdmins", activeSchoolAdmins);
        result.put("activeSections", activeSections);
        result.put("activeGates", activeGates);
        result.put("manualChecks", manualChecks);
        result.put("checks", checks);
        result.put("reviewRequestedAt", timestampString(stateDoc, "reviewRequestedAt"));
        result.put("approvedAt", timestampString(stateDoc, "approvedAt"));
        result.put("approvalNote", safe(stateDoc.getString("approvalNote")));
        result.put("lastAssessedAt", Instant.now().toString());
        return result;
    }

    public Map<String, Object> saveManualChecks(String schoolId, Map<String, Boolean> updates, String actorUid) throws Exception {
        if (updates == null || updates.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one manual check is required");
        for (String key : updates.keySet()) {
            if (!MANUAL_CHECK_KEYS.contains(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported manual check: " + key);
        }
        Map<String, Object> write = new HashMap<>();
        for (Map.Entry<String, Boolean> entry : updates.entrySet()) {
            write.put("manualChecks." + entry.getKey(), Boolean.TRUE.equals(entry.getValue()));
        }
        write.put("manualChecksUpdatedAt", FieldValue.serverTimestamp());
        write.put("manualChecksUpdatedBy", actorUid);
        readinessRef(schoolId).set(write, SetOptions.merge()).get();
        return assess(schoolId);
    }

    public Map<String, Object> requestReview(String schoolId, String actorUid) throws Exception {
        Map<String, Object> assessment = assess(schoolId);
        if (!Boolean.TRUE.equals(assessment.get("readyForReview"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete all required launch checks before requesting platform review");
        }
        readinessRef(schoolId).set(Map.of(
                "reviewStatus", REVIEW_REQUESTED,
                "reviewRequestedAt", FieldValue.serverTimestamp(),
                "reviewRequestedBy", actorUid,
                "updatedAt", FieldValue.serverTimestamp()
        ), SetOptions.merge()).get();
        updateSchoolSummary(schoolId, REVIEW_REQUESTED);
        return assess(schoolId);
    }

    public Map<String, Object> approve(String schoolId, String actorUid, String note) throws Exception {
        Map<String, Object> assessment = assess(schoolId);
        if (!Boolean.TRUE.equals(assessment.get("readyForReview"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This school still has required launch blockers");
        }
        Map<String, Object> write = new HashMap<>();
        write.put("reviewStatus", APPROVED);
        write.put("approvedAt", FieldValue.serverTimestamp());
        write.put("approvedBy", actorUid);
        write.put("approvalNote", safe(note));
        write.put("updatedAt", FieldValue.serverTimestamp());
        readinessRef(schoolId).set(write, SetOptions.merge()).get();
        updateSchoolSummary(schoolId, APPROVED);
        return assess(schoolId);
    }

    public Map<String, Object> reopen(String schoolId, String actorUid, String reason) throws Exception {
        Map<String, Object> write = new HashMap<>();
        write.put("reviewStatus", DRAFT);
        write.put("reopenedAt", FieldValue.serverTimestamp());
        write.put("reopenedBy", actorUid);
        write.put("reopenReason", safe(reason));
        write.put("updatedAt", FieldValue.serverTimestamp());
        readinessRef(schoolId).set(write, SetOptions.merge()).get();
        updateSchoolSummary(schoolId, DRAFT);
        return assess(schoolId);
    }

    private void updateSchoolSummary(String schoolId, String status) throws Exception {
        firestore.collection("schools").document(schoolId).update(
                "launchStatus", status,
                "launchStatusUpdatedAt", FieldValue.serverTimestamp()).get();
    }

    private DocumentReference readinessRef(String schoolId) {
        return firestore.collection("schoolLaunchReadiness").document(schoolId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Boolean> manualChecks(DocumentSnapshot doc) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        MANUAL_CHECK_KEYS.forEach(k -> result.put(k, false));
        if (doc != null && doc.exists()) {
            Object raw = doc.get("manualChecks");
            if (raw instanceof Map<?, ?> map) {
                for (String key : MANUAL_CHECK_KEYS) result.put(key, Boolean.TRUE.equals(map.get(key)));
            }
        }
        return result;
    }

    private static boolean isActiveStudent(DocumentSnapshot student) {
        String status = safe(student.getString("status"));
        return status.isBlank() || "active".equalsIgnoreCase(status);
    }

    private static void required(List<Map<String, Object>> checks, String key, String label,
                                 boolean pass, String detail, String action) {
        checks.add(check(key, label, pass ? "pass" : "blocker", true, detail, action));
    }

    private static void requiredManual(List<Map<String, Object>> checks, String key, String label,
                                       Boolean complete, String detail) {
        checks.add(check(key, label, Boolean.TRUE.equals(complete) ? "pass" : "blocker", true, detail, "manual"));
    }

    private static void warning(List<Map<String, Object>> checks, String key, String label, String detail, String action) {
        checks.add(check(key, label, "warning", false, detail, action));
    }

    private static void pass(List<Map<String, Object>> checks, String key, String label, String detail, boolean required, String action) {
        checks.add(check(key, label, "pass", required, detail, action));
    }

    private static Map<String, Object> check(String key, String label, String status, boolean required, String detail, String action) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("label", label);
        result.put("status", status);
        result.put("required", required);
        result.put("detail", detail);
        result.put("action", action);
        return result;
    }

    private static String normalizeStatus(String value) {
        if (APPROVED.equals(value) || REVIEW_REQUESTED.equals(value)) return value;
        return DRAFT;
    }

    private static String timestampString(DocumentSnapshot doc, String field) {
        if (doc == null || !doc.exists() || doc.getTimestamp(field) == null) return null;
        return doc.getTimestamp(field).toDate().toInstant().toString();
    }

    private static String sampleSuffix(List<String> names) {
        List<String> clean = names.stream().filter(v -> v != null && !v.isBlank()).toList();
        return clean.isEmpty() ? "." : ": " + String.join(", ", clean) + (clean.size() >= 8 ? "…" : ".");
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String value(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}

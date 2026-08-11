package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.TenantUsageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Production student lifecycle management for a single school tenant.
 * Historical students are never hard-deleted; admins move them through
 * active/inactive/transferred/graduated/archived states instead.
 */
@RestController
@RequestMapping("/api/school-admin/students")
public class StudentLifecycleController {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "active", "inactive", "transferred", "graduated", "archived");
    private static final int FIRESTORE_BATCH_LIMIT = 450;

    private final Firestore firestore;
    private final AuditService auditService;
    private final TenantUsageService tenantUsageService;

    public StudentLifecycleController(Firestore firestore, AuditService auditService, TenantUsageService tenantUsageService) {
        this.firestore = firestore;
        this.auditService = auditService;
        this.tenantUsageService = tenantUsageService;
    }

    @GetMapping("/lifecycle")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> listStudents(
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        String requestedStatus = safe(status).toLowerCase(Locale.ROOT);
        if (!requestedStatus.isBlank() && !ALLOWED_STATUSES.contains(requestedStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unsupported student status"));
        }

        List<Map<String, Object>> students = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("students")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            String actualStatus = normalizedStatus(doc);
            if (!requestedStatus.isBlank() && !requestedStatus.equals(actualStatus)) continue;
            students.add(studentMap(doc, actualStatus));
        }

        students.sort(Comparator.comparing(
                item -> String.valueOf(item.get("fullName")),
                String.CASE_INSENSITIVE_ORDER));

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String s : List.of("active", "inactive", "transferred", "graduated", "archived")) {
            counts.put(s, 0);
        }
        for (QueryDocumentSnapshot doc : firestore.collection("students")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            String s = normalizedStatus(doc);
            counts.put(s, counts.getOrDefault(s, 0) + 1);
        }

        return ResponseEntity.ok(Map.of("students", students, "counts", counts));
    }

    @PutMapping("/{studentId}/status")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> updateStatus(
            @PathVariable String studentId,
            @RequestBody StudentStatusRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        String nextStatus = safe(req.getStatus()).toLowerCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(nextStatus)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Status must be active, inactive, transferred, graduated, or archived"));
        }

        DocumentReference ref = firestore.collection("students").document(studentId);
        DocumentSnapshot student = ref.get().get();
        if (!student.exists() || !admin.getSchoolId().equals(student.getString("schoolId"))) {
            throw new NotFoundException("Student not found in your school");
        }

        String previousStatus = normalizedStatus(student);
        if (previousStatus.equals(nextStatus)) {
            return ResponseEntity.ok(Map.of("studentId", studentId, "status", nextStatus));
        }

        boolean activating = "active".equals(nextStatus) && !"active".equals(previousStatus);
        boolean deactivating = !"active".equals(nextStatus) && "active".equals(previousStatus);
        if (activating) tenantUsageService.reserve(admin.getSchoolId(), TenantUsageService.STUDENTS, 1);

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", nextStatus);
        updates.put("statusReason", safe(req.getReason()));
        updates.put("statusChangedAt", FieldValue.serverTimestamp());
        updates.put("statusChangedBy", admin.getUid());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        updates.put("updatedBy", admin.getUid());
        try {
            ref.update(updates).get();
        } catch (Exception e) {
            if (activating) tenantUsageService.release(admin.getSchoolId(), TenantUsageService.STUDENTS, 1);
            throw e;
        }
        if (deactivating) tenantUsageService.release(admin.getSchoolId(), TenantUsageService.STUDENTS, 1);

        // A non-active student must not retain a still-valid pickup QR.
        if (!"active".equals(nextStatus)) {
            for (QueryDocumentSnapshot token : firestore.collection("pickupTokens")
                    .whereEqualTo("studentId", studentId)
                    .get().get().getDocuments()) {
                if (Boolean.TRUE.equals(token.getBoolean("used"))) continue;
                token.getReference().update(
                        "used", true,
                        "invalidatedReason", "student_" + nextStatus,
                        "invalidatedAt", FieldValue.serverTimestamp()).get();
            }
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("previousStatus", previousStatus);
        auditDetails.put("status", nextStatus);
        auditDetails.put("reason", safe(req.getReason()));
        auditDetails.put("studentName", safe(student.getString("fullName")));
        auditService.record(admin, "student.status_changed", "student", studentId, auditDetails);

        return ResponseEntity.ok(Map.of("studentId", studentId, "status", nextStatus));
    }

    /**
     * Preview or execute an end-of-year promotion. Auto-mapping finds an active
     * target section whose grade is the next numeric grade and whose section
     * name matches the source section. Explicit source->target gradeSectionId
     * mappings may be supplied to handle renamed sections or non-numeric levels.
     */
    @PostMapping("/promote")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> promote(
            @RequestBody PromotionRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        String targetAcademicYearId = safe(req.getTargetAcademicYearId());
        if (targetAcademicYearId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "targetAcademicYearId is required"));
        }

        DocumentSnapshot targetYear = firestore.collection("academicYears")
                .document(targetAcademicYearId).get().get();
        if (!targetYear.exists() || !admin.getSchoolId().equals(targetYear.getString("schoolId"))) {
            throw new NotFoundException("Target academic year not found in your school");
        }

        List<QueryDocumentSnapshot> targetSections = firestore.collection("gradeSections")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments();

        Map<String, DocumentSnapshot> targetById = new HashMap<>();
        Map<String, DocumentSnapshot> autoTargets = new HashMap<>();
        for (QueryDocumentSnapshot doc : targetSections) {
            if (!targetAcademicYearId.equals(safe(doc.getString("academicYearId")))) continue;
            if (Boolean.FALSE.equals(doc.getBoolean("active"))) continue;
            targetById.put(doc.getId(), doc);
            autoTargets.put(sectionKey(safe(doc.getString("gradeLevel")), safe(doc.getString("sectionName"))), doc);
        }

        Map<String, String> mappings = req.getSectionMappings() == null
                ? Map.of() : req.getSectionMappings();

        List<PromotionCandidate> ready = new ArrayList<>();
        List<Map<String, Object>> unresolved = new ArrayList<>();
        List<Map<String, Object>> graduating = new ArrayList<>();

        for (QueryDocumentSnapshot student : firestore.collection("students")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            if (!"active".equals(normalizedStatus(student))) continue;
            if (targetAcademicYearId.equals(safe(student.getString("academicYearId")))) continue;

            String sourceSectionId = safe(student.getString("gradeSectionId"));
            String grade = safe(student.getString("grade"));
            String section = safe(student.getString("section"));

            DocumentSnapshot target = null;
            String explicitTargetId = safe(mappings.get(sourceSectionId));
            if (!explicitTargetId.isBlank()) target = targetById.get(explicitTargetId);

            String nextGrade = nextNumericGrade(grade);
            if (target == null && !nextGrade.isBlank()) {
                target = autoTargets.get(sectionKey(nextGrade, section));
            }

            if (target == null) {
                Map<String, Object> issue = baseStudent(student);
                issue.put("reason", nextGrade.isBlank()
                        ? "No automatic next grade could be determined"
                        : "No active target section found for " + nextGrade + " / " + section);
                unresolved.add(issue);
                continue;
            }

            ready.add(new PromotionCandidate(
                    student,
                    target.getId(),
                    safe(target.getString("gradeLevel")),
                    safe(target.getString("sectionName")),
                    targetAcademicYearId,
                    safe(targetYear.getString("name"))));
        }

        // Optional explicit list of students to graduate instead of promote.
        Set<String> graduateIds = req.getGraduateStudentIds() == null
                ? Set.of() : new HashSet<>(req.getGraduateStudentIds());
        if (!graduateIds.isEmpty()) {
            Iterator<PromotionCandidate> iterator = ready.iterator();
            while (iterator.hasNext()) {
                PromotionCandidate candidate = iterator.next();
                if (graduateIds.contains(candidate.student().getId())) {
                    graduating.add(baseStudent(candidate.student()));
                    iterator.remove();
                }
            }
        }

        boolean dryRun = req.isDryRun();
        if (!dryRun && !unresolved.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Resolve all unmapped students before promotion",
                    "unresolvedCount", unresolved.size(),
                    "unresolved", unresolved.subList(0, Math.min(100, unresolved.size()))));
        }

        int promotedCount = 0;
        int graduatedCount = 0;
        if (!dryRun) {
            WriteBatch batch = firestore.batch();
            int batchCount = 0;

            for (PromotionCandidate candidate : ready) {
                Map<String, Object> updates = new HashMap<>();
                updates.put("gradeSectionId", candidate.targetGradeSectionId());
                updates.put("grade", candidate.targetGrade());
                updates.put("section", candidate.targetSection());
                updates.put("academicYearId", candidate.targetAcademicYearId());
                updates.put("academicYearName", candidate.targetAcademicYearName());
                updates.put("status", "active");
                updates.put("promotedAt", FieldValue.serverTimestamp());
                updates.put("promotedBy", admin.getUid());
                updates.put("updatedAt", FieldValue.serverTimestamp());
                batch.update(candidate.student().getReference(), updates);
                batchCount++;
                promotedCount++;
                if (batchCount >= FIRESTORE_BATCH_LIMIT) {
                    batch.commit().get();
                    batch = firestore.batch();
                    batchCount = 0;
                }
            }

            for (Map<String, Object> item : graduating) {
                String studentId = String.valueOf(item.get("studentId"));
                DocumentReference ref = firestore.collection("students").document(studentId);
                batch.update(ref,
                        "status", "graduated",
                        "statusReason", "End-of-year promotion",
                        "statusChangedAt", FieldValue.serverTimestamp(),
                        "statusChangedBy", admin.getUid(),
                        "updatedAt", FieldValue.serverTimestamp());
                batchCount++;
                graduatedCount++;
                if (batchCount >= FIRESTORE_BATCH_LIMIT) {
                    batch.commit().get();
                    batch = firestore.batch();
                    batchCount = 0;
                }
            }
            if (batchCount > 0) batch.commit().get();

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("targetAcademicYearId", targetAcademicYearId);
            details.put("targetAcademicYearName", safe(targetYear.getString("name")));
            details.put("promotedCount", promotedCount);
            details.put("graduatedCount", graduatedCount);
            auditService.record(admin, "students.bulk_promoted", "academicYear", targetAcademicYearId, details);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dryRun", dryRun);
        body.put("targetAcademicYearId", targetAcademicYearId);
        body.put("targetAcademicYearName", safe(targetYear.getString("name")));
        body.put("readyCount", ready.size());
        body.put("unresolvedCount", unresolved.size());
        body.put("graduatingCount", graduating.size());
        body.put("promotedCount", promotedCount);
        body.put("graduatedCount", graduatedCount);
        body.put("readySample", ready.stream().limit(50).map(c -> {
            Map<String, Object> m = baseStudent(c.student());
            m.put("targetGrade", c.targetGrade());
            m.put("targetSection", c.targetSection());
            return m;
        }).toList());
        body.put("unresolved", unresolved.subList(0, Math.min(100, unresolved.size())));
        return ResponseEntity.ok(body);
    }

    private static Map<String, Object> studentMap(DocumentSnapshot doc, String status) {
        Map<String, Object> m = baseStudent(doc);
        m.put("status", status);
        m.put("academicYearId", safe(doc.getString("academicYearId")));
        m.put("academicYearName", safe(doc.getString("academicYearName")));
        m.put("gradeSectionId", safe(doc.getString("gradeSectionId")));
        m.put("studentNumber", safe(doc.getString("studentNumber")));
        return m;
    }

    private static Map<String, Object> baseStudent(DocumentSnapshot doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("studentId", doc.getId());
        m.put("fullName", safe(doc.getString("fullName")));
        m.put("grade", safe(doc.getString("grade")));
        m.put("section", safe(doc.getString("section")));
        return m;
    }

    private static String normalizedStatus(DocumentSnapshot doc) {
        String status = safe(doc.getString("status")).toLowerCase(Locale.ROOT);
        return ALLOWED_STATUSES.contains(status) ? status : "active"; // legacy students remain active
    }

    private static String nextNumericGrade(String grade) {
        String digits = grade.replaceAll("\\D+", "");
        if (digits.isBlank()) return "";
        try {
            int current = Integer.parseInt(digits);
            int next = current + 1;
            String lower = grade.toLowerCase(Locale.ROOT);
            if (lower.contains("grade")) return "Grade " + next;
            return String.valueOf(next);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private static String sectionKey(String grade, String section) {
        return safe(grade).toLowerCase(Locale.ROOT) + "|" + safe(section).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    public static class StudentStatusRequest {
        private String status;
        private String reason;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public static class PromotionRequest {
        private String targetAcademicYearId;
        private boolean dryRun = true;
        private Map<String, String> sectionMappings;
        private List<String> graduateStudentIds;
        public String getTargetAcademicYearId() { return targetAcademicYearId; }
        public void setTargetAcademicYearId(String v) { targetAcademicYearId = v; }
        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean v) { dryRun = v; }
        public Map<String, String> getSectionMappings() { return sectionMappings; }
        public void setSectionMappings(Map<String, String> v) { sectionMappings = v; }
        public List<String> getGraduateStudentIds() { return graduateStudentIds; }
        public void setGraduateStudentIds(List<String> v) { graduateStudentIds = v; }
    }

    private record PromotionCandidate(
            DocumentSnapshot student,
            String targetGradeSectionId,
            String targetGrade,
            String targetSection,
            String targetAcademicYearId,
            String targetAcademicYearName) {}
}

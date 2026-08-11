package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Production academic structure for one school tenant.
 *
 * Data is intentionally stored as top-level collections with schoolId fields so
 * tenant checks are explicit and easy to audit:
 *   academicYears/{id}
 *   gradeSections/{id}
 *
 * Existing student/teacher records keep their grade/section string fields for
 * backwards compatibility, while new records can also reference gradeSectionId
 * and academicYearId.
 */
@RestController
@RequestMapping("/api")
public class AcademicStructureController {

    private final Firestore firestore;
    private final AuditService auditService;

    public AcademicStructureController(Firestore firestore, AuditService auditService) {
        this.firestore = firestore;
        this.auditService = auditService;
    }

    /** Teachers and school admins may read the configured structure. */
    @GetMapping("/academic-structure")
    @PreAuthorize("hasAnyRole('teacher','school_admin')")
    public ResponseEntity<?> getStructure(@AuthenticationPrincipal FirebaseUserDetails user) throws Exception {
        String schoolId = user.getSchoolId();

        List<Map<String, Object>> years = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("academicYears")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            years.add(yearMap(doc));
        }
        years.sort(Comparator.comparing(m -> String.valueOf(m.getOrDefault("name", "")), Comparator.reverseOrder()));

        List<Map<String, Object>> sections = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            sections.add(sectionMap(doc));
        }
        sections.sort(Comparator
                .comparing((Map<String, Object> m) -> gradeSortKey(String.valueOf(m.getOrDefault("gradeLevel", ""))))
                .thenComparing(m -> String.valueOf(m.getOrDefault("sectionName", "")), String.CASE_INSENSITIVE_ORDER));

        Map<String, Object> current = years.stream()
                .filter(y -> Boolean.TRUE.equals(y.get("isCurrent")))
                .findFirst().orElse(null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("currentAcademicYear", current);
        body.put("academicYears", years);
        body.put("gradeSections", sections);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/school-admin/academic-years")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> createAcademicYear(
            @RequestBody AcademicYearRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        String name = safe(req.getName());
        if (name.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Academic year name is required"));

        // Prevent accidental duplicate labels in the same tenant.
        for (QueryDocumentSnapshot doc : firestore.collection("academicYears")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            if (name.equalsIgnoreCase(safe(doc.getString("name")))) {
                return ResponseEntity.badRequest().body(Map.of("error", "That academic year already exists"));
            }
        }

        boolean makeCurrent = req.isCurrent();
        if (makeCurrent) clearCurrentYear(admin.getSchoolId());

        var ref = firestore.collection("academicYears").document();
        Map<String, Object> data = new HashMap<>();
        data.put("schoolId", admin.getSchoolId());
        data.put("name", name);
        data.put("startDate", safe(req.getStartDate()));
        data.put("endDate", safe(req.getEndDate()));
        data.put("isCurrent", makeCurrent);
        data.put("status", "active");
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("createdBy", admin.getUid());
        ref.set(data).get();

        auditService.record(admin, "academic_year.created", "academicYear", ref.getId(),
                Map.of("name", name, "isCurrent", makeCurrent));

        return ResponseEntity.ok(Map.of("id", ref.getId(), "name", name, "isCurrent", makeCurrent));
    }

    @PutMapping("/school-admin/academic-years/{id}/current")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> setCurrentAcademicYear(
            @PathVariable String id,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot target = firestore.collection("academicYears").document(id).get().get();
        if (!target.exists() || !admin.getSchoolId().equals(target.getString("schoolId"))) {
            throw new NotFoundException("Academic year not found in your school");
        }

        clearCurrentYear(admin.getSchoolId());
        firestore.collection("academicYears").document(id).update(
                "isCurrent", true,
                "status", "active",
                "updatedAt", FieldValue.serverTimestamp(),
                "updatedBy", admin.getUid()).get();

        auditService.record(admin, "academic_year.set_current", "academicYear", id,
                Map.of("name", safe(target.getString("name"))));
        return ResponseEntity.ok(Map.of("id", id, "isCurrent", true));
    }

    @PostMapping("/school-admin/grade-sections")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> createGradeSection(
            @RequestBody GradeSectionRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        String academicYearId = safe(req.getAcademicYearId());
        String gradeLevel = safe(req.getGradeLevel());
        String sectionName = safe(req.getSectionName());
        if (academicYearId.isBlank() || gradeLevel.isBlank() || sectionName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "academicYearId, gradeLevel and sectionName are required"));
        }

        DocumentSnapshot year = firestore.collection("academicYears").document(academicYearId).get().get();
        if (!year.exists() || !admin.getSchoolId().equals(year.getString("schoolId"))) {
            throw new NotFoundException("Academic year not found in your school");
        }

        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            if (academicYearId.equals(doc.getString("academicYearId"))
                    && gradeLevel.equalsIgnoreCase(safe(doc.getString("gradeLevel")))
                    && sectionName.equalsIgnoreCase(safe(doc.getString("sectionName")))) {
                return ResponseEntity.badRequest().body(Map.of("error", "That grade and section already exists for this academic year"));
            }
        }

        var ref = firestore.collection("gradeSections").document();
        Map<String, Object> data = new HashMap<>();
        data.put("schoolId", admin.getSchoolId());
        data.put("academicYearId", academicYearId);
        data.put("academicYearName", safe(year.getString("name")));
        data.put("gradeLevel", gradeLevel);
        data.put("sectionName", sectionName);
        data.put("active", true);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("createdBy", admin.getUid());
        ref.set(data).get();

        auditService.record(admin, "grade_section.created", "gradeSection", ref.getId(),
                Map.of("academicYearId", academicYearId, "gradeLevel", gradeLevel, "sectionName", sectionName));

        return ResponseEntity.ok(Map.of(
                "id", ref.getId(),
                "academicYearId", academicYearId,
                "gradeLevel", gradeLevel,
                "sectionName", sectionName,
                "active", true));
    }

    @PutMapping("/school-admin/grade-sections/{id}/status")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> setGradeSectionStatus(
            @PathVariable String id,
            @RequestBody GradeSectionStatusRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot doc = firestore.collection("gradeSections").document(id).get().get();
        if (!doc.exists() || !admin.getSchoolId().equals(doc.getString("schoolId"))) {
            throw new NotFoundException("Grade section not found in your school");
        }

        firestore.collection("gradeSections").document(id).update(
                "active", req.isActive(),
                "updatedAt", FieldValue.serverTimestamp(),
                "updatedBy", admin.getUid()).get();

        auditService.record(admin, req.isActive() ? "grade_section.reactivated" : "grade_section.archived",
                "gradeSection", id,
                Map.of("gradeLevel", safe(doc.getString("gradeLevel")), "sectionName", safe(doc.getString("sectionName"))));

        return ResponseEntity.ok(Map.of("id", id, "active", req.isActive()));
    }

    private void clearCurrentYear(String schoolId) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("academicYears")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (Boolean.TRUE.equals(doc.getBoolean("isCurrent"))) {
                doc.getReference().update("isCurrent", false, "updatedAt", FieldValue.serverTimestamp()).get();
            }
        }
    }

    private static Map<String, Object> yearMap(DocumentSnapshot doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("name", safe(doc.getString("name")));
        m.put("startDate", safe(doc.getString("startDate")));
        m.put("endDate", safe(doc.getString("endDate")));
        m.put("isCurrent", Boolean.TRUE.equals(doc.getBoolean("isCurrent")));
        m.put("status", safe(doc.getString("status")));
        return m;
    }

    private static Map<String, Object> sectionMap(DocumentSnapshot doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("academicYearId", safe(doc.getString("academicYearId")));
        m.put("academicYearName", safe(doc.getString("academicYearName")));
        m.put("gradeLevel", safe(doc.getString("gradeLevel")));
        m.put("sectionName", safe(doc.getString("sectionName")));
        m.put("active", doc.getBoolean("active") == null || Boolean.TRUE.equals(doc.getBoolean("active")));
        return m;
    }

    private static String gradeSortKey(String grade) {
        String digits = grade.replaceAll("\\D+", "");
        if (!digits.isBlank()) {
            try { return String.format("%04d-%s", Integer.parseInt(digits), grade.toLowerCase(Locale.ROOT)); }
            catch (NumberFormatException ignored) { }
        }
        return "9999-" + grade.toLowerCase(Locale.ROOT);
    }

    private static String safe(String v) { return v == null ? "" : v.trim(); }

    public static class AcademicYearRequest {
        private String name;
        private String startDate;
        private String endDate;
        private boolean current;
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String v) { startDate = v; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String v) { endDate = v; }
        public boolean isCurrent() { return current; }
        public void setCurrent(boolean v) { current = v; }
    }

    public static class GradeSectionRequest {
        private String academicYearId;
        private String gradeLevel;
        private String sectionName;
        public String getAcademicYearId() { return academicYearId; }
        public void setAcademicYearId(String v) { academicYearId = v; }
        public String getGradeLevel() { return gradeLevel; }
        public void setGradeLevel(String v) { gradeLevel = v; }
        public String getSectionName() { return sectionName; }
        public void setSectionName(String v) { sectionName = v; }
    }

    public static class GradeSectionStatusRequest {
        private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean v) { active = v; }
    }
}

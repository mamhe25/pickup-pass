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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
 *
 * Destructive operations are intentionally conservative:
 * - current academic years cannot be archived or deleted;
 * - academic years can only be deleted when no section/student references them;
 * - grade sections can only be deleted when unused;
 * - active grade sections cannot be archived while active students or current
 *   teacher assignments still depend on them.
 */
@RestController
@RequestMapping("/api")
public class AcademicStructureController {

    private static final int WRITE_BATCH_SIZE = 400;

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
        years.sort(Comparator.comparing(
                m -> String.valueOf(m.getOrDefault("name", "")),
                Comparator.reverseOrder()
        ));

        List<Map<String, Object>> sections = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            sections.add(sectionMap(doc));
        }
        sections.sort(Comparator
                .comparing((Map<String, Object> m) ->
                        gradeSortKey(String.valueOf(m.getOrDefault("gradeLevel", ""))))
                .thenComparing(
                        m -> String.valueOf(m.getOrDefault("sectionName", "")),
                        String.CASE_INSENSITIVE_ORDER
                ));

        Map<String, Object> current = years.stream()
                .filter(y -> Boolean.TRUE.equals(y.get("isCurrent")))
                .findFirst()
                .orElse(null);

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
        if (name.isBlank()) {
            return badRequest("Academic year name is required");
        }

        String dateError = validateDateRange(req.getStartDate(), req.getEndDate());
        if (dateError != null) {
            return badRequest(dateError);
        }

        if (academicYearNameExists(admin.getSchoolId(), name, null)) {
            return badRequest("That academic year already exists");
        }

        boolean makeCurrent = req.isCurrent();
        if (makeCurrent) {
            clearCurrentYear(admin.getSchoolId());
        }

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

        auditService.record(
                admin,
                "academic_year.created",
                "academicYear",
                ref.getId(),
                Map.of("name", name, "isCurrent", makeCurrent)
        );

        return ResponseEntity.ok(Map.of(
                "id", ref.getId(),
                "name", name,
                "isCurrent", makeCurrent
        ));
    }

    @PutMapping("/school-admin/academic-years/{id}")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> updateAcademicYear(
            @PathVariable String id,
            @RequestBody AcademicYearUpdateRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot target = requireAcademicYear(id, admin.getSchoolId());
        String name = safe(req.getName());
        if (name.isBlank()) {
            return badRequest("Academic year name is required");
        }

        String dateError = validateDateRange(req.getStartDate(), req.getEndDate());
        if (dateError != null) {
            return badRequest(dateError);
        }

        if (academicYearNameExists(admin.getSchoolId(), name, id)) {
            return badRequest("That academic year already exists");
        }

        String oldName = safe(target.getString("name"));
        Map<String, Object> update = new HashMap<>();
        update.put("name", name);
        update.put("startDate", safe(req.getStartDate()));
        update.put("endDate", safe(req.getEndDate()));
        update.put("updatedAt", FieldValue.serverTimestamp());
        update.put("updatedBy", admin.getUid());
        target.getReference().update(update).get();

        if (!oldName.equals(name)) {
            propagateAcademicYearName(admin.getSchoolId(), id, name);
        }

        auditService.record(
                admin,
                "academic_year.updated",
                "academicYear",
                id,
                Map.of(
                        "oldName", oldName,
                        "name", name,
                        "startDate", safe(req.getStartDate()),
                        "endDate", safe(req.getEndDate())
                )
        );

        return ResponseEntity.ok(Map.of("id", id, "name", name, "status", "updated"));
    }

    @PutMapping("/school-admin/academic-years/{id}/current")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> setCurrentAcademicYear(
            @PathVariable String id,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot target = requireAcademicYear(id, admin.getSchoolId());

        clearCurrentYear(admin.getSchoolId());
        target.getReference().update(
                "isCurrent", true,
                "status", "active",
                "updatedAt", FieldValue.serverTimestamp(),
                "updatedBy", admin.getUid()
        ).get();

        auditService.record(
                admin,
                "academic_year.set_current",
                "academicYear",
                id,
                Map.of("name", safe(target.getString("name")))
        );
        return ResponseEntity.ok(Map.of("id", id, "isCurrent", true));
    }

    @PutMapping("/school-admin/academic-years/{id}/status")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> setAcademicYearStatus(
            @PathVariable String id,
            @RequestBody AcademicYearStatusRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot target = requireAcademicYear(id, admin.getSchoolId());
        boolean active = req.isActive();

        if (!active && Boolean.TRUE.equals(target.getBoolean("isCurrent"))) {
            return conflict("Choose another current academic year before archiving this one");
        }

        if (!active && hasActiveSections(id, admin.getSchoolId())) {
            return conflict("Archive the active grade sections in this academic year first");
        }

        if (!active && hasActiveStudentsForAcademicYear(id, admin.getSchoolId())) {
            return conflict("Move or deactivate active students from this academic year before archiving it");
        }

        target.getReference().update(
                "status", active ? "active" : "archived",
                "updatedAt", FieldValue.serverTimestamp(),
                "updatedBy", admin.getUid()
        ).get();

        auditService.record(
                admin,
                active ? "academic_year.reactivated" : "academic_year.archived",
                "academicYear",
                id,
                Map.of("name", safe(target.getString("name")))
        );

        return ResponseEntity.ok(Map.of(
                "id", id,
                "status", active ? "active" : "archived"
        ));
    }

    @DeleteMapping("/school-admin/academic-years/{id}")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> deleteAcademicYear(
            @PathVariable String id,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot target = requireAcademicYear(id, admin.getSchoolId());

        if (Boolean.TRUE.equals(target.getBoolean("isCurrent"))) {
            return conflict("The current academic year cannot be deleted");
        }

        if (hasAnySection(id, admin.getSchoolId())) {
            return conflict("This academic year still contains grade sections. Archive or remove those sections first");
        }

        if (hasStudentsForAcademicYear(id, admin.getSchoolId())) {
            return conflict("This academic year is already referenced by student records and must be kept for history");
        }

        String name = safe(target.getString("name"));
        target.getReference().delete().get();

        auditService.record(
                admin,
                "academic_year.deleted",
                "academicYear",
                id,
                Map.of("name", name)
        );

        return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
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
            return badRequest("academicYearId, gradeLevel and sectionName are required");
        }

        DocumentSnapshot year = requireAcademicYear(academicYearId, admin.getSchoolId());
        if ("archived".equalsIgnoreCase(safe(year.getString("status")))) {
            return conflict("Reactivate the academic year before adding new grade sections");
        }

        if (gradeSectionExists(admin.getSchoolId(), academicYearId, gradeLevel, sectionName, null)) {
            return badRequest("That grade and section already exists for this academic year");
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

        auditService.record(
                admin,
                "grade_section.created",
                "gradeSection",
                ref.getId(),
                Map.of(
                        "academicYearId", academicYearId,
                        "gradeLevel", gradeLevel,
                        "sectionName", sectionName
                )
        );

        return ResponseEntity.ok(Map.of(
                "id", ref.getId(),
                "academicYearId", academicYearId,
                "gradeLevel", gradeLevel,
                "sectionName", sectionName,
                "active", true
        ));
    }

    @PutMapping("/school-admin/grade-sections/{id}")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> updateGradeSection(
            @PathVariable String id,
            @RequestBody GradeSectionUpdateRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot target = requireGradeSection(id, admin.getSchoolId());
        String gradeLevel = safe(req.getGradeLevel());
        String sectionName = safe(req.getSectionName());
        if (gradeLevel.isBlank() || sectionName.isBlank()) {
            return badRequest("Grade level and section name are required");
        }

        String academicYearId = safe(target.getString("academicYearId"));
        DocumentSnapshot academicYear = requireAcademicYear(
                academicYearId,
                admin.getSchoolId()
        );
        boolean currentAcademicYear = Boolean.TRUE.equals(academicYear.getBoolean("isCurrent"));

        if (gradeSectionExists(
                admin.getSchoolId(),
                academicYearId,
                gradeLevel,
                sectionName,
                id
        )) {
            return badRequest("That grade and section already exists for this academic year");
        }

        String oldGrade = safe(target.getString("gradeLevel"));
        String oldSection = safe(target.getString("sectionName"));

        target.getReference().update(
                "gradeLevel", gradeLevel,
                "sectionName", sectionName,
                "updatedAt", FieldValue.serverTimestamp(),
                "updatedBy", admin.getUid()
        ).get();

        propagateGradeSectionToStudents(
                admin.getSchoolId(),
                target,
                oldGrade,
                oldSection,
                gradeLevel,
                sectionName,
                safe(academicYear.getString("name")),
                currentAcademicYear
        );

        if (currentAcademicYear) {
            propagateCurrentTeacherAssignments(
                    admin.getSchoolId(),
                    oldGrade,
                    oldSection,
                    gradeLevel,
                    sectionName
            );
        }

        auditService.record(
                admin,
                "grade_section.updated",
                "gradeSection",
                id,
                Map.of(
                        "oldGradeLevel", oldGrade,
                        "oldSectionName", oldSection,
                        "gradeLevel", gradeLevel,
                        "sectionName", sectionName
                )
        );

        return ResponseEntity.ok(Map.of(
                "id", id,
                "gradeLevel", gradeLevel,
                "sectionName", sectionName,
                "status", "updated"
        ));
    }

    @PutMapping("/school-admin/grade-sections/{id}/status")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> setGradeSectionStatus(
            @PathVariable String id,
            @RequestBody GradeSectionStatusRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot doc = requireGradeSection(id, admin.getSchoolId());

        if (!req.isActive()) {
            if (hasActiveStudentsForSection(doc, admin.getSchoolId())) {
                return conflict("Move or deactivate the active students in this section before archiving it");
            }

            if (isCurrentAcademicYear(
                    safe(doc.getString("academicYearId")),
                    admin.getSchoolId()
            ) && hasTeacherAssignments(
                    admin.getSchoolId(),
                    safe(doc.getString("gradeLevel")),
                    safe(doc.getString("sectionName")),
                    true
            )) {
                return conflict("Remove current teacher assignments from this section before archiving it");
            }
        } else {
            DocumentSnapshot year = requireAcademicYear(
                    safe(doc.getString("academicYearId")),
                    admin.getSchoolId()
            );
            if ("archived".equalsIgnoreCase(safe(year.getString("status")))) {
                return conflict("Reactivate the academic year before reactivating this section");
            }
        }

        doc.getReference().update(
                "active", req.isActive(),
                "updatedAt", FieldValue.serverTimestamp(),
                "updatedBy", admin.getUid()
        ).get();

        auditService.record(
                admin,
                req.isActive() ? "grade_section.reactivated" : "grade_section.archived",
                "gradeSection",
                id,
                Map.of(
                        "gradeLevel", safe(doc.getString("gradeLevel")),
                        "sectionName", safe(doc.getString("sectionName"))
                )
        );

        return ResponseEntity.ok(Map.of("id", id, "active", req.isActive()));
    }

    @DeleteMapping("/school-admin/grade-sections/{id}")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> deleteGradeSection(
            @PathVariable String id,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DocumentSnapshot doc = requireGradeSection(id, admin.getSchoolId());

        if (hasAnyStudentsForSection(doc, admin.getSchoolId())) {
            return conflict("This grade section is already referenced by student records and must be kept for history");
        }

        if (isCurrentAcademicYear(
                safe(doc.getString("academicYearId")),
                admin.getSchoolId()
        ) && hasTeacherAssignments(
                admin.getSchoolId(),
                safe(doc.getString("gradeLevel")),
                safe(doc.getString("sectionName")),
                false
        )) {
            return conflict("Remove teacher assignments from this section before deleting it");
        }

        String label = safe(doc.getString("gradeLevel")) + " · " + safe(doc.getString("sectionName"));
        doc.getReference().delete().get();

        auditService.record(
                admin,
                "grade_section.deleted",
                "gradeSection",
                id,
                Map.of("label", label)
        );

        return ResponseEntity.ok(Map.of("id", id, "status", "deleted"));
    }

    private DocumentSnapshot requireAcademicYear(String id, String schoolId) throws Exception {
        DocumentSnapshot target = firestore.collection("academicYears").document(id).get().get();
        if (!target.exists() || !schoolId.equals(target.getString("schoolId"))) {
            throw new NotFoundException("Academic year not found in your school");
        }
        return target;
    }

    private DocumentSnapshot requireGradeSection(String id, String schoolId) throws Exception {
        DocumentSnapshot target = firestore.collection("gradeSections").document(id).get().get();
        if (!target.exists() || !schoolId.equals(target.getString("schoolId"))) {
            throw new NotFoundException("Grade section not found in your school");
        }
        return target;
    }

    private boolean academicYearNameExists(
            String schoolId,
            String name,
            String excludingId
    ) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("academicYears")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (excludingId != null && excludingId.equals(doc.getId())) {
                continue;
            }
            if (name.equalsIgnoreCase(safe(doc.getString("name")))) {
                return true;
            }
        }
        return false;
    }

    private boolean gradeSectionExists(
            String schoolId,
            String academicYearId,
            String gradeLevel,
            String sectionName,
            String excludingId
    ) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (excludingId != null && excludingId.equals(doc.getId())) {
                continue;
            }
            if (academicYearId.equals(doc.getString("academicYearId"))
                    && gradeLevel.equalsIgnoreCase(safe(doc.getString("gradeLevel")))
                    && sectionName.equalsIgnoreCase(safe(doc.getString("sectionName")))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveSections(String academicYearId, String schoolId) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("academicYearId", academicYearId).get().get().getDocuments()) {
            if (schoolId.equals(doc.getString("schoolId"))
                    && !Boolean.FALSE.equals(doc.getBoolean("active"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnySection(String academicYearId, String schoolId) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("academicYearId", academicYearId).get().get().getDocuments()) {
            if (schoolId.equals(doc.getString("schoolId"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStudentsForAcademicYear(String academicYearId, String schoolId) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("students")
                .whereEqualTo("academicYearId", academicYearId).get().get().getDocuments()) {
            if (schoolId.equals(doc.getString("schoolId"))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveStudentsForAcademicYear(
            String academicYearId,
            String schoolId
    ) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("students")
                .whereEqualTo("academicYearId", academicYearId).get().get().getDocuments()) {
            if (!schoolId.equals(doc.getString("schoolId"))) {
                continue;
            }
            String status = safe(doc.getString("status"));
            if (status.isBlank() || "active".equalsIgnoreCase(status)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveStudentsForSection(
            DocumentSnapshot sectionDoc,
            String schoolId
    ) throws Exception {
        boolean currentYear = isCurrentAcademicYear(
                safe(sectionDoc.getString("academicYearId")),
                schoolId
        );

        for (QueryDocumentSnapshot student : firestore.collection("students")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (!studentMatchesSection(student, sectionDoc, currentYear)) {
                continue;
            }

            String status = safe(student.getString("status"));
            if (status.isBlank() || "active".equalsIgnoreCase(status)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnyStudentsForSection(
            DocumentSnapshot sectionDoc,
            String schoolId
    ) throws Exception {
        boolean currentYear = isCurrentAcademicYear(
                safe(sectionDoc.getString("academicYearId")),
                schoolId
        );

        for (QueryDocumentSnapshot student : firestore.collection("students")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (studentMatchesSection(student, sectionDoc, currentYear)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Matches modern gradeSectionId references and legacy free-text records.
     *
     * Very old students may not have gradeSectionId/academicYearId. Those
     * records are matched by grade+section only for the current academic year,
     * where the relationship is operationally unambiguous.
     */
    private boolean studentMatchesSection(
            DocumentSnapshot student,
            DocumentSnapshot sectionDoc,
            boolean sectionIsCurrentYear
    ) {
        String sectionId = sectionDoc.getId();
        String studentSectionId = safe(student.getString("gradeSectionId"));

        if (sectionId.equals(studentSectionId)) {
            return true;
        }
        if (!studentSectionId.isBlank()) {
            return false;
        }

        if (!safe(sectionDoc.getString("gradeLevel"))
                .equalsIgnoreCase(safe(student.getString("grade")))) {
            return false;
        }
        if (!safe(sectionDoc.getString("sectionName"))
                .equalsIgnoreCase(safe(student.getString("section")))) {
            return false;
        }

        String sectionYearId = safe(sectionDoc.getString("academicYearId"));
        String studentYearId = safe(student.getString("academicYearId"));
        if (!studentYearId.isBlank()) {
            return sectionYearId.equals(studentYearId);
        }

        return sectionIsCurrentYear;
    }

    @SuppressWarnings("unchecked")
    private boolean hasTeacherAssignments(
            String schoolId,
            String grade,
            String section,
            boolean activeOnly
    ) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("users")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (!"teacher".equals(doc.getString("role"))) {
                continue;
            }
            if (activeOnly && Boolean.FALSE.equals(doc.getBoolean("isActive"))) {
                continue;
            }

            Object raw = doc.get("assignedSections");
            if (!(raw instanceof List<?> assigned)) {
                continue;
            }

            for (Object entry : assigned) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                if (grade.equalsIgnoreCase(safe(String.valueOf(map.get("grade"))))
                        && section.equalsIgnoreCase(safe(String.valueOf(map.get("section"))))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isCurrentAcademicYear(String academicYearId, String schoolId) throws Exception {
        if (academicYearId.isBlank()) {
            return false;
        }
        DocumentSnapshot year = firestore.collection("academicYears").document(academicYearId).get().get();
        return year.exists()
                && schoolId.equals(year.getString("schoolId"))
                && Boolean.TRUE.equals(year.getBoolean("isCurrent"));
    }

    private void clearCurrentYear(String schoolId) throws Exception {
        for (QueryDocumentSnapshot doc : firestore.collection("academicYears")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (Boolean.TRUE.equals(doc.getBoolean("isCurrent"))) {
                doc.getReference().update(
                        "isCurrent", false,
                        "updatedAt", FieldValue.serverTimestamp()
                ).get();
            }
        }
    }

    private void propagateAcademicYearName(
            String schoolId,
            String academicYearId,
            String newName
    ) throws Exception {
        List<DocumentReference> refs = new ArrayList<>();

        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("academicYearId", academicYearId).get().get().getDocuments()) {
            if (schoolId.equals(doc.getString("schoolId"))) {
                refs.add(doc.getReference());
            }
        }
        batchUpdate(refs, Map.of("academicYearName", newName));

        refs.clear();
        for (QueryDocumentSnapshot doc : firestore.collection("students")
                .whereEqualTo("academicYearId", academicYearId).get().get().getDocuments()) {
            if (schoolId.equals(doc.getString("schoolId"))) {
                refs.add(doc.getReference());
            }
        }
        batchUpdate(refs, Map.of("academicYearName", newName));
    }

    private void propagateGradeSectionToStudents(
            String schoolId,
            DocumentSnapshot sectionDoc,
            String oldGrade,
            String oldSection,
            String newGrade,
            String newSection,
            String academicYearName,
            boolean currentAcademicYear
    ) throws Exception {
        WriteBatch batch = firestore.batch();
        int pending = 0;

        for (QueryDocumentSnapshot student : firestore.collection("students")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {

            String linkedSectionId = safe(student.getString("gradeSectionId"));
            boolean directReference = sectionDoc.getId().equals(linkedSectionId);

            boolean legacyReference = false;
            if (linkedSectionId.isBlank()
                    && oldGrade.equalsIgnoreCase(safe(student.getString("grade")))
                    && oldSection.equalsIgnoreCase(safe(student.getString("section")))) {

                String studentYearId = safe(student.getString("academicYearId"));
                String sectionYearId = safe(sectionDoc.getString("academicYearId"));

                legacyReference = (!studentYearId.isBlank() && sectionYearId.equals(studentYearId))
                        || (studentYearId.isBlank() && currentAcademicYear);
            }

            if (!directReference && !legacyReference) {
                continue;
            }

            Map<String, Object> update = new HashMap<>();
            update.put("grade", newGrade);
            update.put("section", newSection);
            update.put("updatedAt", FieldValue.serverTimestamp());

            // When a legacy record can be matched unambiguously, repair the
            // structured references while applying the rename.
            if (legacyReference) {
                update.put("gradeSectionId", sectionDoc.getId());
                update.put("academicYearId", safe(sectionDoc.getString("academicYearId")));
                update.put("academicYearName", academicYearName);
            }

            batch.update(student.getReference(), update);
            pending++;

            if (pending >= WRITE_BATCH_SIZE) {
                batch.commit().get();
                batch = firestore.batch();
                pending = 0;
            }
        }

        if (pending > 0) {
            batch.commit().get();
        }
    }

    @SuppressWarnings("unchecked")
    private void propagateCurrentTeacherAssignments(
            String schoolId,
            String oldGrade,
            String oldSection,
            String newGrade,
            String newSection
    ) throws Exception {
        WriteBatch batch = firestore.batch();
        int pending = 0;

        for (QueryDocumentSnapshot doc : firestore.collection("users")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (!"teacher".equals(doc.getString("role"))) {
                continue;
            }

            Object raw = doc.get("assignedSections");
            if (!(raw instanceof List<?> assigned)) {
                continue;
            }

            boolean changed = false;
            List<Object> updated = new ArrayList<>();
            for (Object entry : assigned) {
                if (!(entry instanceof Map<?, ?> map)) {
                    // Preserve unexpected legacy values rather than silently
                    // dropping data while repairing a current assignment.
                    updated.add(entry);
                    continue;
                }
                String grade = safe(String.valueOf(map.get("grade")));
                String section = safe(String.valueOf(map.get("section")));
                if (oldGrade.equalsIgnoreCase(grade) && oldSection.equalsIgnoreCase(section)) {
                    updated.add(Map.of("grade", newGrade, "section", newSection));
                    changed = true;
                } else {
                    updated.add(entry);
                }
            }

            if (!changed) {
                continue;
            }

            batch.update(
                    doc.getReference(),
                    "assignedSections", updated,
                    "updatedAt", FieldValue.serverTimestamp()
            );
            pending++;

            if (pending >= WRITE_BATCH_SIZE) {
                batch.commit().get();
                batch = firestore.batch();
                pending = 0;
            }
        }

        if (pending > 0) {
            batch.commit().get();
        }
    }

    private void batchUpdate(
            List<DocumentReference> refs,
            Map<String, Object> values
    ) throws Exception {
        WriteBatch batch = firestore.batch();
        int pending = 0;

        for (DocumentReference ref : refs) {
            Map<String, Object> update = new HashMap<>(values);
            update.put("updatedAt", FieldValue.serverTimestamp());
            batch.update(ref, update);
            pending++;

            if (pending >= WRITE_BATCH_SIZE) {
                batch.commit().get();
                batch = firestore.batch();
                pending = 0;
            }
        }

        if (pending > 0) {
            batch.commit().get();
        }
    }

    private static String validateDateRange(String startDate, String endDate) {
        String start = safe(startDate);
        String end = safe(endDate);

        LocalDate startParsed = null;
        LocalDate endParsed = null;

        try {
            if (!start.isBlank()) {
                startParsed = LocalDate.parse(start);
            }
            if (!end.isBlank()) {
                endParsed = LocalDate.parse(end);
            }
        } catch (DateTimeParseException ignored) {
            return "Dates must use YYYY-MM-DD format";
        }

        if (startParsed != null && endParsed != null && startParsed.isAfter(endParsed)) {
            return "Start date cannot be after end date";
        }

        return null;
    }

    private static ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private static ResponseEntity<?> conflict(String message) {
        return ResponseEntity.status(409).body(Map.of("error", message));
    }

    private static Map<String, Object> yearMap(DocumentSnapshot doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("name", safe(doc.getString("name")));
        m.put("startDate", safe(doc.getString("startDate")));
        m.put("endDate", safe(doc.getString("endDate")));
        m.put("isCurrent", Boolean.TRUE.equals(doc.getBoolean("isCurrent")));
        String status = safe(doc.getString("status"));
        m.put("status", status.isBlank() ? "active" : status);
        return m;
    }

    private static Map<String, Object> sectionMap(DocumentSnapshot doc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", doc.getId());
        m.put("academicYearId", safe(doc.getString("academicYearId")));
        m.put("academicYearName", safe(doc.getString("academicYearName")));
        m.put("gradeLevel", safe(doc.getString("gradeLevel")));
        m.put("sectionName", safe(doc.getString("sectionName")));
        m.put(
                "active",
                doc.getBoolean("active") == null || Boolean.TRUE.equals(doc.getBoolean("active"))
        );
        return m;
    }

    private static String gradeSortKey(String grade) {
        String digits = grade.replaceAll("\\D+", "");
        if (!digits.isBlank()) {
            try {
                return String.format(
                        "%04d-%s",
                        Integer.parseInt(digits),
                        grade.toLowerCase(Locale.ROOT)
                );
            } catch (NumberFormatException ignored) {
            }
        }
        return "9999-" + grade.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        if (value == null || "null".equalsIgnoreCase(value)) {
            return "";
        }
        return value.trim();
    }

    public static class AcademicYearRequest {
        private String name;
        private String startDate;
        private String endDate;
        private boolean current;

        public String getName() { return name; }
        public void setName(String value) { name = value; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String value) { startDate = value; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String value) { endDate = value; }
        public boolean isCurrent() { return current; }
        public void setCurrent(boolean value) { current = value; }
    }

    public static class AcademicYearUpdateRequest {
        private String name;
        private String startDate;
        private String endDate;

        public String getName() { return name; }
        public void setName(String value) { name = value; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String value) { startDate = value; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String value) { endDate = value; }
    }

    public static class AcademicYearStatusRequest {
        private boolean active;

        public boolean isActive() { return active; }
        public void setActive(boolean value) { active = value; }
    }

    public static class GradeSectionRequest {
        private String academicYearId;
        private String gradeLevel;
        private String sectionName;

        public String getAcademicYearId() { return academicYearId; }
        public void setAcademicYearId(String value) { academicYearId = value; }
        public String getGradeLevel() { return gradeLevel; }
        public void setGradeLevel(String value) { gradeLevel = value; }
        public String getSectionName() { return sectionName; }
        public void setSectionName(String value) { sectionName = value; }
    }

    public static class GradeSectionUpdateRequest {
        private String gradeLevel;
        private String sectionName;

        public String getGradeLevel() { return gradeLevel; }
        public void setGradeLevel(String value) { gradeLevel = value; }
        public String getSectionName() { return sectionName; }
        public void setSectionName(String value) { sectionName = value; }
    }

    public static class GradeSectionStatusRequest {
        private boolean active;

        public boolean isActive() { return active; }
        public void setActive(boolean value) { active = value; }
    }
}

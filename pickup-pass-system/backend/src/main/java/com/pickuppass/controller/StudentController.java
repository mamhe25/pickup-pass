package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.TenantUsageService;
import com.pickuppass.util.NameFormatter;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates student roster records. Deliberately separate from registering a
 * guardian (TeacherOnboardingController) — a school might add its roster
 * in bulk before parent contact info is known, so "the student exists" and
 * "who's authorized to pick them up" are two distinct, independently
 * auditable steps rather than one combined form.
 */
@RestController
@RequestMapping("/api/teacher")
public class StudentController {

    private final Firestore firestore;
    private final AuditService auditService;
    private final TenantUsageService tenantUsageService;

    public StudentController(Firestore firestore, AuditService auditService, TenantUsageService tenantUsageService) {
        this.firestore = firestore;
        this.auditService = auditService;
        this.tenantUsageService = tenantUsageService;
    }

    @PostMapping("/students")
    @PreAuthorize("hasAnyRole('teacher','school_admin')")
    public ResponseEntity<?> createStudent(
            @RequestBody CreateStudentRequest req,
            @AuthenticationPrincipal FirebaseUserDetails staff) throws Exception {

        if (req.getLastName() == null || req.getLastName().isBlank()
                || req.getFirstName() == null || req.getFirstName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "lastName and firstName are required"));
        }

        String fullName = NameFormatter.format(
                req.getLastName(), req.getFirstName(), req.getMiddleInitial(), req.getSuffix());

        DocumentReference studentRef = firestore.collection("students").document(); // auto-ID

        Map<String, Object> student = new HashMap<>();
        student.put("schoolId", staff.getSchoolId());
        // fullName is the computed "Lastname, Firstname M. Suffix" string —
        // every existing query/display (orderBy("fullName"), search, exit
        // logs, scanner verify panel) keeps working unchanged since it's
        // just reading a string field, now correctly last-name-first.
        student.put("fullName", fullName);
        student.put("lastName", req.getLastName().trim());
        student.put("firstName", req.getFirstName().trim());
        student.put("middleInitial", req.getMiddleInitial() != null ? req.getMiddleInitial().trim() : "");
        student.put("suffix", req.getSuffix() != null ? req.getSuffix().trim() : "");
        student.put("status", "active");
        // Phase 2 structured academic placement. If a gradeSectionId is supplied,
        // it is the source of truth; otherwise we resolve a legacy grade/section
        // pair against the current configured structure when one exists.
        AcademicPlacement placement = resolveAcademicPlacement(staff.getSchoolId(), req);

        if ("teacher".equals(staff.getRole())
                && !teacherHasAssignment(staff.getUid(), staff.getSchoolId(), placement)) {
            return ResponseEntity.status(403).body(Map.of(
                    "error",
                    "You can only register students in your current assigned grade and section"));
        }

        student.put("grade", placement.grade());
        student.put("section", placement.section());
        if (!placement.gradeSectionId().isBlank()) student.put("gradeSectionId", placement.gradeSectionId());
        if (!placement.academicYearId().isBlank()) student.put("academicYearId", placement.academicYearId());
        student.put("guardianUids", List.of());   // empty until a guardian is registered separately
        student.put("guardians", Map.of());
        student.put("createdAt", FieldValue.serverTimestamp());
        student.put("createdBy", staff.getUid());

        tenantUsageService.reserve(staff.getSchoolId(), TenantUsageService.STUDENTS, 1);
        try {
            studentRef.set(student).get(); // await so a write failure surfaces as an error, not a false success
        } catch (Exception e) {
            tenantUsageService.release(staff.getSchoolId(), TenantUsageService.STUDENTS, 1);
            throw e;
        }
        auditService.record(
                staff,
                "student.created",
                "student",
                studentRef.getId(),
                Map.of(
                        "fullName", fullName,
                        "grade", placement.grade(),
                        "section", placement.section(),
                        "gradeSectionId", placement.gradeSectionId(),
                        "academicYearId", placement.academicYearId()
                ));

        return ResponseEntity.ok(Map.of(
                "studentId", studentRef.getId(),
                "fullName", fullName
        ));
    }

    public static class CreateStudentRequest {
        @NotBlank private String lastName;
        @NotBlank private String firstName;
        private String middleInitial;
        private String suffix;
        private String grade;
        private String section;
        private String gradeSectionId;
        private String academicYearId;

        public String getLastName() { return lastName; }
        public void setLastName(String v) { this.lastName = v; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { this.firstName = v; }
        public String getMiddleInitial() { return middleInitial; }
        public void setMiddleInitial(String v) { this.middleInitial = v; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String v) { this.suffix = v; }
        public String getGrade() { return grade; }
        public void setGrade(String v) { this.grade = v; }
        public String getSection() { return section; }
        public void setSection(String v) { this.section = v; }
        public String getGradeSectionId() { return gradeSectionId; }
        public void setGradeSectionId(String v) { this.gradeSectionId = v; }
        public String getAcademicYearId() { return academicYearId; }
        public void setAcademicYearId(String v) { this.academicYearId = v; }
    }

    private AcademicPlacement resolveAcademicPlacement(
            String schoolId,
            CreateStudentRequest req) throws Exception {

        String currentYearId = currentAcademicYearId(schoolId);
        if (currentYearId.isBlank()) {
            throw new IllegalArgumentException(
                    "The school administrator must set a current academic year before adding students");
        }

        String requestedId = safe(req.getGradeSectionId());
        if (!requestedId.isBlank()) {
            DocumentSnapshot sectionDoc = firestore.collection("gradeSections")
                    .document(requestedId).get().get();

            if (!sectionDoc.exists()
                    || !schoolId.equals(sectionDoc.getString("schoolId"))
                    || Boolean.FALSE.equals(sectionDoc.getBoolean("active"))
                    || !currentYearId.equals(safe(sectionDoc.getString("academicYearId")))) {
                throw new IllegalArgumentException(
                        "Selected grade/section is not active in the current academic year");
            }

            String requestedYearId = safe(req.getAcademicYearId());
            if (!requestedYearId.isBlank()
                    && !currentYearId.equals(requestedYearId)) {
                throw new IllegalArgumentException(
                        "Selected grade/section does not belong to the current academic year");
            }

            return new AcademicPlacement(
                    safe(sectionDoc.getString("gradeLevel")),
                    safe(sectionDoc.getString("sectionName")),
                    sectionDoc.getId(),
                    currentYearId);
        }

        // Compatibility path for older clients: grade/section text is accepted
        // only when it resolves to an active section in the current academic
        // year. Arbitrary free-text placement is no longer permitted.
        String grade = safe(req.getGrade());
        String section = safe(req.getSection());

        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments()) {
            if (currentYearId.equals(safe(doc.getString("academicYearId")))
                    && !Boolean.FALSE.equals(doc.getBoolean("active"))
                    && grade.equalsIgnoreCase(safe(doc.getString("gradeLevel")))
                    && section.equalsIgnoreCase(safe(doc.getString("sectionName")))) {
                return new AcademicPlacement(
                        safe(doc.getString("gradeLevel")),
                        safe(doc.getString("sectionName")),
                        doc.getId(),
                        currentYearId);
            }
        }

        throw new IllegalArgumentException(
                "Choose a grade/section configured by your school admin for the current academic year");
    }

    @SuppressWarnings("unchecked")
    private boolean teacherHasAssignment(
            String uid,
            String schoolId,
            AcademicPlacement placement) throws Exception {

        DocumentSnapshot teacherDoc = firestore.collection("users")
                .document(uid).get().get();

        if (!teacherDoc.exists()
                || !schoolId.equals(teacherDoc.getString("schoolId"))
                || !"teacher".equals(teacherDoc.getString("role"))) {
            return false;
        }

        Object raw = teacherDoc.get("assignedSections");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return false;
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> section)) {
                continue;
            }

            String grade = safeObject(section.get("grade"));
            String name = safeObject(section.get("section"));
            if (placement.grade().equalsIgnoreCase(grade)
                    && placement.section().equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private String currentAcademicYearId(String schoolId) throws Exception {
        for (QueryDocumentSnapshot year : firestore.collection("academicYears")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments()) {
            if (Boolean.TRUE.equals(year.getBoolean("isCurrent"))
                    && !"archived".equalsIgnoreCase(safe(year.getString("status")))) {
                return year.getId();
            }
        }
        return "";
    }

    private static String safeObject(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private record AcademicPlacement(String grade, String section, String gradeSectionId, String academicYearId) {}
}

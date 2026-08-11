package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
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

    public StudentController(Firestore firestore, AuditService auditService) {
        this.firestore = firestore;
        this.auditService = auditService;
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
        // Phase 2 structured academic placement. If a gradeSectionId is supplied,
        // it is the source of truth; otherwise we resolve a legacy grade/section
        // pair against the current configured structure when one exists.
        AcademicPlacement placement = resolveAcademicPlacement(staff.getSchoolId(), req);
        student.put("grade", placement.grade());
        student.put("section", placement.section());
        if (!placement.gradeSectionId().isBlank()) student.put("gradeSectionId", placement.gradeSectionId());
        if (!placement.academicYearId().isBlank()) student.put("academicYearId", placement.academicYearId());
        student.put("guardianUids", List.of());   // empty until a guardian is registered separately
        student.put("guardians", Map.of());
        student.put("createdAt", FieldValue.serverTimestamp());
        student.put("createdBy", staff.getUid());

        studentRef.set(student).get(); // await so a write failure surfaces as an error, not a false success
        auditService.record(staff, "student.created", "student", studentRef.getId(), Map.of("fullName", fullName));

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

    private AcademicPlacement resolveAcademicPlacement(String schoolId, CreateStudentRequest req) throws Exception {
        String requestedId = req.getGradeSectionId() == null ? "" : req.getGradeSectionId().trim();
        if (!requestedId.isBlank()) {
            DocumentSnapshot sectionDoc = firestore.collection("gradeSections").document(requestedId).get().get();
            if (!sectionDoc.exists() || !schoolId.equals(sectionDoc.getString("schoolId"))
                    || Boolean.FALSE.equals(sectionDoc.getBoolean("active"))) {
                throw new IllegalArgumentException("Selected grade/section is not active in your school");
            }
            return new AcademicPlacement(
                    safe(sectionDoc.getString("gradeLevel")),
                    safe(sectionDoc.getString("sectionName")),
                    sectionDoc.getId(),
                    safe(sectionDoc.getString("academicYearId")));
        }

        // Backwards-compatible path for older Android clients. Once a school has
        // structured sections, the free-text values must match one active section.
        String grade = safe(req.getGrade());
        String section = safe(req.getSection());
        List<QueryDocumentSnapshot> configured = firestore.collection("gradeSections")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments();
        if (!configured.isEmpty()) {
            for (QueryDocumentSnapshot doc : configured) {
                if (!Boolean.FALSE.equals(doc.getBoolean("active"))
                        && grade.equalsIgnoreCase(safe(doc.getString("gradeLevel")))
                        && section.equalsIgnoreCase(safe(doc.getString("sectionName")))) {
                    return new AcademicPlacement(
                            safe(doc.getString("gradeLevel")),
                            safe(doc.getString("sectionName")),
                            doc.getId(),
                            safe(doc.getString("academicYearId")));
                }
            }
            throw new IllegalArgumentException("Choose a grade/section configured by your school admin");
        }
        return new AcademicPlacement(grade, section, "", safe(req.getAcademicYearId()));
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private record AcademicPlacement(String grade, String section, String gradeSectionId, String academicYearId) {}
}

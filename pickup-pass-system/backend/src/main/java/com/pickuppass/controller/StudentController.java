package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
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

    public StudentController(Firestore firestore) {
        this.firestore = firestore;
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
        student.put("grade", req.getGrade() != null ? req.getGrade() : "");
        student.put("section", req.getSection() != null ? req.getSection() : "");
        student.put("guardianUids", List.of());   // empty until a guardian is registered separately
        student.put("guardians", Map.of());
        student.put("createdAt", FieldValue.serverTimestamp());
        student.put("createdBy", staff.getUid());

        studentRef.set(student).get(); // await so a write failure surfaces as an error, not a false success

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
    }
}

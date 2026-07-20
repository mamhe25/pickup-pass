package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
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

        if (req.getFullName() == null || req.getFullName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "fullName is required"));
        }

        DocumentReference studentRef = firestore.collection("students").document(); // auto-ID

        Map<String, Object> student = new HashMap<>();
        student.put("schoolId", staff.getSchoolId());
        student.put("fullName", req.getFullName());
        student.put("grade", req.getGrade() != null ? req.getGrade() : "");
        student.put("section", req.getSection() != null ? req.getSection() : "");
        student.put("guardianUids", List.of());   // empty until a guardian is registered separately
        student.put("guardians", Map.of());
        student.put("createdAt", FieldValue.serverTimestamp());
        student.put("createdBy", staff.getUid());

        studentRef.set(student).get(); // await so a write failure surfaces as an error, not a false success

        return ResponseEntity.ok(Map.of(
                "studentId", studentRef.getId(),
                "fullName", req.getFullName()
        ));
    }

    public static class CreateStudentRequest {
        @NotBlank private String fullName;
        private String grade;
        private String section;

        public String getFullName() { return fullName; }
        public void setFullName(String v) { this.fullName = v; }
        public String getGrade() { return grade; }
        public void setGrade(String v) { this.grade = v; }
        public String getSection() { return section; }
        public void setSection(String v) { this.section = v; }
    }
}

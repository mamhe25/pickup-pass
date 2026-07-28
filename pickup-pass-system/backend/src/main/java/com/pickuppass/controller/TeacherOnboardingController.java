package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.GuardianProvisioningService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Registers a student's PRIMARY parent/guardian. This is the only path that
 * creates a student record's initial guardianUids entry — additional backup
 * guardians are added later by the primary parent via
 * ParentGuardianController, not by teachers, to keep an auditable chain of
 * "who authorized whom".
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherOnboardingController {

    private final Firestore firestore;
    private final GuardianProvisioningService guardianService;

    public TeacherOnboardingController(Firestore firestore, GuardianProvisioningService guardianService) {
        this.firestore = firestore;
        this.guardianService = guardianService;
    }

    @PostMapping("/register-parent")
    @PreAuthorize("hasAnyRole('teacher','school_admin')")
    public ResponseEntity<?> registerParent(
            @RequestBody RegisterParentRequest req,
            @AuthenticationPrincipal FirebaseUserDetails teacher) throws Exception {

        String schoolId = teacher.getSchoolId();

        if (req.getLastName() == null || req.getLastName().isBlank()
                || req.getFirstName() == null || req.getFirstName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "lastName and firstName are required"));
        }

        DocumentSnapshot studentDoc = firestore.collection("students")
                .document(req.getStudentId()).get().get();
        if (!studentDoc.exists() || !schoolId.equals(studentDoc.getString("schoolId"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Student not in your school"));
        }

        GuardianProvisioningService.ProvisionResult result = guardianService.provisionGuardianAccount(
                req.getParentEmail(), req.getLastName(), req.getFirstName(),
                req.getMiddleInitial(), req.getSuffix(), schoolId);

        Map<String, Object> guardianEntry = new HashMap<>();
        guardianEntry.put("relationship", req.getRelationship() != null ? req.getRelationship() : "parent/guardian");
        guardianEntry.put("isPrimary", true);
        guardianEntry.put("addedBy", teacher.getUid());
        guardianEntry.put("addedAt", FieldValue.serverTimestamp());

        // .get() here (rather than fire-and-forget) so a Firestore write
        // failure surfaces as a proper error response instead of the
        // request appearing to succeed while the guardian link never landed.
        firestore.collection("students").document(req.getStudentId()).update(
                "guardianUids", FieldValue.arrayUnion(result.getUid()),
                "guardians." + result.getUid(), guardianEntry
        ).get();

        String status = result.isNewlyCreated()
                ? (result.isEmailSent() ? "created_and_linked" : "created_and_linked_email_failed")
                : "linked_existing";

        return ResponseEntity.ok(Map.of(
                "parentUid", result.getUid(),
                "studentId", req.getStudentId(),
                "status", status,
                "emailSent", result.isEmailSent()
        ));
    }

    public static class RegisterParentRequest {
        private String parentEmail;
        @NotBlank private String lastName;
        @NotBlank private String firstName;
        private String middleInitial;
        private String suffix;
        private String studentId;
        private String relationship;

        public String getParentEmail() { return parentEmail; }
        public void setParentEmail(String v) { this.parentEmail = v; }
        public String getLastName() { return lastName; }
        public void setLastName(String v) { this.lastName = v; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { this.firstName = v; }
        public String getMiddleInitial() { return middleInitial; }
        public void setMiddleInitial(String v) { this.middleInitial = v; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String v) { this.suffix = v; }
        public String getStudentId() { return studentId; }
        public void setStudentId(String v) { this.studentId = v; }
        public String getRelationship() { return relationship; }
        public void setRelationship(String v) { this.relationship = v; }
    }
}

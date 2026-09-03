package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.GuardianProvisioningService;
import com.pickuppass.service.AuditService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers a student's PRIMARY parent/guardian. This is the only path that
 * creates a student's protected primary guardian relationship. Secondary
 * backup and one-day pickup permissions are managed separately through
 * ParentGuardianController after this primary relationship exists.
 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherOnboardingController {

    private final Firestore firestore;
    private final GuardianProvisioningService guardianService;
    private final AuditService auditService;

    public TeacherOnboardingController(Firestore firestore, GuardianProvisioningService guardianService, AuditService auditService) {
        this.firestore = firestore;
        this.guardianService = guardianService;
        this.auditService = auditService;
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

        DocumentReference studentRef = firestore.collection("students").document(req.getStudentId());
        DocumentSnapshot studentDoc = studentRef.get().get();
        if (!studentDoc.exists() || !schoolId.equals(studentDoc.getString("schoolId"))) {
            return ResponseEntity.status(403).body(Map.of("error", "Student not in your school"));
        }

        if ("teacher".equals(teacher.getRole()) && !teacherCanManageStudent(teacher, studentDoc)) {
            return ResponseEntity.status(403).body(
                    Map.of("error", "This student is not in one of your assigned sections"));
        }

        if (hasPrimaryGuardian(studentDoc)) {
            return ResponseEntity.status(409).body(
                    Map.of("error", "This student already has a primary guardian. Manage backup or temporary pickup access instead."));
        }

        GuardianProvisioningService.ProvisionResult result = guardianService.provisionGuardianAccount(
                req.getParentEmail(), req.getLastName(), req.getFirstName(),
                req.getMiddleInitial(), req.getSuffix(), schoolId);

        Map<String, Object> guardianEntry = new HashMap<>();
        guardianEntry.put("relationship", req.getRelationship() != null ? req.getRelationship() : "parent/guardian");
        guardianEntry.put("isPrimary", true);
        guardianEntry.put("addedBy", teacher.getUid());
        guardianEntry.put("addedAt", FieldValue.serverTimestamp());

        // Re-check inside a Firestore transaction so two concurrent staff
        // requests cannot both establish different primary guardians.
        Boolean linked = firestore.runTransaction(transaction -> {
            DocumentSnapshot freshStudent = transaction.get(studentRef).get();
            if (!freshStudent.exists() || !schoolId.equals(freshStudent.getString("schoolId"))) {
                return false;
            }
            if (hasPrimaryGuardian(freshStudent)) {
                return false;
            }
            transaction.update(
                    studentRef,
                    "guardianUids", FieldValue.arrayUnion(result.getUid()),
                    "guardians." + result.getUid(), guardianEntry
            );
            return true;
        }).get();

        if (!Boolean.TRUE.equals(linked)) {
            return ResponseEntity.status(409).body(
                    Map.of("error", "This student already has a primary guardian. Manage backup or temporary pickup access instead."));
        }

        String status = result.isNewlyCreated()
                ? (result.isEmailSent() ? "created_and_linked" : "created_and_linked_email_failed")
                : "linked_existing";
        auditService.record(teacher, "guardian.primary_registered", "student", req.getStudentId(), Map.of(
                "guardianUid", result.getUid(), "relationship", guardianEntry.get("relationship")));

        return ResponseEntity.ok(Map.of(
                "parentUid", result.getUid(),
                "studentId", req.getStudentId(),
                "status", status,
                "emailSent", result.isEmailSent()
        ));
    }

    @SuppressWarnings("unchecked")
    private boolean teacherCanManageStudent(
            FirebaseUserDetails teacher,
            DocumentSnapshot studentDoc) throws Exception {
        DocumentSnapshot teacherDoc = firestore.collection("users")
                .document(teacher.getUid()).get().get();
        if (!teacherDoc.exists()
                || !teacher.getSchoolId().equals(teacherDoc.getString("schoolId"))
                || !"teacher".equals(teacherDoc.getString("role"))) {
            return false;
        }

        List<Map<String, Object>> assignedSections =
                (List<Map<String, Object>>) teacherDoc.get("assignedSections");
        if (assignedSections == null || assignedSections.isEmpty()) return false;

        String studentGrade = safe(studentDoc.getString("grade"));
        String studentSection = safe(studentDoc.getString("section"));
        for (Map<String, Object> assigned : assignedSections) {
            String grade = safeValue(assigned.get("grade"));
            String section = safeValue(assigned.get("section"));
            if (studentGrade.equalsIgnoreCase(grade)
                    && studentSection.equalsIgnoreCase(section)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean hasPrimaryGuardian(DocumentSnapshot studentDoc) {
        Map<String, Object> guardians = (Map<String, Object>) studentDoc.get("guardians");
        if (guardians == null || guardians.isEmpty()) return false;
        for (Object value : guardians.values()) {
            if (!(value instanceof Map<?, ?> entry)) continue;
            if (Boolean.TRUE.equals(entry.get("isPrimary"))) return true;
        }
        return false;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
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

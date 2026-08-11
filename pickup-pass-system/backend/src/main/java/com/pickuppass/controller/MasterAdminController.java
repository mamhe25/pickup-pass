package com.pickuppass.controller;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.pickuppass.service.StaffProvisioningService;
import com.pickuppass.service.AuditService;
import com.pickuppass.security.FirebaseUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/master-admin")
public class MasterAdminController {

    private static final Set<String> ASSIGNABLE_STAFF_ROLES = Set.of("teacher", "school_admin");

    private final Firestore firestore;
    private final StaffProvisioningService staffProvisioningService;
    private final FirebaseAuth firebaseAuth;
    private final AuditService auditService;

    public MasterAdminController(Firestore firestore, StaffProvisioningService staffProvisioningService,
                                 FirebaseAuth firebaseAuth, AuditService auditService) {
        this.firestore = firestore;
        this.staffProvisioningService = staffProvisioningService;
        this.firebaseAuth = firebaseAuth;
        this.auditService = auditService;
    }

    /** Creates a new tenant. Returns the auto-generated schoolId to use in every other endpoint. */
    @PostMapping("/schools")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> createSchool(@RequestBody CreateSchoolRequest req,
                                           @AuthenticationPrincipal FirebaseUserDetails masterAdmin) throws ExecutionException, InterruptedException {
        if (req.getSchoolName() == null || req.getSchoolName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "schoolName is required"));
        }

        DocumentReference schoolRef = firestore.collection("schools").document(); // auto-ID

        Map<String, Object> school = new HashMap<>();
        school.put("schoolName", req.getSchoolName());
        school.put("status", "active");
        school.put("createdAt", FieldValue.serverTimestamp());
        schoolRef.set(school).get(); // await so a write failure surfaces as an error, not a false success
        auditService.record(masterAdmin, "school.created", "school", schoolRef.getId(), Map.of("schoolName", req.getSchoolName()));

        return ResponseEntity.ok(Map.of("schoolId", schoolRef.getId(), "schoolName", req.getSchoolName()));
    }

    @PostMapping("/schools/{schoolId}/status")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> setSchoolStatus(
            @PathVariable String schoolId,
            @RequestBody SchoolStatusRequest req,
            @AuthenticationPrincipal FirebaseUserDetails masterAdmin) throws ExecutionException, InterruptedException {

        if (!req.getStatus().equals("active") && !req.getStatus().equals("suspended")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status"));
        }

        DocumentReference schoolRef = firestore.collection("schools").document(schoolId);
        ApiFuture<DocumentSnapshot> future = schoolRef.get();
        if (!future.get().exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        }

        Map<String, Object> update = new HashMap<>();
        update.put("status", req.getStatus());
        update.put("statusUpdatedAt", FieldValue.serverTimestamp());
        schoolRef.update(update).get();

        ApiFuture<QuerySnapshot> users = firestore.collection("users")
                .whereEqualTo("schoolId", schoolId).get();
        for (DocumentSnapshot doc : users.get().getDocuments()) {
            String uid = doc.getId();
            if (req.getStatus().equals("suspended")) {
                // Remember that this specific disable came from tenant suspension so a
                // later reactivation does not accidentally re-enable a teacher that an
                // admin had independently deactivated before the suspension.
                if (!Boolean.FALSE.equals(doc.getBoolean("isActive"))) {
                    doc.getReference().update(
                            "isActive", false,
                            "suspendedBySchool", true,
                            "statusUpdatedAt", FieldValue.serverTimestamp()).get();
                    try {
                        firebaseAuth.updateUser(new UserRecord.UpdateRequest(uid).setDisabled(true));
                        firebaseAuth.revokeRefreshTokens(uid);
                    } catch (Exception ignored) { }
                }
            } else if (Boolean.TRUE.equals(doc.getBoolean("suspendedBySchool"))) {
                doc.getReference().update(
                        "isActive", true,
                        "suspendedBySchool", false,
                        "statusUpdatedAt", FieldValue.serverTimestamp()).get();
                try {
                    firebaseAuth.updateUser(new UserRecord.UpdateRequest(uid).setDisabled(false));
                } catch (Exception ignored) { }
            }
        }
        auditService.record(masterAdmin, "school.status_changed", "school", schoolId, Map.of("status", req.getStatus()));

        return ResponseEntity.ok(Map.of("schoolId", schoolId, "status", req.getStatus()));
    }

    /**
     * Creates a teacher or school_admin account for any school — the initial
     * admin for a newly onboarded school. No try/catch here on purpose:
     * StaffProvisioningService throws ConflictException for a duplicate
     * email and FirebaseAuthException for other Auth-service failures, both
     * of which GlobalExceptionHandler now translates into the right status
     * code and a friendly message. Catching Exception here and always
     * returning 400 (the previous behavior) would mask that distinction and
     * swallow the stack trace before it ever reached the logger.
     */
    @PostMapping("/schools/{schoolId}/staff")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> createStaff(
            @PathVariable String schoolId,
            @RequestBody CreateStaffRequest req,
            @AuthenticationPrincipal FirebaseUserDetails masterAdmin) throws Exception {

        if (!ASSIGNABLE_STAFF_ROLES.contains(req.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("error", "role must be 'teacher' or 'school_admin'"));
        }
        if (!firestore.collection("schools").document(schoolId).get().get().exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        }
        if (req.getLastName() == null || req.getLastName().isBlank()
                || req.getFirstName() == null || req.getFirstName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "lastName and firstName are required"));
        }

        StaffProvisioningService.StaffCreationResult result = staffProvisioningService.createStaffAccount(
                req.getEmail(), req.getLastName(), req.getFirstName(),
                req.getMiddleInitial(), req.getSuffix(), req.getRole(), schoolId);

        auditService.record(masterAdmin, "staff.created", "user", result.getUid(), Map.of(
                "schoolId", schoolId, "role", req.getRole(), "email", req.getEmail()));
        return ResponseEntity.ok(Map.of(
                "uid", result.getUid(),
                "role", req.getRole(),
                "schoolId", schoolId,
                "emailSent", result.isEmailSent()
        ));
    }

    public static class CreateSchoolRequest {
        @NotBlank private String schoolName;
        public String getSchoolName() { return schoolName; }
        public void setSchoolName(String schoolName) { this.schoolName = schoolName; }
    }

    public static class SchoolStatusRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class CreateStaffRequest {
        @NotBlank private String email;
        @NotBlank private String lastName;
        @NotBlank private String firstName;
        private String middleInitial;
        private String suffix;
        @NotBlank private String role;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getLastName() { return lastName; }
        public void setLastName(String v) { this.lastName = v; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { this.firstName = v; }
        public String getMiddleInitial() { return middleInitial; }
        public void setMiddleInitial(String v) { this.middleInitial = v; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String v) { this.suffix = v; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}

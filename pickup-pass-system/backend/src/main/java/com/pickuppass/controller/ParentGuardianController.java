package com.pickuppass.controller;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pickuppass.exception.ForbiddenException;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.GuardianProvisioningService;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.GuardianAuthorizationService;
import java.time.LocalDate;
import java.time.ZoneId;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets an already-authorized parent/guardian add or remove OTHER authorized
 * pickup contacts for their child (e.g. a spouse, grandparent, or nanny who
 * may need to pick up when the primary parent can't).
 *
 * Authorization model:
 *  - Only someone already listed in a student's guardianUids can add/remove
 *    guardians for that student — a stranger can't add themselves.
 *  - The PRIMARY guardian (set at teacher-registration time) can't be
 *    removed through this endpoint, to avoid a student ending up with no
 *    accountable guardian of record; only school staff can change that.
 *  - Removing a guardian immediately invalidates any live, unused QR pass
 *    they're currently holding, so revocation takes effect right away
 *    rather than waiting for the token to expire on its own.
 */
@RestController
@RequestMapping("/api/parent")
public class ParentGuardianController {

    private final Firestore firestore;
    private final GuardianProvisioningService guardianService;
    private final int maxGuardiansPerStudent;
    private final AuditService auditService;
    private final ZoneId schoolTimeZone;
    private final GuardianAuthorizationService guardianAuthorizationService;

    public ParentGuardianController(
            Firestore firestore,
            GuardianProvisioningService guardianService,
            AuditService auditService,
            GuardianAuthorizationService guardianAuthorizationService,
            @Value("${app.max-guardians-per-student:4}") int maxGuardiansPerStudent,
            @Value("${app.school-time-zone:Asia/Manila}") String schoolTimeZone) {
        this.firestore = firestore;
        this.guardianService = guardianService;
        this.auditService = auditService;
        this.guardianAuthorizationService = guardianAuthorizationService;
        this.maxGuardiansPerStudent = maxGuardiansPerStudent;
        this.schoolTimeZone = ZoneId.of(schoolTimeZone);
    }

    @PostMapping("/add-guardian")
    @PreAuthorize("hasAnyRole('parent', 'teacher', 'school_admin')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> addGuardian(
            @RequestBody AddGuardianRequest req,
            @AuthenticationPrincipal FirebaseUserDetails parent) throws Exception {

        String schoolId = parent.getSchoolId();
        DocumentReference studentRef = firestore.collection("students").document(req.getStudentId());
        DocumentSnapshot studentSnap = studentRef.get().get();

        if (!studentSnap.exists() || !schoolId.equals(studentSnap.getString("schoolId"))) {
            throw new NotFoundException("Student not found");
        }

        List<String> guardianUids = (List<String>) studentSnap.get("guardianUids");
        if (guardianUids == null) guardianUids = new java.util.ArrayList<>();
        boolean isSchoolStaff = "teacher".equals(parent.getRole()) || "school_admin".equals(parent.getRole());
        if (!isSchoolStaff) {
            GuardianAuthorizationService.AuthorizationDecision decision = guardianAuthorizationService.check(studentSnap, parent.getUid());
            if (!decision.allowed()) throw new ForbiddenException(decision.reason());
            if (decision.temporary()) throw new ForbiddenException("Temporary guardians cannot manage other guardians");
        }

        if (guardianUids.size() >= maxGuardiansPerStudent) {
            return ResponseEntity.status(400).body(
                    Map.of("error", "Maximum of " + maxGuardiansPerStudent + " authorized guardians reached for this student"));
        }

        if (req.getLastName() == null || req.getLastName().isBlank()
                || req.getFirstName() == null || req.getFirstName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "lastName and firstName are required"));
        }

        GuardianProvisioningService.ProvisionResult result = guardianService.provisionGuardianAccount(
                req.getGuardianEmail(), req.getLastName(), req.getFirstName(),
                req.getMiddleInitial(), req.getSuffix(), schoolId);

        if (guardianUids.contains(result.getUid())) {
            return ResponseEntity.status(400).body(Map.of("error", "This person is already an authorized guardian"));
        }

        Map<String, Object> guardianEntry = new HashMap<>();
        guardianEntry.put("relationship", req.getRelationship() != null ? req.getRelationship() : "authorized pickup");
        guardianEntry.put("isPrimary", false);
        guardianEntry.put("addedBy", parent.getUid());
        guardianEntry.put("addedAt", FieldValue.serverTimestamp());

        studentRef.update(
                "guardianUids", FieldValue.arrayUnion(result.getUid()),
                "guardians." + result.getUid(), guardianEntry
        ).get();
        auditService.record(parent, "guardian.added", "student", req.getStudentId(), Map.of(
                "guardianUid", result.getUid(), "relationship", guardianEntry.get("relationship")));

        return ResponseEntity.ok(Map.of(
                "guardianUid", result.getUid(),
                "status", result.isNewlyCreated() ? "created_and_linked" : "linked_existing",
                "emailSent", result.isEmailSent()
        ));
    }


    @PostMapping("/add-temporary-guardian")
    @PreAuthorize("hasAnyRole('parent', 'teacher', 'school_admin')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> addTemporaryGuardian(
            @RequestBody AddTemporaryGuardianRequest req,
            @AuthenticationPrincipal FirebaseUserDetails parent) throws Exception {

        String schoolId = parent.getSchoolId();
        DocumentReference studentRef = firestore.collection("students").document(req.getStudentId());
        DocumentSnapshot studentSnap = studentRef.get().get();
        if (!studentSnap.exists() || !schoolId.equals(studentSnap.getString("schoolId"))) {
            throw new NotFoundException("Student not found");
        }

        List<String> guardianUids = (List<String>) studentSnap.get("guardianUids");
        if (guardianUids == null) guardianUids = new java.util.ArrayList<>();
        boolean isSchoolStaff = "teacher".equals(parent.getRole()) || "school_admin".equals(parent.getRole());
        if (!isSchoolStaff) {
            GuardianAuthorizationService.AuthorizationDecision decision = guardianAuthorizationService.check(studentSnap, parent.getUid());
            if (!decision.allowed()) throw new ForbiddenException(decision.reason());
            if (decision.temporary()) throw new ForbiddenException("Temporary guardians cannot authorize other guardians");
        }

        if (req.getLastName() == null || req.getLastName().isBlank()
                || req.getFirstName() == null || req.getFirstName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "lastName and firstName are required"));
        }

        LocalDate today = LocalDate.now(schoolTimeZone);
        LocalDate validDate;
        try {
            validDate = LocalDate.parse(req.getValidDate());
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "validDate must use YYYY-MM-DD"));
        }
        if (validDate.isBefore(today)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Temporary pickup date cannot be in the past"));
        }
        if (validDate.isAfter(today.plusDays(30))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Temporary pickup can be authorized up to 30 days in advance"));
        }

        GuardianProvisioningService.ProvisionResult result = guardianService.provisionGuardianAccount(
                req.getGuardianEmail(), req.getLastName(), req.getFirstName(),
                req.getMiddleInitial(), req.getSuffix(), schoolId);

        Map<String, Object> guardians = (Map<String, Object>) studentSnap.get("guardians");
        Map<String, Object> existing = guardians == null ? null : (Map<String, Object>) guardians.get(result.getUid());
        if (existing != null && !"temporary".equalsIgnoreCase(String.valueOf(existing.getOrDefault("authorizationType", "permanent")))) {
            return ResponseEntity.status(400).body(Map.of("error", "This person is already a permanent authorized guardian"));
        }
        if (!guardianUids.contains(result.getUid()) && guardianUids.size() >= maxGuardiansPerStudent) {
            return ResponseEntity.status(400).body(Map.of("error", "Maximum of " + maxGuardiansPerStudent + " authorized guardians reached for this student"));
        }

        Map<String, Object> guardianEntry = new HashMap<>();
        guardianEntry.put("relationship", req.getRelationship() != null ? req.getRelationship() : "temporary pickup");
        guardianEntry.put("isPrimary", false);
        guardianEntry.put("authorizationType", "temporary");
        guardianEntry.put("validDate", validDate.toString());
        guardianEntry.put("remainingUses", 1);
        guardianEntry.put("addedBy", parent.getUid());
        guardianEntry.put("addedAt", FieldValue.serverTimestamp());

        studentRef.update(
                "guardianUids", FieldValue.arrayUnion(result.getUid()),
                "guardians." + result.getUid(), guardianEntry
        ).get();

        // Re-authorizing a temporary guardian supersedes any stale QR they might already hold.
        ApiFuture<QuerySnapshot> liveTokens = firestore.collection("pickupTokens")
                .whereEqualTo("studentId", req.getStudentId())
                .whereEqualTo("parentUid", result.getUid())
                .whereEqualTo("used", false)
                .get();
        for (DocumentSnapshot tokenDoc : liveTokens.get().getDocuments()) {
            tokenDoc.getReference().update("used", true, "invalidatedReason", "temporary_authorization_reissued").get();
        }

        auditService.record(parent, "guardian.temporary_authorized", "student", req.getStudentId(), Map.of(
                "guardianUid", result.getUid(),
                "relationship", guardianEntry.get("relationship"),
                "validDate", validDate.toString(),
                "remainingUses", 1));

        return ResponseEntity.ok(Map.of(
                "guardianUid", result.getUid(),
                "status", result.isNewlyCreated() ? "created_and_linked" : "linked_existing",
                "authorizationType", "temporary",
                "validDate", validDate.toString(),
                "remainingUses", 1,
                "emailSent", result.isEmailSent()
        ));
    }

    @PostMapping("/remove-guardian")
    @PreAuthorize("hasAnyRole('parent', 'teacher', 'school_admin')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> removeGuardian(
            @RequestBody RemoveGuardianRequest req,
            @AuthenticationPrincipal FirebaseUserDetails parent) throws Exception {

        String schoolId = parent.getSchoolId();
        DocumentReference studentRef = firestore.collection("students").document(req.getStudentId());
        DocumentSnapshot studentSnap = studentRef.get().get();

        if (!studentSnap.exists() || !schoolId.equals(studentSnap.getString("schoolId"))) {
            throw new NotFoundException("Student not found");
        }

        List<String> guardianUids = (List<String>) studentSnap.get("guardianUids");
        boolean isSchoolStaff = "teacher".equals(parent.getRole()) || "school_admin".equals(parent.getRole());
        if (!isSchoolStaff) {
            GuardianAuthorizationService.AuthorizationDecision decision = guardianAuthorizationService.check(studentSnap, parent.getUid());
            if (!decision.allowed()) throw new ForbiddenException(decision.reason());
            if (decision.temporary()) throw new ForbiddenException("Temporary guardians cannot manage other guardians");
        }

        Map<String, Object> guardians = (Map<String, Object>) studentSnap.get("guardians");
        Map<String, Object> target = guardians != null
                ? (Map<String, Object>) guardians.get(req.getGuardianUid()) : null;

        if (target == null) {
            throw new NotFoundException("That guardian is not linked to this student");
        }
        if (Boolean.TRUE.equals(target.get("isPrimary"))) {
            return ResponseEntity.status(400).body(
                    Map.of("error", "The primary guardian can't be removed here — contact the school office"));
        }

        studentRef.update(
                "guardianUids", FieldValue.arrayRemove(req.getGuardianUid()),
                "guardians." + req.getGuardianUid(), FieldValue.delete()
        ).get();

        // Immediately kill any still-unused QR pass the removed guardian is holding.
        ApiFuture<QuerySnapshot> liveTokens = firestore.collection("pickupTokens")
                .whereEqualTo("studentId", req.getStudentId())
                .whereEqualTo("parentUid", req.getGuardianUid())
                .whereEqualTo("used", false)
                .get();
        for (DocumentSnapshot tokenDoc : liveTokens.get().getDocuments()) {
            tokenDoc.getReference().update("used", true, "invalidatedReason", "guardian_removed").get();
        }
        auditService.record(parent, "guardian.removed", "student", req.getStudentId(), Map.of("guardianUid", req.getGuardianUid()));

        return ResponseEntity.ok(Map.of("status", "removed"));
    }

    public static class AddGuardianRequest {
        @NotBlank private String studentId;
        @NotBlank private String guardianEmail;
        @NotBlank private String lastName;
        @NotBlank private String firstName;
        private String middleInitial;
        private String suffix;
        private String relationship;

        public String getStudentId() { return studentId; }
        public void setStudentId(String v) { this.studentId = v; }
        public String getGuardianEmail() { return guardianEmail; }
        public void setGuardianEmail(String v) { this.guardianEmail = v; }
        public String getLastName() { return lastName; }
        public void setLastName(String v) { this.lastName = v; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { this.firstName = v; }
        public String getMiddleInitial() { return middleInitial; }
        public void setMiddleInitial(String v) { this.middleInitial = v; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String v) { this.suffix = v; }
        public String getRelationship() { return relationship; }
        public void setRelationship(String v) { this.relationship = v; }
    }


    public static class AddTemporaryGuardianRequest {
        @NotBlank private String studentId;
        @NotBlank private String guardianEmail;
        @NotBlank private String lastName;
        @NotBlank private String firstName;
        @NotBlank private String validDate;
        private String middleInitial;
        private String suffix;
        private String relationship;

        public String getStudentId() { return studentId; }
        public void setStudentId(String v) { this.studentId = v; }
        public String getGuardianEmail() { return guardianEmail; }
        public void setGuardianEmail(String v) { this.guardianEmail = v; }
        public String getLastName() { return lastName; }
        public void setLastName(String v) { this.lastName = v; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { this.firstName = v; }
        public String getValidDate() { return validDate; }
        public void setValidDate(String v) { this.validDate = v; }
        public String getMiddleInitial() { return middleInitial; }
        public void setMiddleInitial(String v) { this.middleInitial = v; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String v) { this.suffix = v; }
        public String getRelationship() { return relationship; }
        public void setRelationship(String v) { this.relationship = v; }
    }

    public static class RemoveGuardianRequest {
        @NotBlank private String studentId;
        @NotBlank private String guardianUid;

        public String getStudentId() { return studentId; }
        public void setStudentId(String v) { this.studentId = v; }
        public String getGuardianUid() { return guardianUid; }
        public void setGuardianUid(String v) { this.guardianUid = v; }
    }
}

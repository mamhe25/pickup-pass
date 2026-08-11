package com.pickuppass.controller;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.pickuppass.exception.ForbiddenException;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.GuardianProvisioningService;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.SubscriptionFeatureService;
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
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final ZoneId schoolTimeZone;
    private final GuardianAuthorizationService guardianAuthorizationService;

    public ParentGuardianController(
            Firestore firestore,
            GuardianProvisioningService guardianService,
            AuditService auditService,
            GuardianAuthorizationService guardianAuthorizationService,
            SubscriptionFeatureService subscriptionFeatureService,
            @Value("${app.max-guardians-per-student:4}") int maxGuardiansPerStudent,
            @Value("${app.school-time-zone:Asia/Manila}") String schoolTimeZone) {
        this.firestore = firestore;
        this.guardianService = guardianService;
        this.auditService = auditService;
        this.guardianAuthorizationService = guardianAuthorizationService;
        this.subscriptionFeatureService = subscriptionFeatureService;
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

        initializeGuardianVerification(result.getUid(), schoolId, isSchoolStaff);

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

        subscriptionFeatureService.requireFeature(parent.getSchoolId(), "temporary_guardians");
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

        initializeGuardianVerification(result.getUid(), schoolId, isSchoolStaff);

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

    @PutMapping("/guardian-schedule")
    @PreAuthorize("hasAnyRole('parent', 'teacher', 'school_admin')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> updateGuardianSchedule(
            @RequestBody GuardianScheduleRequest req,
            @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {

        subscriptionFeatureService.requireFeature(actor.getSchoolId(), "guardian_pickup_schedules");
        String schoolId = actor.getSchoolId();
        DocumentReference studentRef = firestore.collection("students").document(req.getStudentId());
        DocumentSnapshot studentSnap = studentRef.get().get();
        if (!studentSnap.exists() || !schoolId.equals(studentSnap.getString("schoolId"))) {
            throw new NotFoundException("Student not found");
        }

        boolean isSchoolStaff = "teacher".equals(actor.getRole()) || "school_admin".equals(actor.getRole());
        if (!isSchoolStaff) {
            GuardianAuthorizationService.AuthorizationDecision actorDecision = guardianAuthorizationService.check(studentSnap, actor.getUid());
            if (!actorDecision.allowed()) throw new ForbiddenException(actorDecision.reason());
            if (actorDecision.temporary()) throw new ForbiddenException("Temporary guardians cannot manage pickup schedules");
            if (actor.getUid().equals(req.getGuardianUid())) {
                throw new ForbiddenException("A guardian cannot restrict their own pickup schedule. Contact the school office if this is needed.");
            }
        }

        Map<String, Object> guardians = (Map<String, Object>) studentSnap.get("guardians");
        Map<String, Object> target = guardians == null ? null : (Map<String, Object>) guardians.get(req.getGuardianUid());
        if (target == null) throw new NotFoundException("That guardian is not linked to this student");
        if ("temporary".equalsIgnoreCase(String.valueOf(target.getOrDefault("authorizationType", "permanent")))) {
            return ResponseEntity.badRequest().body(Map.of("error", "One-day guardians already have a date-limited authorization"));
        }

        List<String> normalizedDays = new java.util.ArrayList<>();
        if (req.isEnabled()) {
            if (req.getPickupDays() == null || req.getPickupDays().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Select at least one pickup day"));
            }
            for (String value : req.getPickupDays()) {
                try {
                    String normalized = java.time.DayOfWeek.valueOf(value.trim().toUpperCase()).name();
                    if (!normalizedDays.contains(normalized)) normalizedDays.add(normalized);
                } catch (RuntimeException ex) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Invalid pickup day: " + value));
                }
            }
        }

        String startDate = req.getStartDate() == null ? "" : req.getStartDate().trim();
        String endDate = req.getEndDate() == null ? "" : req.getEndDate().trim();
        try {
            LocalDate start = startDate.isBlank() ? null : LocalDate.parse(startDate);
            LocalDate end = endDate.isBlank() ? null : LocalDate.parse(endDate);
            if (start != null && end != null && end.isBefore(start)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Schedule end date cannot be before start date"));
            }
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Schedule dates must use YYYY-MM-DD"));
        }

        Map<String, Object> updates = new HashMap<>();
        String base = "guardians." + req.getGuardianUid() + ".";
        updates.put(base + "pickupScheduleEnabled", req.isEnabled());
        updates.put(base + "pickupDays", normalizedDays);
        updates.put(base + "scheduleStartDate", startDate);
        updates.put(base + "scheduleEndDate", endDate);
        updates.put(base + "scheduleUpdatedBy", actor.getUid());
        updates.put(base + "scheduleUpdatedAt", FieldValue.serverTimestamp());
        studentRef.update(updates).get();

        // Existing passes are revoked so every subsequent QR is issued under the new schedule.
        int invalidated = 0;
        ApiFuture<QuerySnapshot> liveTokens = firestore.collection("pickupTokens")
                .whereEqualTo("studentId", req.getStudentId())
                .whereEqualTo("parentUid", req.getGuardianUid())
                .whereEqualTo("used", false)
                .get();
        for (DocumentSnapshot tokenDoc : liveTokens.get().getDocuments()) {
            tokenDoc.getReference().update("used", true, "invalidatedReason", "guardian_schedule_changed").get();
            invalidated++;
        }

        auditService.record(actor, "guardian.pickup_schedule_updated", "student", req.getStudentId(), Map.of(
                "guardianUid", req.getGuardianUid(),
                "enabled", req.isEnabled(),
                "pickupDays", normalizedDays,
                "startDate", startDate,
                "endDate", endDate,
                "invalidatedQrPasses", invalidated));

        Map<String, Object> response = new HashMap<>();
        response.put("status", "updated");
        response.put("enabled", req.isEnabled());
        response.put("pickupDays", normalizedDays);
        response.put("startDate", startDate);
        response.put("endDate", endDate);
        response.put("invalidatedQrPasses", invalidated);
        return ResponseEntity.ok(response);
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


    private void initializeGuardianVerification(String guardianUid, String schoolId, boolean addedBySchoolStaff) throws Exception {
        DocumentReference userRef = firestore.collection("users").document(guardianUid);
        DocumentSnapshot user = userRef.get().get();
        if (!user.exists()) return;
        String existing = user.getString("guardianVerificationStatus");
        if (existing != null && !existing.isBlank()) return;

        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        boolean verificationRequired = school.exists() && Boolean.TRUE.equals(school.getBoolean("guardianVerificationRequired"));
        String status = addedBySchoolStaff || !verificationRequired ? "verified" : "pending";
        Map<String, Object> updates = new HashMap<>();
        updates.put("guardianVerificationStatus", status);
        updates.put("guardianVerificationUpdatedAt", FieldValue.serverTimestamp());
        if ("verified".equals(status)) {
            updates.put("guardianVerifiedAt", FieldValue.serverTimestamp());
        }
        userRef.update(updates).get();
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


    public static class GuardianScheduleRequest {
        @NotBlank private String studentId;
        @NotBlank private String guardianUid;
        private boolean enabled;
        private List<String> pickupDays;
        private String startDate;
        private String endDate;

        public String getStudentId() { return studentId; }
        public void setStudentId(String v) { this.studentId = v; }
        public String getGuardianUid() { return guardianUid; }
        public void setGuardianUid(String v) { this.guardianUid = v; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean v) { this.enabled = v; }
        public List<String> getPickupDays() { return pickupDays; }
        public void setPickupDays(List<String> v) { this.pickupDays = v; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String v) { this.startDate = v; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String v) { this.endDate = v; }
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

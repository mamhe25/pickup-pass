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
import com.pickuppass.service.SubscriptionFeatureService;
import com.pickuppass.service.TenantUsageService;
import com.pickuppass.service.SubscriptionLifecycleService;
import com.pickuppass.service.SaasOperationsHealthService;
import com.pickuppass.service.TenantDataExportService;
import com.pickuppass.security.FirebaseUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/master-admin")
public class MasterAdminController {

    private static final Set<String> ASSIGNABLE_STAFF_ROLES = Set.of("teacher", "school_admin");

    private final Firestore firestore;
    private final StaffProvisioningService staffProvisioningService;
    private final FirebaseAuth firebaseAuth;
    private final AuditService auditService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final TenantUsageService tenantUsageService;
    private final SubscriptionLifecycleService subscriptionLifecycleService;
    private final SaasOperationsHealthService operationsHealthService;
    private final TenantDataExportService tenantDataExportService;

    public MasterAdminController(Firestore firestore, StaffProvisioningService staffProvisioningService,
                                 FirebaseAuth firebaseAuth, AuditService auditService,
                                 SubscriptionFeatureService subscriptionFeatureService, TenantUsageService tenantUsageService,
                                 SubscriptionLifecycleService subscriptionLifecycleService,
                                 SaasOperationsHealthService operationsHealthService,
                                 TenantDataExportService tenantDataExportService) {
        this.firestore = firestore;
        this.staffProvisioningService = staffProvisioningService;
        this.firebaseAuth = firebaseAuth;
        this.auditService = auditService;
        this.subscriptionFeatureService = subscriptionFeatureService;
        this.tenantUsageService = tenantUsageService;
        this.subscriptionLifecycleService = subscriptionLifecycleService;
        this.operationsHealthService = operationsHealthService;
        this.tenantDataExportService = tenantDataExportService;
    }


    /**
     * Lists SaaS tenants for the master-admin console. This intentionally
     * returns tenant metadata only; operational student/pickup analytics stay
     * in school-scoped endpoints so opening this screen does not scan every
     * student or exit log in the platform.
     */
    @GetMapping("/schools")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> listSchools() throws ExecutionException, InterruptedException {
        QuerySnapshot snapshot = firestore.collection("schools").get().get();
        List<Map<String, Object>> schools = new ArrayList<>();
        int active = 0;
        int suspended = 0;

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            String status = doc.getString("status");
            if (status == null || status.isBlank()) status = "active";
            if ("suspended".equals(status)) suspended++; else active++;

            Map<String, Object> item = new HashMap<>();
            item.put("schoolId", doc.getId());
            item.put("schoolName", doc.getString("schoolName") == null ? "Unnamed school" : doc.getString("schoolName"));
            item.put("status", status);
            item.put("createdAt", doc.getTimestamp("createdAt") == null ? null : doc.getTimestamp("createdAt").toDate().toInstant().toString());
            item.put("statusUpdatedAt", doc.getTimestamp("statusUpdatedAt") == null ? null : doc.getTimestamp("statusUpdatedAt").toDate().toInstant().toString());
            item.putAll(subscriptionFeatureService.effectiveEntitlements(doc));
            Object rawOverrides = doc.get("featureOverrides");
            item.put("featureOverrides", rawOverrides instanceof Map<?, ?> ? rawOverrides : Map.of());
            item.put("usage", tenantUsageService.snapshot(doc.getId()));
            item.put("selfServiceDataExportEnabled", Boolean.TRUE.equals(doc.getBoolean("selfServiceDataExportEnabled")));
            schools.add(item);
        }

        schools.sort(Comparator.comparing(v -> String.valueOf(v.get("schoolName")), String.CASE_INSENSITIVE_ORDER));
        return ResponseEntity.ok(Map.of(
                "totalSchools", schools.size(),
                "activeSchools", active,
                "suspendedSchools", suspended,
                "schools", schools
        ));
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
        Instant subscriptionNow = Instant.now();
        Instant trialEnd = subscriptionNow.plus(30, ChronoUnit.DAYS);
        school.put("plan", SubscriptionFeatureService.TRIAL);
        school.put("subscriptionStatus", "trialing");
        school.put("trialEndsAt", Date.from(trialEnd));
        school.put("currentPeriodStart", Date.from(subscriptionNow));
        school.put("currentPeriodEnd", Date.from(trialEnd));
        school.put("autoRenew", true);
        school.put("cancelAtPeriodEnd", false);
        school.put("featureOverrides", Map.of());
        // Privacy/cost-safe default: a tenant admin cannot create a full data export
        // until the platform owner intentionally enables self-service export.
        school.put("selfServiceDataExportEnabled", false);
        school.put("createdAt", FieldValue.serverTimestamp());
        schoolRef.set(school).get(); // await so a write failure surfaces as an error, not a false success
        tenantUsageService.initializeNewTenant(schoolRef.getId());
        auditService.record(masterAdmin, "school.created", "school", schoolRef.getId(), Map.of("schoolName", req.getSchoolName()));

        return ResponseEntity.ok(Map.of(
                "schoolId", schoolRef.getId(),
                "schoolName", req.getSchoolName(),
                "plan", SubscriptionFeatureService.TRIAL,
                "subscriptionStatus", "trialing"
        ));
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
        operationsHealthService.refreshSchool(schoolId);

        return ResponseEntity.ok(Map.of("schoolId", schoolId, "status", req.getStatus()));
    }

    @PutMapping("/schools/{schoolId}/data-export-access")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> setSchoolDataExportAccess(
            @PathVariable String schoolId,
            @RequestBody DataExportAccessRequest req,
            @AuthenticationPrincipal FirebaseUserDetails masterAdmin) throws Exception {
        DocumentReference ref = firestore.collection("schools").document(schoolId);
        DocumentSnapshot school = ref.get().get();
        if (!school.exists()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "School not found"));
        ref.update(
                "selfServiceDataExportEnabled", req.isEnabled(),
                "dataExportAccessUpdatedAt", FieldValue.serverTimestamp(),
                "dataExportAccessUpdatedBy", masterAdmin.getUid()).get();
        auditService.record(masterAdmin, "school.data_export_access_changed", "school", schoolId,
                Map.of("enabled", req.isEnabled()));
        return ResponseEntity.ok(Map.of("schoolId", schoolId, "enabled", req.isEnabled()));
    }

    @GetMapping("/schools/{schoolId}/data-export")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> exportSchoolData(
            @PathVariable String schoolId,
            @AuthenticationPrincipal FirebaseUserDetails masterAdmin) throws Exception {
        TenantDataExportService.ExportResult result = tenantDataExportService.exportSchool(schoolId, masterAdmin);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(result.fileName(), StandardCharsets.UTF_8).build());
        headers.setContentLength(result.bytes().length);
        headers.set("Cache-Control", "no-store");
        return new ResponseEntity<>(result.bytes(), headers, HttpStatus.OK);
    }

    @GetMapping("/plans")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> planCatalog() {
        return ResponseEntity.ok(Map.of(
                "plans", subscriptionFeatureService.getCatalog(),
                "featureKeys", SubscriptionFeatureService.FEATURES
        ));
    }

    @PutMapping("/schools/{schoolId}/subscription")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> updateSubscription(
            @PathVariable String schoolId,
            @RequestBody SubscriptionUpdateRequest req,
            @AuthenticationPrincipal FirebaseUserDetails masterAdmin) throws Exception {

        DocumentReference ref = firestore.collection("schools").document(schoolId);
        DocumentSnapshot school = ref.get().get();
        if (!school.exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        }

        String plan = subscriptionFeatureService.normalizePlan(req.getPlan());
        if (req.getPlan() == null || !SubscriptionFeatureService.PLANS.contains(req.getPlan().trim().toLowerCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown plan"));
        }

        String subscriptionStatus = req.getSubscriptionStatus() == null || req.getSubscriptionStatus().isBlank()
                ? (SubscriptionFeatureService.TRIAL.equals(plan) ? "trialing" : "active")
                : req.getSubscriptionStatus().trim().toLowerCase();
        if (!Set.of("trialing", "active", "past_due", "cancelled").contains(subscriptionStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid subscriptionStatus"));
        }

        Map<String, Boolean> overrides = new HashMap<>();
        if (req.getFeatureOverrides() != null) {
            for (Map.Entry<String, Boolean> entry : req.getFeatureOverrides().entrySet()) {
                if (!SubscriptionFeatureService.FEATURES.contains(entry.getKey())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Unknown feature: " + entry.getKey()));
                }
                if (entry.getValue() != null) overrides.put(entry.getKey(), entry.getValue());
            }
        }

        Instant now = Instant.now();
        Map<String, Object> update = new HashMap<>();
        update.put("plan", plan);
        update.put("subscriptionStatus", subscriptionStatus);
        update.put("featureOverrides", overrides);
        update.put("autoRenew", req.getAutoRenew() == null ? !Boolean.FALSE.equals(school.getBoolean("autoRenew")) : req.getAutoRenew());
        update.put("cancelAtPeriodEnd", Boolean.TRUE.equals(req.getCancelAtPeriodEnd()));
        update.put("subscriptionUpdatedAt", FieldValue.serverTimestamp());

        if (req.getTrialEndsAt() != null && !req.getTrialEndsAt().isBlank()) {
            try {
                update.put("trialEndsAt", Date.from(Instant.parse(req.getTrialEndsAt())));
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("error", "trialEndsAt must be ISO-8601"));
            }
        } else if (SubscriptionFeatureService.TRIAL.equals(plan) && school.getTimestamp("trialEndsAt") == null) {
            update.put("trialEndsAt", Date.from(now.plus(30, ChronoUnit.DAYS)));
        }

        int extendTrialDays = req.getExtendTrialDays() == null ? 0 : req.getExtendTrialDays();
        if (extendTrialDays < 0 || extendTrialDays > 365) {
            return ResponseEntity.badRequest().body(Map.of("error", "extendTrialDays must be between 0 and 365"));
        }
        if (extendTrialDays > 0) {
            if (!SubscriptionFeatureService.TRIAL.equals(plan)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Trial extension is only available on the Trial plan"));
            }
            Instant existing = school.getTimestamp("trialEndsAt") == null
                    ? now : school.getTimestamp("trialEndsAt").toDate().toInstant();
            Instant base = existing.isAfter(now) ? existing : now;
            Instant extended = base.plus(extendTrialDays, ChronoUnit.DAYS);
            update.put("trialEndsAt", Date.from(extended));
            update.put("currentPeriodEnd", Date.from(extended));
            update.put("subscriptionStatus", "trialing");
            subscriptionStatus = "trialing";
            update.put("graceEndsAt", FieldValue.delete());
            update.put("pastDueAt", FieldValue.delete());
        }

        boolean startNewPeriod = Boolean.TRUE.equals(req.getStartNewPeriod());
        boolean planChanged = !plan.equals(subscriptionFeatureService.normalizePlan(school.getString("plan")));
        if ("active".equals(subscriptionStatus)
                && (startNewPeriod || planChanged || school.getTimestamp("currentPeriodEnd") == null)) {
            update.put("currentPeriodStart", Date.from(now));
            update.put("currentPeriodEnd", Date.from(now.plus(30, ChronoUnit.DAYS)));
            update.put("graceEndsAt", FieldValue.delete());
            update.put("pastDueAt", FieldValue.delete());
            update.put("cancelledAt", FieldValue.delete());
            update.put("subscriptionAccessBlockedAt", FieldValue.delete());
        } else if ("past_due".equals(subscriptionStatus) && school.getTimestamp("graceEndsAt") == null) {
            update.put("pastDueAt", Date.from(now));
            update.put("graceEndsAt", Date.from(now.plus(7, ChronoUnit.DAYS)));
        } else if ("cancelled".equals(subscriptionStatus)) {
            update.put("cancelledAt", Date.from(now));
            update.put("subscriptionAccessBlockedAt", Date.from(now));
        }

        ref.update(update).get();
        Map<String,Object> audit = new LinkedHashMap<>();
        audit.put("plan", plan);
        audit.put("subscriptionStatus", subscriptionStatus);
        audit.put("autoRenew", update.get("autoRenew"));
        audit.put("cancelAtPeriodEnd", update.get("cancelAtPeriodEnd"));
        audit.put("startNewPeriod", startNewPeriod);
        audit.put("extendTrialDays", extendTrialDays);
        audit.put("featureOverrides", overrides);
        auditService.record(masterAdmin, "school.subscription_updated", "school", schoolId, audit);

        DocumentSnapshot refreshed = ref.get().get();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("schoolId", schoolId);
        response.putAll(subscriptionFeatureService.effectiveEntitlements(refreshed));
        response.put("featureOverrides", overrides);
        operationsHealthService.refreshSchool(schoolId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/schools/{schoolId}/subscription/reconcile")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> reconcileSubscription(
            @PathVariable String schoolId,
            @AuthenticationPrincipal FirebaseUserDetails masterAdmin) throws Exception {
        if (!firestore.collection("schools").document(schoolId).get().get().exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        }
        SubscriptionLifecycleService.TransitionResult result = subscriptionLifecycleService.reconcileSchool(schoolId, Instant.now());
        auditService.record(masterAdmin, "school.subscription_reconciled", "school", schoolId,
                Map.of("transition", result.action() == null ? "none" : result.action()));
        DocumentSnapshot refreshed = firestore.collection("schools").document(schoolId).get().get();
        Map<String,Object> response = new LinkedHashMap<>();
        response.put("schoolId", schoolId);
        response.put("transition", result.action() == null ? "none" : result.action());
        response.putAll(subscriptionFeatureService.effectiveEntitlements(refreshed));
        operationsHealthService.refreshSchool(schoolId);
        return ResponseEntity.ok(response);
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

        tenantUsageService.reserve(schoolId, TenantUsageService.STAFF, 1);
        StaffProvisioningService.StaffCreationResult result;
        try {
            result = staffProvisioningService.createStaffAccount(
                    req.getEmail(), req.getLastName(), req.getFirstName(),
                    req.getMiddleInitial(), req.getSuffix(), req.getRole(), schoolId);
        } catch (Exception e) {
            tenantUsageService.release(schoolId, TenantUsageService.STAFF, 1);
            throw e;
        }

        auditService.record(masterAdmin, "staff.created", "user", result.getUid(), Map.of(
                "schoolId", schoolId, "role", req.getRole(), "email", req.getEmail()));
        operationsHealthService.refreshSchool(schoolId);
        return ResponseEntity.ok(Map.of(
                "uid", result.getUid(),
                "role", req.getRole(),
                "schoolId", schoolId,
                "emailSent", result.isEmailSent()
        ));
    }


    @GetMapping("/schools/{schoolId}/usage")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> getTenantUsage(@PathVariable String schoolId) throws Exception {
        if (!firestore.collection("schools").document(schoolId).get().get().exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        }
        return ResponseEntity.ok(tenantUsageService.snapshot(schoolId));
    }

    @PostMapping("/schools/{schoolId}/usage/reconcile")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> reconcileTenantUsage(@PathVariable String schoolId,
                                                   @AuthenticationPrincipal FirebaseUserDetails masterAdmin) throws Exception {
        if (!firestore.collection("schools").document(schoolId).get().get().exists()) {
            return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        }
        tenantUsageService.reconcile(schoolId);
        auditService.record(masterAdmin, "tenant.usage_reconciled", "school", schoolId, Map.of());
        operationsHealthService.refreshSchool(schoolId);
        return ResponseEntity.ok(tenantUsageService.snapshot(schoolId));
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

    public static class SubscriptionUpdateRequest {
        private String plan;
        private String subscriptionStatus;
        private String trialEndsAt;
        private Map<String, Boolean> featureOverrides;
        private Boolean autoRenew;
        private Boolean cancelAtPeriodEnd;
        private Boolean startNewPeriod;
        private Integer extendTrialDays;

        public String getPlan() { return plan; }
        public void setPlan(String plan) { this.plan = plan; }
        public String getSubscriptionStatus() { return subscriptionStatus; }
        public void setSubscriptionStatus(String subscriptionStatus) { this.subscriptionStatus = subscriptionStatus; }
        public String getTrialEndsAt() { return trialEndsAt; }
        public void setTrialEndsAt(String trialEndsAt) { this.trialEndsAt = trialEndsAt; }
        public Map<String, Boolean> getFeatureOverrides() { return featureOverrides; }
        public void setFeatureOverrides(Map<String, Boolean> featureOverrides) { this.featureOverrides = featureOverrides; }
        public Boolean getAutoRenew() { return autoRenew; }
        public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }
        public Boolean getCancelAtPeriodEnd() { return cancelAtPeriodEnd; }
        public void setCancelAtPeriodEnd(Boolean cancelAtPeriodEnd) { this.cancelAtPeriodEnd = cancelAtPeriodEnd; }
        public Boolean getStartNewPeriod() { return startNewPeriod; }
        public void setStartNewPeriod(Boolean startNewPeriod) { this.startNewPeriod = startNewPeriod; }
        public Integer getExtendTrialDays() { return extendTrialDays; }
        public void setExtendTrialDays(Integer extendTrialDays) { this.extendTrialDays = extendTrialDays; }
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

    public static class DataExportAccessRequest {
        private boolean enabled;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

}

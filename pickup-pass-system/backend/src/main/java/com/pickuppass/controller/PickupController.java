package com.pickuppass.controller;

import com.pickuppass.dto.QrVerificationResult;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.PickupMetricsService;
import com.pickuppass.service.IdempotencyService;
import com.pickuppass.service.PushNotificationService;
import com.pickuppass.service.QrVerificationService;
import com.pickuppass.service.SubscriptionFeatureService;
import com.pickuppass.service.TenantUsageService;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pickup")
public class PickupController {

    private final QrVerificationService qrService;
    private final PushNotificationService pushNotificationService;
    private final AuditService auditService;
    private final PickupMetricsService metrics;
    private final IdempotencyService idempotencyService;
    private final SubscriptionFeatureService subscriptionFeatureService;
    private final TenantUsageService tenantUsageService;

    public PickupController(QrVerificationService qrService,
                            PushNotificationService pushNotificationService,
                            AuditService auditService,
                            PickupMetricsService metrics,
                            IdempotencyService idempotencyService,
                            SubscriptionFeatureService subscriptionFeatureService,
                            TenantUsageService tenantUsageService) {
        this.qrService = qrService;
        this.pushNotificationService = pushNotificationService;
        this.auditService = auditService;
        this.metrics = metrics;
        this.idempotencyService = idempotencyService;
        this.subscriptionFeatureService = subscriptionFeatureService;
        this.tenantUsageService = tenantUsageService;
    }

    @GetMapping("/gates")
    @PreAuthorize("hasAnyRole('teacher','school_admin')")
    public ResponseEntity<?> activePickupGates(@AuthenticationPrincipal FirebaseUserDetails staff) throws Exception {
        return ResponseEntity.ok(Map.of("gates", qrService.activePickupGates(staff.getSchoolId(), staff.getUid())));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('teacher','school_admin')")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyRequest req,
                                     @AuthenticationPrincipal FirebaseUserDetails staff) throws Exception {
        Timer.Sample timer = metrics.startTimer();
        try {
            QrVerificationResult result = qrService.verify(req.getQrToken(), staff.getSchoolId());
            if (!result.isValid()) {
                metrics.verificationFailed();
                return ResponseEntity.badRequest().body(Map.of("valid", false, "reason", result.getMessage()));
            }
            metrics.verificationSucceeded();
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "studentId", result.getStudentId(),
                    "parentUid", result.getParentUid()
            ));
        } catch (Exception e) {
            metrics.verificationFailed();
            throw e;
        } finally {
            metrics.stopVerificationTimer(timer);
        }
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('teacher','school_admin')")
    public ResponseEntity<?> approve(@Valid @RequestBody VerifyRequest req,
                                      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                      @RequestHeader(value = "X-Pickup-Gate-Id", required = false) String pickupGateId,
                                      @AuthenticationPrincipal FirebaseUserDetails staff) throws Exception {
        Timer.Sample timer = metrics.startTimer();
        String fingerprint = idempotencyService.fingerprint(req.getQrToken() + "\n" + (pickupGateId == null ? "" : pickupGateId.trim()));
        var replay = idempotencyService.findExisting(staff.getSchoolId(), staff.getUid(),
                "pickup.approve", idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "status", "release_approved",
                    "exitLogId", replay.get(),
                    "idempotentReplay", true));
        }
        try {
            QrVerificationResult result = qrService.verify(req.getQrToken(), staff.getSchoolId());
            if (!result.isValid()) {
                metrics.approvalFailed();
                return ResponseEntity.badRequest().body(Map.of("valid", false, "reason", result.getMessage()));
            }
            String exitLogId = qrService.markUsedAndLog(result, staff.getUid(), staff.getSchoolId(), pickupGateId);
            // Pickup success is authoritative even when push delivery fails internally.
            pushNotificationService.notifyGuardiansOfPickup(result.getStudentId(), result.getParentUid());
            auditService.record(staff, "pickup.approved", "exitLog", exitLogId,
                    Map.of("studentId", result.getStudentId(), "method", "qr_scan",
                            "pickupGateId", pickupGateId == null ? "" : pickupGateId.trim()));
            idempotencyService.storeResult(staff.getSchoolId(), staff.getUid(),
                    "pickup.approve", idempotencyKey, fingerprint, exitLogId);
            metrics.approvalSucceeded();
            tenantUsageService.recordQrPickup(staff.getSchoolId());
            return ResponseEntity.ok(Map.of("status", "release_approved", "exitLogId", exitLogId, "idempotentReplay", false));
        } catch (Exception e) {
            metrics.approvalFailed();
            throw e;
        } finally {
            metrics.stopApprovalTimer(timer);
        }
    }

    @PostMapping("/manual-override")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> manualOverride(@Valid @RequestBody ManualOverrideRequest req,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                             @RequestHeader(value = "X-Pickup-Gate-Id", required = false) String pickupGateId,
                                             @AuthenticationPrincipal FirebaseUserDetails staff) throws Exception {
        subscriptionFeatureService.requireFeature(staff.getSchoolId(), "manual_override");
        String fingerprint = idempotencyService.fingerprint(
                req.getStudentId() + "\n" + req.getGuardianUid() + "\n" + req.getReason().trim() + "\n"
                        + (pickupGateId == null ? "" : pickupGateId.trim()));
        var replay = idempotencyService.findExisting(staff.getSchoolId(), staff.getUid(),
                "pickup.manual_override", idempotencyKey, fingerprint);
        if (replay.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "status", "release_approved",
                    "method", "manual_override",
                    "exitLogId", replay.get(),
                    "idempotentReplay", true));
        }
        try {
            String exitLogId = qrService.manualOverride(req.getStudentId(), req.getGuardianUid(), req.getReason(),
                    staff.getUid(), staff.getSchoolId(), pickupGateId);
            pushNotificationService.notifyGuardiansOfPickup(req.getStudentId(), req.getGuardianUid());
            auditService.record(staff, "pickup.manual_override", "exitLog", exitLogId, Map.of(
                    "studentId", req.getStudentId(),
                    "guardianUid", req.getGuardianUid(),
                    "reason", req.getReason().trim(),
                    "pickupGateId", pickupGateId == null ? "" : pickupGateId.trim()));
            idempotencyService.storeResult(staff.getSchoolId(), staff.getUid(),
                    "pickup.manual_override", idempotencyKey, fingerprint, exitLogId);
            metrics.manualOverrideSucceeded();
            tenantUsageService.recordManualPickup(staff.getSchoolId());
            return ResponseEntity.ok(Map.of(
                    "status", "release_approved",
                    "method", "manual_override",
                    "exitLogId", exitLogId,
                    "idempotentReplay", false));
        } catch (Exception e) {
            metrics.manualOverrideFailed();
            throw e;
        }
    }

    public static class VerifyRequest {
        @NotBlank private String qrToken;
        public String getQrToken() { return qrToken; }
        public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    }

    public static class ManualOverrideRequest {
        @NotBlank private String studentId;
        @NotBlank private String guardianUid;
        @NotBlank @Size(min = 5, max = 500) private String reason;
        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
        public String getGuardianUid() { return guardianUid; }
        public void setGuardianUid(String guardianUid) { this.guardianUid = guardianUid; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}

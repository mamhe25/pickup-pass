package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.LaunchReadinessService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

/** Tenant onboarding/readiness API. Final launch approval is platform-owner only. */
@RestController
@RequestMapping("/api")
public class LaunchReadinessController {

    private final LaunchReadinessService readinessService;
    private final AuditService auditService;

    public LaunchReadinessController(LaunchReadinessService readinessService, AuditService auditService) {
        this.readinessService = readinessService;
        this.auditService = auditService;
    }

    @GetMapping("/school-admin/launch-readiness")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> schoolAssessment(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        return ResponseEntity.ok(readinessService.assess(admin.getSchoolId()));
    }

    @PutMapping("/school-admin/launch-readiness/manual-checks")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> saveManualChecks(@RequestBody ManualChecksRequest request,
                                               @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        Map<String, Object> result = readinessService.saveManualChecks(
                admin.getSchoolId(), request.getManualChecks(), admin.getUid());
        auditService.record(admin, "launch_readiness.manual_checks_updated", "school", admin.getSchoolId(),
                Map.of("manualChecks", request.getManualChecks() == null ? Map.of() : request.getManualChecks()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/school-admin/launch-readiness/request-review")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> requestReview(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        Map<String, Object> result = readinessService.requestReview(admin.getSchoolId(), admin.getUid());
        auditService.record(admin, "launch_readiness.review_requested", "school", admin.getSchoolId(), Map.of());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/master-admin/schools/{schoolId}/launch-readiness")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> masterAssessment(@PathVariable String schoolId) throws Exception {
        return ResponseEntity.ok(readinessService.assess(schoolId));
    }

    @PostMapping("/master-admin/schools/{schoolId}/launch-readiness/approve")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> approve(@PathVariable String schoolId,
                                     @RequestBody(required = false) ReviewDecisionRequest request,
                                     @AuthenticationPrincipal FirebaseUserDetails master) throws Exception {
        String note = request == null ? "" : request.getNote();
        Map<String, Object> result = readinessService.approve(schoolId, master.getUid(), note);
        auditService.record(master, "launch_readiness.approved", "school", schoolId,
                note == null || note.isBlank() ? Map.of() : Map.of("note", note.trim()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/master-admin/schools/{schoolId}/launch-readiness/reopen")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> reopen(@PathVariable String schoolId,
                                    @RequestBody(required = false) ReviewDecisionRequest request,
                                    @AuthenticationPrincipal FirebaseUserDetails master) throws Exception {
        String reason = request == null ? "" : request.getNote();
        Map<String, Object> result = readinessService.reopen(schoolId, master.getUid(), reason);
        auditService.record(master, "launch_readiness.reopened", "school", schoolId,
                reason == null || reason.isBlank() ? Map.of() : Map.of("reason", reason.trim()));
        return ResponseEntity.ok(result);
    }

    public static class ManualChecksRequest {
        private Map<String, Boolean> manualChecks = Collections.emptyMap();
        public Map<String, Boolean> getManualChecks() { return manualChecks; }
        public void setManualChecks(Map<String, Boolean> manualChecks) { this.manualChecks = manualChecks; }
    }

    public static class ReviewDecisionRequest {
        private String note;
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}

package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.DemoRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/master-admin/demo-requests")
@PreAuthorize("hasRole('master_admin')")
public class MasterDemoRequestController {

    private final DemoRequestService demoRequests;
    private final AuditService auditService;

    public MasterDemoRequestController(
            DemoRequestService demoRequests,
            AuditService auditService) {
        this.demoRequests = demoRequests;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<?> list() throws Exception {
        return ResponseEntity.ok(demoRequests.list());
    }

    @PatchMapping("/{requestId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String requestId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal FirebaseUserDetails owner)
            throws Exception {
        String status = body == null ? "" : String.valueOf(body.getOrDefault("status", ""));
        Map<String, Object> result = demoRequests.updateStatus(requestId, status, owner.getUid());

        auditService.record(
                owner,
                "demo_request.status_changed",
                "demo_request",
                requestId,
                Map.of(
                        "previousStatus", String.valueOf(result.getOrDefault("previousStatus", "")),
                        "status", String.valueOf(result.getOrDefault("status", ""))));

        return ResponseEntity.ok(result);
    }
}

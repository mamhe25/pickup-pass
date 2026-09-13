package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.DemoRequestCommunicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/master-admin/demo-requests")
@PreAuthorize("hasRole('master_admin')")
public class MasterDemoRequestController {

    private final DemoRequestCommunicationService communications;
    private final AuditService auditService;

    public MasterDemoRequestController(
            DemoRequestCommunicationService communications,
            AuditService auditService) {
        this.communications = communications;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<?> list() throws Exception {
        return ResponseEntity.ok(communications.list());
    }

    @GetMapping("/{requestId}/communications")
    public ResponseEntity<?> communicationHistory(@PathVariable String requestId)
            throws Exception {
        return ResponseEntity.ok(communications.communicationHistory(requestId));
    }

    @PatchMapping("/{requestId}/status")
    public ResponseEntity<?> updateStatus(
            @PathVariable String requestId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal FirebaseUserDetails owner)
            throws Exception {
        Map<String, Object> result = communications.updateStatus(requestId, body, owner.getUid());

        auditService.record(
                owner,
                "demo_request.status_changed",
                "demo_request",
                requestId,
                Map.of(
                        "previousStatus", String.valueOf(result.getOrDefault("previousStatus", "")),
                        "status", String.valueOf(result.getOrDefault("status", "")),
                        "requesterNotified", Boolean.TRUE.equals(result.get("notificationEmailSent"))));

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{requestId}/messages")
    public ResponseEntity<?> sendUpdate(
            @PathVariable String requestId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal FirebaseUserDetails owner)
            throws Exception {
        Map<String, Object> result = communications.sendManualUpdate(requestId, body, owner.getUid());

        auditService.record(
                owner,
                "demo_request.update_sent",
                "demo_request",
                requestId,
                Map.of(
                        "channel", "email",
                        "sent", Boolean.TRUE.equals(result.get("sent"))));

        return ResponseEntity.ok(result);
    }
}

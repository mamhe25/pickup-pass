package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.SaasOperationsHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/master-admin/operations")
@PreAuthorize("hasRole('master_admin')")
public class MasterOperationsController {
    private final SaasOperationsHealthService operations;
    private final AuditService auditService;

    public MasterOperationsController(SaasOperationsHealthService operations, AuditService auditService) {
        this.operations = operations;
        this.auditService = auditService;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview() throws Exception {
        return ResponseEntity.ok(operations.overview());
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        SaasOperationsHealthService.RefreshResult result = operations.refreshAll();
        auditService.record(actor, "saas.operations_refreshed", "platform", "global", Map.of(
                "executed", result.executed(),
                "activeAlerts", result.activeAlerts(),
                "resolvedAlerts", result.resolvedAlerts(),
                "status", result.status()
        ));
        return ResponseEntity.ok(Map.of(
                "executed", result.executed(),
                "activeAlerts", result.activeAlerts(),
                "resolvedAlerts", result.resolvedAlerts(),
                "status", result.status()
        ));
    }
}

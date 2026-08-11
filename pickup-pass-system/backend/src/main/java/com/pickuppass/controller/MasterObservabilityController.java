package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.PlatformObservabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/master-admin/observability")
@PreAuthorize("hasRole('master_admin')")
public class MasterObservabilityController {
    private final PlatformObservabilityService observability;
    private final AuditService audit;

    public MasterObservabilityController(PlatformObservabilityService observability, AuditService audit) {
        this.observability = observability;
        this.audit = audit;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(observability.overview());
    }

    @PostMapping("/evaluate")
    public ResponseEntity<?> evaluate(@AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        Map<String, Object> result = observability.evaluate();
        audit.record(actor, "observability.manual_evaluation", "platform", "global", Map.of("mode", "startup_low_cost"));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/incidents/{incidentId}/status")
    public ResponseEntity<?> setIncidentStatus(
            @PathVariable String incidentId,
            @RequestBody IncidentStatusRequest request,
            @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        boolean updated = observability.setIncidentStatus(incidentId, request.status(), actor, request.note());
        if (!updated) return ResponseEntity.notFound().build();
        audit.record(actor, "observability.incident_status_changed", "platformIncident", incidentId,
                Map.of("status", request.status(), "note", request.note() == null ? "" : request.note()));
        return ResponseEntity.ok(Map.of("status", request.status()));
    }

    public record IncidentStatusRequest(String status, String note) { }
}

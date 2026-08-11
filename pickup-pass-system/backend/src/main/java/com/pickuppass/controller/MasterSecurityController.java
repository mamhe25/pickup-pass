package com.pickuppass.controller;

import com.google.firebase.auth.FirebaseAuth;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.DeviceSessionService;
import com.pickuppass.service.SecurityEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/master-admin/security")
public class MasterSecurityController {
    private final SecurityEventService security;
    private final DeviceSessionService sessions;
    private final FirebaseAuth firebaseAuth;
    private final AuditService audit;

    public MasterSecurityController(SecurityEventService security, DeviceSessionService sessions,
                                    FirebaseAuth firebaseAuth, AuditService audit) {
        this.security = security; this.sessions = sessions; this.firebaseAuth = firebaseAuth; this.audit = audit;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> overview(@RequestParam(defaultValue="100") int limit) throws Exception {
        return ResponseEntity.ok(security.overview(limit));
    }

    @PostMapping("/alerts/{alertId}/status")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> setStatus(@PathVariable String alertId, @RequestBody AlertStatusRequest req,
                                       @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        boolean ok = security.setAlertStatus(alertId, req.status(), actor, req.note());
        if (!ok) return ResponseEntity.notFound().build();
        audit.record(actor, "security.alert_status_changed", "securityAlert", alertId,
                Map.of("status", req.status(), "note", req.note()==null?"":req.note()));
        return ResponseEntity.ok(Map.of("status", req.status()));
    }

    @PostMapping("/users/{uid}/revoke-sessions")
    @PreAuthorize("hasRole('master_admin')")
    public ResponseEntity<?> revokeUserSessions(@PathVariable String uid, @RequestBody RevokeSessionsRequest req,
                                                 @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        String reason = req.reason()==null?"":req.reason().trim();
        if (reason.length() < 5) return ResponseEntity.badRequest().body(Map.of("error","A short reason is required"));
        int devices = sessions.revokeAllByAdmin(uid, reason);
        firebaseAuth.revokeRefreshTokens(uid);
        audit.record(actor, "security.user_sessions_revoked", "user", uid, Map.of("reason", reason, "deviceSessions", devices));
        return ResponseEntity.ok(Map.of("uid",uid,"revokedDeviceSessions",devices,"refreshTokensRevoked",true));
    }

    public record AlertStatusRequest(String status, String note) {}
    public record RevokeSessionsRequest(String reason) {}
}

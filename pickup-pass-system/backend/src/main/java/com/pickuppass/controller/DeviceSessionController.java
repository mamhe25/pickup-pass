package com.pickuppass.controller;

import com.pickuppass.security.DeviceSessionFilter;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.DeviceSessionService;
import com.pickuppass.service.SubscriptionFeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/session/devices")
public class DeviceSessionController {
    private final DeviceSessionService sessions;
    private final AuditService auditService;
    private final SubscriptionFeatureService subscriptionFeatureService;

    public DeviceSessionController(DeviceSessionService sessions, AuditService auditService,
                                   SubscriptionFeatureService subscriptionFeatureService) {
        this.sessions = sessions;
        this.auditService = auditService;
        this.subscriptionFeatureService = subscriptionFeatureService;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal FirebaseUserDetails user,
                                  @RequestHeader(value = DeviceSessionFilter.DEVICE_ID, required = false) String currentDeviceId) throws Exception {
        subscriptionFeatureService.requireFeature(user.getSchoolId(), "device_session_management");
        return ResponseEntity.ok(Map.of("devices", sessions.listForUser(user.getUid(), currentDeviceId)));
    }

    @PostMapping("/{deviceId}/revoke")
    public ResponseEntity<?> revoke(@PathVariable String deviceId,
                                    @AuthenticationPrincipal FirebaseUserDetails user) throws Exception {
        subscriptionFeatureService.requireFeature(user.getSchoolId(), "device_session_management");
        boolean revoked = sessions.revokeOwnDevice(user.getUid(), deviceId);
        if (!revoked) return ResponseEntity.notFound().build();
        auditService.record(user, "session.device_revoked", "device_session", deviceId, Map.of());
        return ResponseEntity.ok(Map.of("status", "revoked"));
    }

    @PostMapping("/revoke-others")
    public ResponseEntity<?> revokeOthers(@AuthenticationPrincipal FirebaseUserDetails user,
                                          @RequestHeader(value = DeviceSessionFilter.DEVICE_ID, required = false) String currentDeviceId) throws Exception {
        subscriptionFeatureService.requireFeature(user.getSchoolId(), "device_session_management");
        int count = sessions.revokeOtherDevices(user.getUid(), currentDeviceId);
        auditService.record(user, "session.other_devices_revoked", "user", user.getUid(), Map.of("count", count));
        return ResponseEntity.ok(Map.of("status", "ok", "revokedCount", count));
    }
}

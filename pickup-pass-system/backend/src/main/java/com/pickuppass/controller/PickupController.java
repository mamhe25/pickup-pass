package com.pickuppass.controller;

import com.pickuppass.dto.QrVerificationResult;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.PushNotificationService;
import com.pickuppass.service.QrVerificationService;
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

    public PickupController(QrVerificationService qrService, PushNotificationService pushNotificationService) {
        this.qrService = qrService;
        this.pushNotificationService = pushNotificationService;
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAnyRole('teacher','school_admin')")
    public ResponseEntity<?> verify(@RequestBody VerifyRequest req,
                                     @AuthenticationPrincipal FirebaseUserDetails staff) throws Exception {
        QrVerificationResult result = qrService.verify(req.getQrToken(), staff.getSchoolId());
        if (!result.isValid()) {
            return ResponseEntity.status(400).body(Map.of("valid", false, "reason", result.getMessage()));
        }
        return ResponseEntity.ok(Map.of(
                "valid", true,
                "studentId", result.getStudentId(),
                "parentUid", result.getParentUid()
        ));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('teacher','school_admin')")
    public ResponseEntity<?> approve(@RequestBody VerifyRequest req,
                                      @AuthenticationPrincipal FirebaseUserDetails staff) throws Exception {
        QrVerificationResult result = qrService.verify(req.getQrToken(), staff.getSchoolId());
        if (!result.isValid()) {
            return ResponseEntity.status(400).body(Map.of("valid", false, "reason", result.getMessage()));
        }
        qrService.markUsedAndLog(result, staff.getUid(), staff.getSchoolId());
        pushNotificationService.notifyGuardiansOfPickup(result.getStudentId(), result.getParentUid());
        return ResponseEntity.ok(Map.of("status", "release_approved"));
    }

    public static class VerifyRequest {
        private String qrToken;
        public String getQrToken() { return qrToken; }
        public void setQrToken(String qrToken) { this.qrToken = qrToken; }
    }
}

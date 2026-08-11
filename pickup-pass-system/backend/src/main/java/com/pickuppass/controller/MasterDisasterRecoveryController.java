package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.FirestoreDisasterRecoveryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/master-admin/disaster-recovery")
@PreAuthorize("hasRole('master_admin')")
public class MasterDisasterRecoveryController {

    private final FirestoreDisasterRecoveryService disasterRecoveryService;

    public MasterDisasterRecoveryController(FirestoreDisasterRecoveryService disasterRecoveryService) {
        this.disasterRecoveryService = disasterRecoveryService;
    }

    @GetMapping("/overview")
    public ResponseEntity<?> overview() {
        return ResponseEntity.ok(disasterRecoveryService.overview());
    }

    @PostMapping("/protection/free")
    public ResponseEntity<?> applyFreeSafeguards(
            @RequestBody ApplyProtectionRequest request,
            @AuthenticationPrincipal FirebaseUserDetails actor) {
        try {
            return ResponseEntity.ok(disasterRecoveryService.applyFreeSafeguards(
                    request.getConfirmationText(), actor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Could not configure free Firestore safeguards"));
        }
    }

    @PostMapping("/protection/startup")
    public ResponseEntity<?> applyStartupProtection(
            @RequestBody ApplyProtectionRequest request,
            @AuthenticationPrincipal FirebaseUserDetails actor) {
        try {
            return ResponseEntity.ok(disasterRecoveryService.applyStartupProtection(
                    request.getConfirmationText(), actor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Could not configure startup Firestore protection"));
        }
    }

    @PostMapping("/protection/recommended")
    public ResponseEntity<?> applyRecommendedProtection(
            @RequestBody ApplyProtectionRequest request,
            @AuthenticationPrincipal FirebaseUserDetails actor) {
        try {
            return ResponseEntity.ok(disasterRecoveryService.applyRecommendedProtection(
                    request.getConfirmationText(), actor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Could not configure Firestore disaster-recovery protection"));
        }
    }

    @PostMapping("/recovery-drills")
    public ResponseEntity<?> startRecoveryDrill(
            @RequestBody StartRecoveryDrillRequest request,
            @AuthenticationPrincipal FirebaseUserDetails actor) {
        try {
            return ResponseEntity.ok(disasterRecoveryService.startRecoveryDrill(
                    request.getBackupName(), request.getReason(), request.getConfirmationText(), actor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Could not start isolated Firestore recovery drill"));
        }
    }

    @PostMapping("/recovery-drills/{jobId}/refresh")
    public ResponseEntity<?> refreshRecoveryDrill(
            @PathVariable String jobId,
            @AuthenticationPrincipal FirebaseUserDetails actor) {
        try {
            return ResponseEntity.ok(disasterRecoveryService.refreshRecoveryJob(jobId, actor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Could not refresh recovery-drill status"));
        }
    }

    public static class ApplyProtectionRequest {
        private String confirmationText;
        public String getConfirmationText() { return confirmationText; }
        public void setConfirmationText(String confirmationText) { this.confirmationText = confirmationText; }
    }

    public static class StartRecoveryDrillRequest {
        private String backupName;
        private String reason;
        private String confirmationText;
        public String getBackupName() { return backupName; }
        public void setBackupName(String backupName) { this.backupName = backupName; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public String getConfirmationText() { return confirmationText; }
        public void setConfirmationText(String confirmationText) { this.confirmationText = confirmationText; }
    }
}

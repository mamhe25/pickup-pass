package com.pickuppass.controller;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.pickuppass.service.StaffProvisioningService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Solves the chicken-and-egg problem every other endpoint in this app has:
 * everything else requires you to already be signed in as some role in
 * order to create the next role down (master_admin creates school_admin,
 * school_admin/teacher creates parent, parent creates backup guardian) —
 * but nothing creates the very first account.
 *
 * This endpoint is intentionally NOT behind Firebase auth (there's no user
 * to authenticate as yet). Instead it's protected two ways:
 *   1. A shared secret (BOOTSTRAP_SECRET env var) passed as a header —
 *      not embedded anywhere in the codebase, not a default credential.
 *   2. It refuses to run at all once any master_admin already exists,
 *      even with a correct secret — so leaking the secret after initial
 *      setup doesn't let someone mint extra master admins.
 *
 * Operational recommendation: after using this once, unset/rotate
 * BOOTSTRAP_SECRET in your deployment so the endpoint has no valid secret
 * to check against going forward.
 */
@RestController
@RequestMapping("/api/bootstrap")
public class BootstrapController {

    private final Firestore firestore;
    private final StaffProvisioningService staffProvisioningService;
    private final String bootstrapSecret;

    public BootstrapController(
            Firestore firestore,
            StaffProvisioningService staffProvisioningService,
            @Value("${bootstrap.secret:}") String bootstrapSecret) {
        this.firestore = firestore;
        this.staffProvisioningService = staffProvisioningService;
        this.bootstrapSecret = bootstrapSecret;
    }

    @PostMapping("/master-admin")
    public ResponseEntity<?> createFirstMasterAdmin(
            @RequestHeader(value = "X-Bootstrap-Secret", required = false) String providedSecret,
            @RequestBody CreateMasterAdminRequest req) throws Exception {

        if (bootstrapSecret.isBlank()) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "Bootstrap is disabled — BOOTSTRAP_SECRET is not set on the server"));
        }
        if (providedSecret == null || !constantTimeEquals(providedSecret, bootstrapSecret)) {
            return ResponseEntity.status(403).body(Map.of("error", "Invalid bootstrap secret"));
        }

        QuerySnapshot existingMasterAdmins = firestore.collection("users")
                .whereEqualTo("role", "master_admin")
                .limit(1)
                .get()
                .get();
        if (!existingMasterAdmins.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "A master_admin already exists — bootstrap only runs once. " +
                             "Rotate BOOTSTRAP_SECRET if this wasn't you."));
        }

        StaffProvisioningService.StaffCreationResult result = staffProvisioningService.createStaffAccount(
                req.getEmail(), req.getDisplayName(), "master_admin", null);
        return ResponseEntity.ok(Map.of(
                "uid", result.getUid(),
                "email", req.getEmail(),
                "status", "master_admin_created",
                "emailSent", result.isEmailSent(),
                "note", result.isEmailSent()
                        ? "A password-reset email was sent so this account can set its own password."
                        : "The invite email could not be sent — use the 'Forgot password?' link on the sign-in page with this email instead."
        ));
    }

    /** Avoids leaking secret length/content via response-timing side channels. */
    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    public static class CreateMasterAdminRequest {
        @NotBlank private String email;
        @NotBlank private String displayName;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}

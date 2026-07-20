package com.pickuppass.controller;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Lets a signed-in user (any role) register the FCM token for the device
 * they're currently using, so the backend knows where to push
 * "your child was picked up" notifications. Tokens are stored as an array
 * on the user's own profile — a person can be signed in on more than one
 * device and should get notified on all of them.
 */
@RestController
@RequestMapping("/api/device")
public class DeviceController {

    private final Firestore firestore;

    public DeviceController(Firestore firestore) {
        this.firestore = firestore;
    }

    @PostMapping("/register-token")
    public ResponseEntity<?> registerToken(
            @RequestBody TokenRequest req,
            @AuthenticationPrincipal FirebaseUserDetails user) throws Exception {

        firestore.collection("users").document(user.getUid())
                .update("fcmTokens", FieldValue.arrayUnion(req.getToken()))
                .get();

        return ResponseEntity.ok(Map.of("status", "registered"));
    }

    @PostMapping("/unregister-token")
    public ResponseEntity<?> unregisterToken(
            @RequestBody TokenRequest req,
            @AuthenticationPrincipal FirebaseUserDetails user) throws Exception {

        firestore.collection("users").document(user.getUid())
                .update("fcmTokens", FieldValue.arrayRemove(req.getToken()))
                .get();

        return ResponseEntity.ok(Map.of("status", "unregistered"));
    }

    public static class TokenRequest {
        @NotBlank
        private String token;

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}

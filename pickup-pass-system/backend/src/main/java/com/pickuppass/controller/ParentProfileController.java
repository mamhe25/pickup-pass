package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.GuardianPhotoValidationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns guardian verification-photo updates.
 *
 * Photos are validated server-side before the user profile is changed.
 * Clients cannot mark a photo as validated themselves.
 */
@RestController
@RequestMapping("/api/parent/profile")
public class ParentProfileController {

    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png");

    private final Firestore firestore;
    private final GuardianPhotoValidationService photoValidationService;
    private final AuditService auditService;

    public ParentProfileController(
            Firestore firestore,
            GuardianPhotoValidationService photoValidationService,
            AuditService auditService) {
        this.firestore = firestore;
        this.photoValidationService = photoValidationService;
        this.auditService = auditService;
    }

    @PostMapping(
            value = "/verification-photo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('parent')")
    public ResponseEntity<?> uploadVerificationPhoto(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal FirebaseUserDetails parent)
            throws Exception {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Choose a photo before continuing"));
        }

        if (file.getSize() > GuardianPhotoValidationService.MAX_UPLOAD_BYTES) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Photo is too large. Choose an image under 1 MB."));
        }

        String contentType =
                file.getContentType() == null
                        ? ""
                        : file.getContentType().toLowerCase();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Use a JPEG or PNG photo"));
        }

        DocumentReference userRef =
                firestore.collection("users").document(parent.getUid());
        DocumentSnapshot user = userRef.get().get();

        if (!user.exists()
                || !parent.getSchoolId().equals(user.getString("schoolId"))
                || !"parent".equals(user.getString("role"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "Parent profile could not be verified"));
        }
        if (Boolean.FALSE.equals(user.getBoolean("isActive"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "This guardian account is inactive"));
        }

        byte[] bytes = file.getBytes();
        GuardianPhotoValidationService.ValidationResult validation =
                photoValidationService.validate(bytes);

        if (!validation.accepted()) {
            HttpStatus status =
                    validation.validatorUnavailable()
                            ? HttpStatus.SERVICE_UNAVAILABLE
                            : HttpStatus.UNPROCESSABLE_ENTITY;
            return ResponseEntity.status(status).body(Map.of(
                    "error", validation.message(),
                    "photoValidationStatus",
                    validation.validatorUnavailable()
                            ? "unavailable"
                            : "rejected"
            ));
        }

        String normalizedContentType =
                "image/png".equals(contentType)
                        ? "image/png"
                        : "image/jpeg";
        String dataUri =
                "data:"
                        + normalizedContentType
                        + ";base64,"
                        + Base64.getEncoder().encodeToString(bytes);

        Map<String, Object> update = new HashMap<>();
        update.put("photoUrl", dataUri);
        update.put("photoValidationStatus", "verified");
        update.put("photoValidatedAt", FieldValue.serverTimestamp());
        update.put("photoValidationProvider", "google_cloud_vision");
        update.put("photoValidationVersion", 1);
        update.put("photoDetectionConfidence", validation.detectionConfidence());
        update.put("photoFaceAreaRatio", validation.faceAreaRatio());
        userRef.update(update).get();

        auditService.record(
                parent,
                "guardian.verification_photo_updated",
                "user",
                parent.getUid(),
                Map.of(
                        "validationProvider", "google_cloud_vision",
                        "validationVersion", 1
                )
        );

        return ResponseEntity.ok(Map.of(
                "status", "verified",
                "photoUrl", dataUri,
                "message",
                "Verification photo accepted. School staff can use it during pickup identity checks."
        ));
    }
}

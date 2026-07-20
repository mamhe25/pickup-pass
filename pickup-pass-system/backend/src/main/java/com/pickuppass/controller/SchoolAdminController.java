package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.SchoolLogoService;
import com.pickuppass.service.StaffProvisioningService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/** Everything a school_admin can do for their own school: branding and staff invites. */
@RestController
@RequestMapping("/api/school-admin")
public class SchoolAdminController {

    private final SchoolLogoService logoService;
    private final StaffProvisioningService staffProvisioningService;

    public SchoolAdminController(SchoolLogoService logoService, StaffProvisioningService staffProvisioningService) {
        this.logoService = logoService;
        this.staffProvisioningService = staffProvisioningService;
    }

    /** A school admin can update their own school's logo — no schoolId param, always their own claim. */
    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> uploadLogo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {
        try {
            String logoUrl = logoService.uploadLogo(schoolAdmin.getSchoolId(), file);
            return ResponseEntity.ok(Map.of("logoUrl", logoUrl));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Invites a teacher for the school admin's OWN school. Deliberately
     * restricted to the 'teacher' role only — a school_admin can't use this
     * to mint another school_admin or a master_admin for themselves or
     * anyone else. Creating a school_admin account requires a master_admin
     * (see MasterAdminController.createStaff).
     */
    /**
     * Invites a teacher for the school admin's OWN school. Deliberately
     * restricted to the 'teacher' role only — a school_admin can't use this
     * to mint another school_admin or a master_admin for themselves or
     * anyone else. Creating a school_admin account requires a master_admin
     * (see MasterAdminController.createStaff). No catch-all here — see the
     * comment on MasterAdminController.createStaff for why.
     */
    @PostMapping("/staff")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> inviteTeacher(
            @RequestBody InviteTeacherRequest req,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {
        StaffProvisioningService.StaffCreationResult result = staffProvisioningService.createStaffAccount(
                req.getEmail(), req.getDisplayName(), "teacher", schoolAdmin.getSchoolId());
        return ResponseEntity.ok(Map.of(
                "uid", result.getUid(),
                "role", "teacher",
                "emailSent", result.isEmailSent()
        ));
    }

    public static class InviteTeacherRequest {
        @NotBlank private String email;
        @NotBlank private String displayName;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}

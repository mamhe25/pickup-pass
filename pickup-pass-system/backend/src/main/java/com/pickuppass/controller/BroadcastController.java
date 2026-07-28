package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.BroadcastService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class BroadcastController {

    private static final Set<String> VALID_AUDIENCE_ROLES = Set.of("teacher", "parent");

    private final BroadcastService broadcastService;

    public BroadcastController(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    /**
     * School-wide (or role-filtered) announcement — "early dismissal
     * Friday" to everyone, or just to teachers, or just to guardians.
     */
    @PostMapping("/api/school-admin/broadcasts")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> broadcastToSchool(
            @RequestBody BroadcastRequest req,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {

        if (req.getTitle() == null || req.getTitle().isBlank() || req.getBody() == null || req.getBody().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title and body are required"));
        }
        List<String> audience = req.getAudience();
        if (audience == null || audience.isEmpty() || !VALID_AUDIENCE_ROLES.containsAll(audience)) {
            return ResponseEntity.badRequest().body(Map.of("error", "audience must be a non-empty subset of ['teacher','parent']"));
        }

        int recipientCount = broadcastService.broadcastToSchool(
                schoolAdmin.getSchoolId(), schoolAdmin.getUid(),
                req.getTitle().trim(), req.getBody().trim(), audience);

        return ResponseEntity.ok(Map.of("recipientCount", recipientCount));
    }

    /**
     * Section-scoped announcement — reaches only the guardians of students
     * in the sending teacher's own assigned section(s). Deliberately no
     * audience parameter here (unlike the admin endpoint): a teacher's
     * broadcast always goes to guardians, never to other staff.
     */
    @PostMapping("/api/teacher/broadcasts")
    @PreAuthorize("hasRole('teacher')")
    public ResponseEntity<?> broadcastToSection(
            @RequestBody BroadcastRequest req,
            @AuthenticationPrincipal FirebaseUserDetails teacher) throws Exception {

        if (req.getTitle() == null || req.getTitle().isBlank() || req.getBody() == null || req.getBody().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title and body are required"));
        }

        int recipientCount = broadcastService.broadcastToSection(
                teacher.getSchoolId(), teacher.getUid(),
                req.getTitle().trim(), req.getBody().trim());

        return ResponseEntity.ok(Map.of("recipientCount", recipientCount));
    }

    public static class BroadcastRequest {
        @NotBlank private String title;
        @NotBlank private String body;
        private List<String> audience = new ArrayList<>(); // only used by the school-admin endpoint

        public String getTitle() { return title; }
        public void setTitle(String v) { this.title = v; }
        public String getBody() { return body; }
        public void setBody(String v) { this.body = v; }
        public List<String> getAudience() { return audience; }
        public void setAudience(List<String> v) { this.audience = v; }
    }
}

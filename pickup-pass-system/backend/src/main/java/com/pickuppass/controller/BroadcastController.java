package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.BroadcastService;
import com.pickuppass.service.ScheduledBroadcastService;
import com.pickuppass.service.SubscriptionFeatureService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
public class BroadcastController {

    private static final Set<String> VALID_AUDIENCE_ROLES = Set.of("teacher", "parent");
    private static final Duration MIN_SCHEDULE_AHEAD = Duration.ofSeconds(30);
    private static final Duration MAX_SCHEDULE_AHEAD = Duration.ofDays(90);

    private final BroadcastService broadcastService;
    private final AuditService auditService;
    private final ScheduledBroadcastService scheduledBroadcastService;
    private final SubscriptionFeatureService subscriptionFeatureService;

    public BroadcastController(BroadcastService broadcastService,
                               AuditService auditService,
                               ScheduledBroadcastService scheduledBroadcastService,
                               SubscriptionFeatureService subscriptionFeatureService) {
        this.broadcastService = broadcastService;
        this.auditService = auditService;
        this.scheduledBroadcastService = scheduledBroadcastService;
        this.subscriptionFeatureService = subscriptionFeatureService;
    }

    @PostMapping("/api/school-admin/broadcasts")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> broadcastToSchool(
            @RequestBody BroadcastRequest req,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {

        ResponseEntity<?> validation = validateSchoolBroadcast(req);
        if (validation != null) return validation;

        String title = req.getTitle().trim();
        String body = req.getBody().trim();
        List<String> audience = List.copyOf(req.getAudience());

        int recipientCount = broadcastService.broadcastToSchool(
                schoolAdmin.getSchoolId(), schoolAdmin.getUid(), title, body, audience);
        String broadcastId = scheduledBroadcastService.recordImmediate(
                schoolAdmin, title, body, audience, recipientCount);

        auditService.record(schoolAdmin, "broadcast.school_sent", "broadcast", broadcastId, Map.of(
                "title", title, "audience", audience, "recipientCount", recipientCount));

        return ResponseEntity.ok(Map.of("recipientCount", recipientCount, "broadcastId", broadcastId));
    }

    @PostMapping("/api/school-admin/broadcasts/schedule")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> scheduleBroadcast(
            @RequestBody ScheduledBroadcastRequest req,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {

        subscriptionFeatureService.requireFeature(schoolAdmin.getSchoolId(), "scheduled_announcements");
        ResponseEntity<?> validation = validateSchoolBroadcast(req);
        if (validation != null) return validation;
        if (req.getScheduledAt() == null || req.getScheduledAt().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "scheduledAt is required"));
        }

        final Instant scheduledAt;
        try {
            scheduledAt = Instant.parse(req.getScheduledAt().trim());
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "scheduledAt must be an ISO-8601 UTC timestamp"));
        }

        Instant now = Instant.now();
        if (scheduledAt.isBefore(now.plus(MIN_SCHEDULE_AHEAD))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Schedule the announcement at least 30 seconds in the future"));
        }
        if (scheduledAt.isAfter(now.plus(MAX_SCHEDULE_AHEAD))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Announcements can be scheduled up to 90 days ahead"));
        }

        String title = req.getTitle().trim();
        String body = req.getBody().trim();
        List<String> audience = List.copyOf(req.getAudience());
        String id = scheduledBroadcastService.schedule(schoolAdmin, title, body, audience, scheduledAt);

        auditService.record(schoolAdmin, "broadcast.scheduled", "broadcast", id, Map.of(
                "title", title,
                "audience", audience,
                "scheduledAt", scheduledAt.toString()));

        return ResponseEntity.ok(Map.of(
                "broadcastId", id,
                "status", "scheduled",
                "scheduledAt", scheduledAt.toString()
        ));
    }

    @GetMapping("/api/school-admin/broadcasts/history")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> broadcastHistory(
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {
        return ResponseEntity.ok(Map.of(
                "broadcasts", scheduledBroadcastService.history(schoolAdmin.getSchoolId(), limit)
        ));
    }

    @DeleteMapping("/api/school-admin/broadcasts/{broadcastId}")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> cancelScheduledBroadcast(
            @PathVariable String broadcastId,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {
        boolean cancelled = scheduledBroadcastService.cancel(schoolAdmin.getSchoolId(), broadcastId);
        if (!cancelled) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Only a pending scheduled announcement from your school can be cancelled"));
        }
        auditService.record(schoolAdmin, "broadcast.cancelled", "broadcast", broadcastId, Map.of());
        return ResponseEntity.ok(Map.of("status", "cancelled", "broadcastId", broadcastId));
    }

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
        auditService.record(teacher, "broadcast.section_sent", "broadcast", "", Map.of(
                "title", req.getTitle().trim(), "recipientCount", recipientCount));

        return ResponseEntity.ok(Map.of("recipientCount", recipientCount));
    }

    private ResponseEntity<?> validateSchoolBroadcast(BroadcastRequest req) {
        if (req.getTitle() == null || req.getTitle().isBlank() || req.getBody() == null || req.getBody().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "title and body are required"));
        }
        if (req.getTitle().trim().length() > 120) {
            return ResponseEntity.badRequest().body(Map.of("error", "title must be 120 characters or fewer"));
        }
        if (req.getBody().trim().length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("error", "body must be 2000 characters or fewer"));
        }
        List<String> audience = req.getAudience();
        if (audience == null || audience.isEmpty() || !VALID_AUDIENCE_ROLES.containsAll(audience)) {
            return ResponseEntity.badRequest().body(Map.of("error", "audience must be a non-empty subset of ['teacher','parent']"));
        }
        return null;
    }

    public static class BroadcastRequest {
        @NotBlank private String title;
        @NotBlank private String body;
        private List<String> audience = new ArrayList<>();

        public String getTitle() { return title; }
        public void setTitle(String v) { this.title = v; }
        public String getBody() { return body; }
        public void setBody(String v) { this.body = v; }
        public List<String> getAudience() { return audience; }
        public void setAudience(List<String> v) { this.audience = v; }
    }

    public static class ScheduledBroadcastRequest extends BroadcastRequest {
        private String scheduledAt;
        public String getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(String scheduledAt) { this.scheduledAt = scheduledAt; }
    }
}

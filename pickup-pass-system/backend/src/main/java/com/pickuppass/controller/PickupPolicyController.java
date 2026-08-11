package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * School-level pickup policy. The default is deliberately unrestricted so the
 * original PickupPass behavior remains unchanged: an authorized guardian can
 * present a valid QR without joining a queue or checking in first.
 */
@RestController
@RequestMapping("/api/school-admin/pickup-policy")
public class PickupPolicyController {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final Firestore firestore;
    private final AuditService auditService;
    private final String schoolTimeZone;

    public PickupPolicyController(Firestore firestore,
                                  AuditService auditService,
                                  @org.springframework.beans.factory.annotation.Value("${app.school-time-zone:Asia/Manila}") String schoolTimeZone) {
        this.firestore = firestore;
        this.auditService = auditService;
        this.schoolTimeZone = schoolTimeZone;
    }

    @GetMapping
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> getPolicy(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        DocumentSnapshot school = firestore.collection("schools").document(admin.getSchoolId()).get().get();
        if (!school.exists()) throw new NotFoundException("School not found");

        @SuppressWarnings("unchecked")
        Map<String, Object> stored = (Map<String, Object>) school.get("pickupPolicy");
        return ResponseEntity.ok(normalize(stored));
    }

    @PutMapping
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> updatePolicy(@Valid @RequestBody UpdatePickupPolicyRequest req,
                                          @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        String mode = req.getMode().trim().toLowerCase();
        if (!mode.equals("unrestricted") && !mode.equals("time_window")) {
            return ResponseEntity.badRequest().body(Map.of("error", "mode must be unrestricted or time_window"));
        }

        String earliest = nullableTrim(req.getEarliestPickupTime());
        String latest = nullableTrim(req.getLatestPickupTime());

        if (mode.equals("time_window")) {
            if (earliest == null || latest == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Start and end pickup times are required for a time window"));
            }
            try {
                LocalTime start = LocalTime.parse(earliest, HH_MM);
                LocalTime end = LocalTime.parse(latest, HH_MM);
                if (!start.isBefore(end)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Pickup start time must be earlier than the end time"));
                }
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "Pickup times must use 24-hour HH:mm format"));
            }
        } else {
            earliest = null;
            latest = null;
        }

        Map<String, Object> policy = new HashMap<>();
        policy.put("mode", mode);
        policy.put("allowManualOverride", req.isAllowManualOverride());
        if (earliest != null) policy.put("earliestPickupTime", earliest);
        if (latest != null) policy.put("latestPickupTime", latest);

        var schoolRef = firestore.collection("schools").document(admin.getSchoolId());
        if (!schoolRef.get().get().exists()) throw new NotFoundException("School not found");

        schoolRef.update(
                "pickupPolicy", policy,
                "pickupPolicyUpdatedAt", FieldValue.serverTimestamp(),
                "pickupPolicyUpdatedBy", admin.getUid()
        ).get();

        auditService.record(admin, "school.pickup_policy_updated", "school", admin.getSchoolId(), policy);
        return ResponseEntity.ok(normalize(policy));
    }

    private Map<String, Object> normalize(Map<String, Object> stored) {
        String mode = stored != null && stored.get("mode") != null
                ? String.valueOf(stored.get("mode")) : "unrestricted";
        boolean allowManualOverride = stored == null || !Boolean.FALSE.equals(stored.get("allowManualOverride"));
        String earliest = stored != null && stored.get("earliestPickupTime") != null
                ? String.valueOf(stored.get("earliestPickupTime")) : null;
        String latest = stored != null && stored.get("latestPickupTime") != null
                ? String.valueOf(stored.get("latestPickupTime")) : null;

        Map<String, Object> result = new HashMap<>();
        result.put("mode", mode);
        result.put("allowManualOverride", allowManualOverride);
        result.put("timeZone", schoolTimeZone);
        result.put("earliestPickupTime", earliest == null ? "" : earliest);
        result.put("latestPickupTime", latest == null ? "" : latest);
        return result;
    }

    private static String nullableTrim(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public static class UpdatePickupPolicyRequest {
        @NotBlank private String mode;
        private String earliestPickupTime;
        private String latestPickupTime;
        private boolean allowManualOverride = true;

        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
        public String getEarliestPickupTime() { return earliestPickupTime; }
        public void setEarliestPickupTime(String earliestPickupTime) { this.earliestPickupTime = earliestPickupTime; }
        public String getLatestPickupTime() { return latestPickupTime; }
        public void setLatestPickupTime(String latestPickupTime) { this.latestPickupTime = latestPickupTime; }
        public boolean isAllowManualOverride() { return allowManualOverride; }
        public void setAllowManualOverride(boolean allowManualOverride) { this.allowManualOverride = allowManualOverride; }
    }
}

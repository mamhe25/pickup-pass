package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** School-admin workflow for guardian identity verification and suspension. */
@RestController
@RequestMapping("/api/school-admin/guardian-verification")
@PreAuthorize("hasRole('school_admin')")
public class GuardianVerificationController {

    private static final Set<String> ALLOWED_STATUSES = Set.of("pending", "verified", "suspended");

    private final Firestore firestore;
    private final AuditService auditService;

    public GuardianVerificationController(Firestore firestore, AuditService auditService) {
        this.firestore = firestore;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        String schoolId = admin.getSchoolId();
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        boolean required = Boolean.TRUE.equals(school.getBoolean("guardianVerificationRequired"));

        Map<String, Set<String>> studentNamesByGuardian = new HashMap<>();
        for (QueryDocumentSnapshot student : firestore.collection("students")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            Object raw = student.get("guardianUids");
            if (!(raw instanceof List<?> list)) continue;
            String studentName = studentName(student);
            for (Object uidObj : list) {
                if (uidObj == null) continue;
                String uid = String.valueOf(uidObj);
                studentNamesByGuardian.computeIfAbsent(uid, k -> new TreeSet<>()).add(studentName);
            }
        }

        List<Map<String, Object>> guardians = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : studentNamesByGuardian.entrySet()) {
            DocumentSnapshot user = firestore.collection("users").document(entry.getKey()).get().get();
            if (!user.exists()) continue;
            if (!schoolId.equals(user.getString("schoolId"))) continue;
            if (!"parent".equalsIgnoreCase(String.valueOf(user.getString("role")))) continue;

            String status = normalizedStatus(user.getString("guardianVerificationStatus"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("uid", entry.getKey());
            item.put("displayName", displayName(user));
            item.put("email", Objects.toString(user.getString("email"), ""));
            item.put("status", status);
            item.put("studentNames", new ArrayList<>(entry.getValue()));
            item.put("verificationReason", Objects.toString(user.getString("guardianVerificationReason"), ""));
            item.put("verifiedAt", user.getTimestamp("guardianVerifiedAt") == null ? null : user.getTimestamp("guardianVerifiedAt").toDate());
            guardians.add(item);
        }
        guardians.sort(Comparator.comparing(g -> String.valueOf(g.get("displayName")).toLowerCase(Locale.ROOT)));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("verificationRequired", required);
        body.put("guardians", guardians);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/policy")
    public ResponseEntity<?> updatePolicy(
            @RequestBody VerificationPolicyRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        firestore.collection("schools").document(admin.getSchoolId()).update(
                "guardianVerificationRequired", req.required,
                "guardianVerificationUpdatedAt", FieldValue.serverTimestamp(),
                "guardianVerificationUpdatedBy", admin.getUid()
        ).get();
        auditService.record(admin, "guardian.verification_policy_updated", "school", admin.getSchoolId(),
                Map.of("required", req.required));
        return ResponseEntity.ok(Map.of("verificationRequired", req.required));
    }

    @PutMapping("/{guardianUid}")
    public ResponseEntity<?> updateStatus(
            @PathVariable String guardianUid,
            @Valid @RequestBody GuardianStatusRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        String schoolId = admin.getSchoolId();
        DocumentSnapshot user = firestore.collection("users").document(guardianUid).get().get();
        if (!user.exists() || !schoolId.equals(user.getString("schoolId")) || !"parent".equalsIgnoreCase(user.getString("role"))) {
            throw new NotFoundException("Guardian not found");
        }

        String status = normalizedRequestedStatus(req.status);
        Map<String, Object> updates = new HashMap<>();
        updates.put("guardianVerificationStatus", status);
        updates.put("guardianVerificationReason", req.reason == null ? "" : req.reason.trim());
        updates.put("guardianVerificationUpdatedAt", FieldValue.serverTimestamp());
        updates.put("guardianVerificationUpdatedBy", admin.getUid());
        if ("verified".equals(status)) {
            updates.put("guardianVerifiedAt", FieldValue.serverTimestamp());
            updates.put("guardianVerifiedBy", admin.getUid());
        }
        firestore.collection("users").document(guardianUid).update(updates).get();

        int invalidatedTokens = 0;
        if ("suspended".equals(status) || "pending".equals(status)) {
            WriteBatch batch = firestore.batch();
            for (QueryDocumentSnapshot token : firestore.collection("pickupTokens")
                    .whereEqualTo("parentUid", guardianUid)
                    .whereEqualTo("used", false)
                    .get().get().getDocuments()) {
                if (!schoolId.equals(token.getString("schoolId"))) continue;
                batch.update(token.getReference(),
                        "used", true,
                        "invalidatedReason", "guardian_verification_" + status,
                        "invalidatedAt", FieldValue.serverTimestamp());
                invalidatedTokens++;
            }
            if (invalidatedTokens > 0) batch.commit().get();
        }

        auditService.record(admin, "guardian.verification_status_changed", "guardian", guardianUid, Map.of(
                "status", status,
                "reason", req.reason == null ? "" : req.reason.trim(),
                "invalidatedTokens", invalidatedTokens));

        return ResponseEntity.ok(Map.of("status", status, "invalidatedTokens", invalidatedTokens));
    }

    private static String normalizedRequestedStatus(String value) {
        String status = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status must be pending, verified, or suspended");
        }
        return status;
    }

    private static String normalizedStatus(String value) {
        if (value == null || value.isBlank()) return "verified"; // legacy guardians remain usable
        String status = value.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_STATUSES.contains(status) ? status : "pending";
    }

    private static String studentName(DocumentSnapshot student) {
        String first = Objects.toString(student.getString("firstName"), "").trim();
        String last = Objects.toString(student.getString("lastName"), "").trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? student.getId() : full;
    }

    private static String displayName(DocumentSnapshot user) {
        String display = Objects.toString(user.getString("displayName"), "").trim();
        if (!display.isBlank()) return display;
        String first = Objects.toString(user.getString("firstName"), "").trim();
        String last = Objects.toString(user.getString("lastName"), "").trim();
        String full = (first + " " + last).trim();
        return full.isBlank() ? Objects.toString(user.getString("email"), user.getId()) : full;
    }

    public static class VerificationPolicyRequest {
        public boolean required;
        public boolean isRequired() { return required; }
        public void setRequired(boolean required) { this.required = required; }
    }

    public static class GuardianStatusRequest {
        @NotBlank public String status;
        public String reason;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}

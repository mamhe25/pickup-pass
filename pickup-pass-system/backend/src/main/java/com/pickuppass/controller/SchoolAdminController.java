package com.pickuppass.controller;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FieldValue;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.SchoolLogoService;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.StaffProvisioningService;
import com.pickuppass.service.TenantUsageService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Everything a school_admin can do for their own school: branding, staff invites, and section assignment. */
@RestController
@RequestMapping("/api/school-admin")
public class SchoolAdminController {

    private final Firestore firestore;
    private final SchoolLogoService logoService;
    private final StaffProvisioningService staffProvisioningService;
    private final FirebaseAuth firebaseAuth;
    private final AuditService auditService;
    private final TenantUsageService tenantUsageService;

    public SchoolAdminController(
            Firestore firestore, SchoolLogoService logoService, StaffProvisioningService staffProvisioningService,
            FirebaseAuth firebaseAuth, AuditService auditService, TenantUsageService tenantUsageService) {
        this.firestore = firestore;
        this.logoService = logoService;
        this.staffProvisioningService = staffProvisioningService;
        this.firebaseAuth = firebaseAuth;
        this.auditService = auditService;
        this.tenantUsageService = tenantUsageService;
    }

    /** A school admin can update their own school's logo — no schoolId param, always their own claim. */
    @PostMapping(value = "/logo", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> uploadLogo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {
        try {
            String logoUrl = logoService.uploadLogo(schoolAdmin.getSchoolId(), file);
            auditService.record(schoolAdmin, "school.logo_updated", "school", schoolAdmin.getSchoolId(), Map.of());
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
     * (see MasterAdminController.createStaff). No catch-all here — see the
     * comment on MasterAdminController.createStaff for why.
     */
    @PostMapping("/staff")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> inviteTeacher(
            @RequestBody InviteTeacherRequest req,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {

        if (req.getLastName() == null || req.getLastName().isBlank()
                || req.getFirstName() == null || req.getFirstName().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "lastName and firstName are required"));
        }

        List<Map<String, String>> assignedSections;
        try {
            assignedSections = validateNewAssignments(
                    schoolAdmin.getSchoolId(),
                    req.getAssignedSections());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        tenantUsageService.reserve(schoolAdmin.getSchoolId(), TenantUsageService.STAFF, 1);
        StaffProvisioningService.StaffCreationResult result;
        try {
            result = staffProvisioningService.createStaffAccount(
                    req.getEmail(), req.getLastName(), req.getFirstName(),
                    req.getMiddleInitial(), req.getSuffix(), "teacher",
                    schoolAdmin.getSchoolId(), assignedSections);
        } catch (Exception e) {
            tenantUsageService.release(schoolAdmin.getSchoolId(), TenantUsageService.STAFF, 1);
            throw e;
        }

        auditService.record(schoolAdmin, "staff.invited", "user", result.getUid(),
                Map.of(
                        "role", "teacher",
                        "email", req.getEmail(),
                        "assignedSections", assignedSections));
        return ResponseEntity.ok(Map.of(
                "uid", result.getUid(),
                "role", "teacher",
                "emailSent", result.isEmailSent(),
                "assignedSections", assignedSections
        ));
    }

    /** Lists teachers at the admin's school, including their current section assignments — for the section-editor UI. */
    @GetMapping("/staff")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> listStaff(@AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {
        List<QueryDocumentSnapshot> docs = firestore.collection("users")
                .whereEqualTo("schoolId", schoolAdmin.getSchoolId())
                .whereEqualTo("role", "teacher")
                .get().get().getDocuments();

        List<Map<String, Object>> teachers = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            Map<String, Object> t = new HashMap<>();
            t.put("uid", doc.getId());
            t.put("displayName", doc.getString("displayName"));
            t.put("email", doc.getString("email"));
            Boolean isActive = doc.getBoolean("isActive");
            t.put("isActive", isActive == null || isActive);
            t.put("assignedSections", doc.get("assignedSections") != null ? doc.get("assignedSections") : List.of());
            teachers.add(t);
        }
        return ResponseEntity.ok(Map.of("teachers", teachers));
    }

    /**
     * Sets (replaces, not merges — the UI sends the full desired list each
     * time) a teacher's assigned grade/section pairs. This is what scopes
     * "broadcast to my section" on the teacher side — see
     * BroadcastService.broadcastToSection, which reads this exact field.
     */
    @PutMapping("/staff/{uid}/sections")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> updateTeacherSections(
            @PathVariable String uid,
            @RequestBody UpdateSectionsRequest req,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {

        var teacherDoc = firestore.collection("users").document(uid).get().get();
        if (!teacherDoc.exists() || !schoolAdmin.getSchoolId().equals(teacherDoc.getString("schoolId"))
                || !"teacher".equals(teacherDoc.getString("role"))) {
            throw new NotFoundException("Teacher not found in your school");
        }

        List<QueryDocumentSnapshot> currentSections =
                currentActiveGradeSections(schoolAdmin.getSchoolId());
        Set<String> existingKeys = existingAssignmentKeys(
                teacherDoc.get("assignedSections"));
        Set<String> seen = new HashSet<>();

        List<Map<String, String>> sections = new ArrayList<>();
        for (SectionEntry s : req.getSections()) {
            if (s.getGrade() == null || s.getGrade().isBlank()
                    || s.getSection() == null || s.getSection().isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Each section needs both a grade and a section"));
            }

            String grade = s.getGrade().trim();
            String section = s.getSection().trim();
            String key = sectionKey(grade, section);

            if (!seen.add(key)) {
                continue;
            }

            boolean validCurrent = matchesConfiguredSection(
                    currentSections,
                    grade,
                    section);

            // Existing legacy assignments may remain until the admin removes
            // them, but a client cannot introduce a new assignment outside the
            // active current-year structure.
            if (!validCurrent && !existingKeys.contains(key)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error",
                        "New teacher assignments must use an active grade/section from the current academic year"));
            }

            sections.add(Map.of("grade", grade, "section", section));
        }

        firestore.collection("users").document(uid).update("assignedSections", sections, "updatedAt", FieldValue.serverTimestamp()).get();
        auditService.record(schoolAdmin, "staff.sections_updated", "user", uid, Map.of("assignedSections", sections));

        return ResponseEntity.ok(Map.of("uid", uid, "assignedSections", sections));
    }


    /** Deactivate/reactivate a teacher account and revoke existing sessions when deactivating. */
    @PutMapping("/staff/{uid}/status")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> setTeacherStatus(
            @PathVariable String uid,
            @RequestBody StaffStatusRequest req,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {
        var teacherDoc = firestore.collection("users").document(uid).get().get();
        if (!teacherDoc.exists() || !schoolAdmin.getSchoolId().equals(teacherDoc.getString("schoolId"))
                || !"teacher".equals(teacherDoc.getString("role"))) {
            throw new NotFoundException("Teacher not found in your school");
        }
        boolean active = req.isActive();
        boolean wasActive = teacherDoc.getBoolean("isActive") == null || Boolean.TRUE.equals(teacherDoc.getBoolean("isActive"));
        boolean activating = active && !wasActive;
        boolean deactivating = !active && wasActive;
        if (activating) tenantUsageService.reserve(schoolAdmin.getSchoolId(), TenantUsageService.STAFF, 1);
        try {
            firebaseAuth.updateUser(new UserRecord.UpdateRequest(uid).setDisabled(!active));
            if (!active) firebaseAuth.revokeRefreshTokens(uid);
            firestore.collection("users").document(uid).update(
                    "isActive", active,
                    "statusUpdatedAt", FieldValue.serverTimestamp(),
                    "statusUpdatedBy", schoolAdmin.getUid()).get();
        } catch (Exception e) {
            if (activating) tenantUsageService.release(schoolAdmin.getSchoolId(), TenantUsageService.STAFF, 1);
            throw e;
        }
        if (deactivating) tenantUsageService.release(schoolAdmin.getSchoolId(), TenantUsageService.STAFF, 1);
        auditService.record(schoolAdmin, active ? "staff.reactivated" : "staff.deactivated", "user", uid, Map.of());
        return ResponseEntity.ok(Map.of("uid", uid, "isActive", active));
    }

    /** Immediately invalidates a teacher's refresh tokens (lost/stolen device or forced logout). */
    @PostMapping("/staff/{uid}/revoke-sessions")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> revokeTeacherSessions(
            @PathVariable String uid,
            @AuthenticationPrincipal FirebaseUserDetails schoolAdmin) throws Exception {
        var teacherDoc = firestore.collection("users").document(uid).get().get();
        if (!teacherDoc.exists() || !schoolAdmin.getSchoolId().equals(teacherDoc.getString("schoolId"))
                || !"teacher".equals(teacherDoc.getString("role"))) {
            throw new NotFoundException("Teacher not found in your school");
        }
        firebaseAuth.revokeRefreshTokens(uid);
        auditService.record(schoolAdmin, "staff.sessions_revoked", "user", uid, Map.of());
        return ResponseEntity.ok(Map.of("uid", uid, "status", "sessions_revoked"));
    }

    private List<Map<String, String>> validateNewAssignments(
            String schoolId,
            List<SectionEntry> requested) throws Exception {

        if (requested == null || requested.isEmpty()) {
            // Kept optional for compatibility with existing web/admin clients.
            // The Android production invite flow always requires at least one.
            return List.of();
        }

        List<QueryDocumentSnapshot> currentSections =
                currentActiveGradeSections(schoolId);
        if (currentSections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Set a current academic year with at least one active grade/section before assigning a teacher");
        }

        List<Map<String, String>> normalized = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (SectionEntry entry : requested) {
            String grade = entry == null ? "" : safe(entry.getGrade());
            String section = entry == null ? "" : safe(entry.getSection());

            if (grade.isBlank() || section.isBlank()) {
                throw new IllegalArgumentException(
                        "Each teacher assignment needs both a grade and a section");
            }

            if (!matchesConfiguredSection(currentSections, grade, section)) {
                throw new IllegalArgumentException(
                        "Teacher assignments must use an active grade/section from the current academic year");
            }

            if (seen.add(sectionKey(grade, section))) {
                normalized.add(Map.of(
                        "grade", grade,
                        "section", section));
            }
        }

        return normalized;
    }

    private List<QueryDocumentSnapshot> currentActiveGradeSections(
            String schoolId) throws Exception {

        String currentYearId = "";
        for (QueryDocumentSnapshot year : firestore.collection("academicYears")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments()) {
            if (Boolean.TRUE.equals(year.getBoolean("isCurrent"))
                    && !"archived".equalsIgnoreCase(safe(year.getString("status")))) {
                currentYearId = year.getId();
                break;
            }
        }

        if (currentYearId.isBlank()) {
            return List.of();
        }

        List<QueryDocumentSnapshot> result = new ArrayList<>();
        for (QueryDocumentSnapshot section : firestore.collection("gradeSections")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments()) {
            if (currentYearId.equals(safe(section.getString("academicYearId")))
                    && !Boolean.FALSE.equals(section.getBoolean("active"))) {
                result.add(section);
            }
        }
        return result;
    }

    private boolean matchesConfiguredSection(
            List<QueryDocumentSnapshot> configured,
            String grade,
            String section) {
        return configured.stream().anyMatch(doc ->
                grade.equalsIgnoreCase(safe(doc.getString("gradeLevel")))
                        && section.equalsIgnoreCase(safe(doc.getString("sectionName"))));
    }

    private Set<String> existingAssignmentKeys(Object raw) {
        Set<String> result = new HashSet<>();
        if (!(raw instanceof List<?> list)) {
            return result;
        }

        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String grade = safeObject(map.get("grade"));
            String section = safeObject(map.get("section"));
            if (!grade.isBlank() && !section.isBlank()) {
                result.add(sectionKey(grade, section));
            }
        }
        return result;
    }

    private static String sectionKey(String grade, String section) {
        return safe(grade).toLowerCase(Locale.ROOT) + "||" + safe(section).toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeObject(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static class InviteTeacherRequest {
        @NotBlank private String email;
        @NotBlank private String lastName;
        @NotBlank private String firstName;
        private String middleInitial;
        private String suffix;
        private List<SectionEntry> assignedSections = new ArrayList<>();

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getLastName() { return lastName; }
        public void setLastName(String v) { this.lastName = v; }
        public String getFirstName() { return firstName; }
        public void setFirstName(String v) { this.firstName = v; }
        public String getMiddleInitial() { return middleInitial; }
        public void setMiddleInitial(String v) { this.middleInitial = v; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String v) { this.suffix = v; }
        public List<SectionEntry> getAssignedSections() { return assignedSections; }
        public void setAssignedSections(List<SectionEntry> v) {
            this.assignedSections = v == null ? new ArrayList<>() : v;
        }
    }

    public static class StaffStatusRequest {
        private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static class SectionEntry {
        private String grade;
        private String section;

        public String getGrade() { return grade; }
        public void setGrade(String v) { this.grade = v; }
        public String getSection() { return section; }
        public void setSection(String v) { this.section = v; }
    }

    public static class UpdateSectionsRequest {
        private List<SectionEntry> sections = new ArrayList<>();

        public List<SectionEntry> getSections() { return sections; }
        public void setSections(List<SectionEntry> v) { this.sections = v; }
    }
}

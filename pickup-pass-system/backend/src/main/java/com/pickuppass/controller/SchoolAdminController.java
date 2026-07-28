package com.pickuppass.controller;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.SchoolLogoService;
import com.pickuppass.service.StaffProvisioningService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything a school_admin can do for their own school: branding, staff invites, and section assignment. */
@RestController
@RequestMapping("/api/school-admin")
public class SchoolAdminController {

    private final Firestore firestore;
    private final SchoolLogoService logoService;
    private final StaffProvisioningService staffProvisioningService;

    public SchoolAdminController(
            Firestore firestore, SchoolLogoService logoService, StaffProvisioningService staffProvisioningService) {
        this.firestore = firestore;
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

        StaffProvisioningService.StaffCreationResult result = staffProvisioningService.createStaffAccount(
                req.getEmail(), req.getLastName(), req.getFirstName(),
                req.getMiddleInitial(), req.getSuffix(), "teacher", schoolAdmin.getSchoolId());

        return ResponseEntity.ok(Map.of(
                "uid", result.getUid(),
                "role", "teacher",
                "emailSent", result.isEmailSent()
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

        List<Map<String, String>> sections = new ArrayList<>();
        for (SectionEntry s : req.getSections()) {
            if (s.getGrade() == null || s.getGrade().isBlank() || s.getSection() == null || s.getSection().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Each section needs both a grade and a section"));
            }
            sections.add(Map.of("grade", s.getGrade().trim(), "section", s.getSection().trim()));
        }

        firestore.collection("users").document(uid).update("assignedSections", sections).get();

        return ResponseEntity.ok(Map.of("uid", uid, "assignedSections", sections));
    }

    public static class InviteTeacherRequest {
        @NotBlank private String email;
        @NotBlank private String lastName;
        @NotBlank private String firstName;
        private String middleInitial;
        private String suffix;

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

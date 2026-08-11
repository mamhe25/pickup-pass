package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.TenantDataExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/school-admin/data-export")
@PreAuthorize("hasRole('school_admin')")
public class SchoolDataExportController {

    private final Firestore firestore;
    private final TenantDataExportService exportService;

    public SchoolDataExportController(Firestore firestore, TenantDataExportService exportService) {
        this.firestore = firestore;
        this.exportService = exportService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        DocumentSnapshot school = firestore.collection("schools").document(admin.getSchoolId()).get().get();
        if (!school.exists()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "School not found"));
        boolean enabled = Boolean.TRUE.equals(school.getBoolean("selfServiceDataExportEnabled"));
        return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "storageMode", "direct_download",
                "cloudCopyCreated", false,
                "platformBackupControlledBy", "master_admin",
                "message", enabled
                        ? "Your school can create a tenant-scoped data export. The archive is saved directly to your device and is not stored by PickupPass."
                        : "Self-service school data export is disabled by the platform owner."
        ));
    }

    @GetMapping("/download")
    public ResponseEntity<?> download(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        DocumentSnapshot school = firestore.collection("schools").document(admin.getSchoolId()).get().get();
        if (!school.exists()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "School not found"));
        if (!Boolean.TRUE.equals(school.getBoolean("selfServiceDataExportEnabled"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Self-service data export is not enabled for this school"));
        }
        TenantDataExportService.ExportResult result = exportService.exportSchool(admin.getSchoolId(), admin);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(result.fileName(), StandardCharsets.UTF_8).build());
        headers.setContentLength(result.bytes().length);
        headers.set("Cache-Control", "no-store");
        return new ResponseEntity<>(result.bytes(), headers, HttpStatus.OK);
    }
}

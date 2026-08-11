package com.pickuppass.controller;

import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/school-admin/audit-events")
public class AuditController {
    private final Firestore firestore;

    public AuditController(Firestore firestore) {
        this.firestore = firestore;
    }

    @GetMapping
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> recentAuditEvents(
            @AuthenticationPrincipal FirebaseUserDetails admin,
            @RequestParam(defaultValue = "100") int limit) throws Exception {
        int safeLimit = Math.max(1, Math.min(limit, 250));
        List<QueryDocumentSnapshot> docs = firestore.collection("schools").document(admin.getSchoolId())
                .collection("auditEvents")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(safeLimit)
                .get().get().getDocuments();

        List<Map<String, Object>> events = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            Map<String, Object> event = new HashMap<>(doc.getData());
            event.put("id", doc.getId());
            events.add(event);
        }
        return ResponseEntity.ok(Map.of("events", events));
    }
}

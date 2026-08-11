package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.SubscriptionFeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/school-admin/staff-gates")
@PreAuthorize("hasRole('school_admin')")
public class StaffPickupGateController {
    private final Firestore firestore;
    private final AuditService auditService;
    private final SubscriptionFeatureService subscriptionFeatureService;

    public StaffPickupGateController(Firestore firestore, AuditService auditService,
                                     SubscriptionFeatureService subscriptionFeatureService) {
        this.firestore = firestore;
        this.auditService = auditService;
        this.subscriptionFeatureService = subscriptionFeatureService;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        List<Map<String,Object>> gates = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("pickupGates")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            if (Boolean.FALSE.equals(doc.getBoolean("active"))) continue;
            String campusId = safe(doc.getString("campusId"));
            String campusName = safe(doc.getString("campusName"));
            if (!campusId.isBlank()) {
                DocumentSnapshot campus = firestore.collection("campuses").document(campusId).get().get();
                if (!campus.exists() || Boolean.FALSE.equals(campus.getBoolean("active"))) continue;
                campusName = safe(campus.getString("name"));
            }
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("id", doc.getId());
            m.put("name", safe(doc.getString("name")));
            m.put("campusId", campusId);
            m.put("campusName", campusName);
            gates.add(m);
        }
        gates.sort(Comparator.comparing(m -> (safeObj(m.get("campusName")) + " " + safeObj(m.get("name"))).toLowerCase(Locale.ROOT)));

        Set<String> activeGateIds = new HashSet<>();
        for (Map<String,Object> g : gates) activeGateIds.add(String.valueOf(g.get("id")));

        List<Map<String,Object>> staff = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("users")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            String role = safe(doc.getString("role"));
            if (!role.equals("teacher") && !role.equals("school_admin")) continue;
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("uid", doc.getId());
            m.put("displayName", safe(doc.getString("displayName")));
            m.put("email", safe(doc.getString("email")));
            m.put("role", role);
            m.put("isActive", doc.getBoolean("isActive") == null || Boolean.TRUE.equals(doc.getBoolean("isActive")));
            List<String> assigned = stringList(doc.get("assignedPickupGateIds"));
            assigned.removeIf(id -> !activeGateIds.contains(id));
            m.put("assignedPickupGateIds", assigned);
            m.put("allGates", assigned.isEmpty());
            staff.add(m);
        }
        staff.sort(Comparator.comparing(m -> safeObj(m.get("displayName")).toLowerCase(Locale.ROOT)));
        return ResponseEntity.ok(Map.of("staff", staff, "gates", gates));
    }

    @PutMapping("/{uid}")
    public ResponseEntity<?> update(@PathVariable String uid,
                                    @RequestBody AssignmentRequest req,
                                    @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        subscriptionFeatureService.requireFeature(admin.getSchoolId(), "staff_gate_restrictions");
        DocumentSnapshot user = firestore.collection("users").document(uid).get().get();
        if (!user.exists() || !admin.getSchoolId().equals(user.getString("schoolId"))) {
            throw new NotFoundException("Staff account not found in your school");
        }
        String role = safe(user.getString("role"));
        if (!role.equals("teacher") && !role.equals("school_admin")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only teacher and school admin accounts can be assigned to pickup gates"));
        }

        LinkedHashSet<String> requested = new LinkedHashSet<>();
        if (req.getPickupGateIds() != null) {
            for (String id : req.getPickupGateIds()) if (id != null && !id.isBlank()) requested.add(id.trim());
        }
        if (requested.size() > 50) {
            return ResponseEntity.badRequest().body(Map.of("error", "A staff account cannot be assigned to more than 50 pickup gates"));
        }

        for (String gateId : requested) {
            DocumentSnapshot gate = firestore.collection("pickupGates").document(gateId).get().get();
            if (!gate.exists() || !admin.getSchoolId().equals(gate.getString("schoolId")) || Boolean.FALSE.equals(gate.getBoolean("active"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "One or more pickup gates are invalid or inactive"));
            }
            String campusId = safe(gate.getString("campusId"));
            if (!campusId.isBlank()) {
                DocumentSnapshot campus = firestore.collection("campuses").document(campusId).get().get();
                if (!campus.exists() || Boolean.FALSE.equals(campus.getBoolean("active"))) {
                    return ResponseEntity.badRequest().body(Map.of("error", "One or more pickup gates belong to an inactive campus"));
                }
            }
        }

        List<String> assigned = new ArrayList<>(requested);
        user.getReference().update(
                "assignedPickupGateIds", assigned,
                "pickupGateAssignmentUpdatedAt", FieldValue.serverTimestamp(),
                "pickupGateAssignmentUpdatedBy", admin.getUid()).get();

        auditService.record(admin, "staff.pickup_gates_updated", "user", uid, Map.of(
                "assignedPickupGateIds", assigned,
                "allGates", assigned.isEmpty(),
                "staffRole", role));
        return ResponseEntity.ok(Map.of("uid", uid, "assignedPickupGateIds", assigned, "allGates", assigned.isEmpty()));
    }

    private static List<String> stringList(Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) for (Object v : list) if (v != null && !String.valueOf(v).isBlank()) out.add(String.valueOf(v));
        return out;
    }
    private static String safe(String v) { return v == null ? "" : v.trim(); }
    private static String safeObj(Object v) { return v == null ? "" : String.valueOf(v).trim(); }

    public static class AssignmentRequest {
        private List<String> pickupGateIds = new ArrayList<>();
        public List<String> getPickupGateIds() { return pickupGateIds; }
        public void setPickupGateIds(List<String> value) { pickupGateIds = value == null ? new ArrayList<>() : value; }
    }
}

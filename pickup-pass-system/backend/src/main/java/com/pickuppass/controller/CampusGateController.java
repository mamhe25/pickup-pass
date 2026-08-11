package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.exception.NotFoundException;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/school-admin/campus-gates")
@PreAuthorize("hasRole('school_admin')")
public class CampusGateController {
    private final Firestore firestore;
    private final AuditService auditService;

    public CampusGateController(Firestore firestore, AuditService auditService) {
        this.firestore = firestore;
        this.auditService = auditService;
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        List<Map<String,Object>> campuses = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("campuses")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            campuses.add(campusMap(doc));
        }
        campuses.sort(Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase(Locale.ROOT)));

        List<Map<String,Object>> gates = new ArrayList<>();
        for (QueryDocumentSnapshot doc : firestore.collection("pickupGates")
                .whereEqualTo("schoolId", admin.getSchoolId()).get().get().getDocuments()) {
            gates.add(gateMap(doc));
        }
        gates.sort(Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase(Locale.ROOT)));
        return ResponseEntity.ok(Map.of("campuses", campuses, "gates", gates));
    }

    @PostMapping("/campuses")
    public ResponseEntity<?> createCampus(@Valid @RequestBody CampusRequest req,
                                           @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        String name = req.getName().trim();
        ensureUniqueCampus(admin.getSchoolId(), name, null);
        var ref = firestore.collection("campuses").document();
        Map<String,Object> data = new HashMap<>();
        data.put("schoolId", admin.getSchoolId());
        data.put("name", name);
        data.put("address", safe(req.getAddress()));
        data.put("active", true);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("createdBy", admin.getUid());
        ref.set(data).get();
        auditService.record(admin, "campus.created", "campus", ref.getId(), Map.of("name", name));
        return ResponseEntity.ok(Map.of("id", ref.getId(), "name", name, "active", true));
    }

    @PutMapping("/campuses/{id}/status")
    public ResponseEntity<?> setCampusStatus(@PathVariable String id,
                                              @RequestBody ActiveRequest req,
                                              @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        DocumentSnapshot campus = firestore.collection("campuses").document(id).get().get();
        if (!campus.exists() || !admin.getSchoolId().equals(campus.getString("schoolId"))) {
            throw new NotFoundException("Campus not found in your school");
        }
        campus.getReference().update("active", req.isActive(), "updatedAt", FieldValue.serverTimestamp(), "updatedBy", admin.getUid()).get();
        if (!req.isActive()) {
            for (QueryDocumentSnapshot gate : firestore.collection("pickupGates")
                    .whereEqualTo("schoolId", admin.getSchoolId())
                    .whereEqualTo("campusId", id).get().get().getDocuments()) {
                gate.getReference().update("active", false, "updatedAt", FieldValue.serverTimestamp(), "updatedBy", admin.getUid()).get();
            }
        }
        auditService.record(admin, req.isActive() ? "campus.reactivated" : "campus.archived", "campus", id,
                Map.of("name", safe(campus.getString("name"))));
        return ResponseEntity.ok(Map.of("id", id, "active", req.isActive()));
    }

    @PostMapping("/gates")
    public ResponseEntity<?> createGate(@Valid @RequestBody GateRequest req,
                                         @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        String campusId = safe(req.getCampusId());
        String campusName = "";
        if (!campusId.isBlank()) {
            DocumentSnapshot campus = firestore.collection("campuses").document(campusId).get().get();
            if (!campus.exists() || !admin.getSchoolId().equals(campus.getString("schoolId")) || Boolean.FALSE.equals(campus.getBoolean("active"))) {
                throw new NotFoundException("Active campus not found in your school");
            }
            campusName = safe(campus.getString("name"));
        }
        String name = req.getName().trim();
        ensureUniqueGate(admin.getSchoolId(), campusId, name, null);
        var ref = firestore.collection("pickupGates").document();
        Map<String,Object> data = new HashMap<>();
        data.put("schoolId", admin.getSchoolId());
        data.put("campusId", campusId);
        data.put("campusName", campusName);
        data.put("name", name);
        data.put("description", safe(req.getDescription()));
        data.put("active", true);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("createdBy", admin.getUid());
        ref.set(data).get();
        auditService.record(admin, "pickup_gate.created", "pickupGate", ref.getId(), Map.of("name", name, "campusId", campusId));
        return ResponseEntity.ok(Map.of("id", ref.getId(), "name", name, "active", true));
    }

    @PutMapping("/gates/{id}/status")
    public ResponseEntity<?> setGateStatus(@PathVariable String id,
                                            @RequestBody ActiveRequest req,
                                            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        DocumentSnapshot gate = firestore.collection("pickupGates").document(id).get().get();
        if (!gate.exists() || !admin.getSchoolId().equals(gate.getString("schoolId"))) {
            throw new NotFoundException("Pickup gate not found in your school");
        }
        if (req.isActive() && !safe(gate.getString("campusId")).isBlank()) {
            DocumentSnapshot campus = firestore.collection("campuses").document(gate.getString("campusId")).get().get();
            if (!campus.exists() || Boolean.FALSE.equals(campus.getBoolean("active"))) {
                return ResponseEntity.badRequest().body(Map.of("error", "Reactivate the gate's campus first"));
            }
        }
        gate.getReference().update("active", req.isActive(), "updatedAt", FieldValue.serverTimestamp(), "updatedBy", admin.getUid()).get();
        auditService.record(admin, req.isActive() ? "pickup_gate.reactivated" : "pickup_gate.archived", "pickupGate", id,
                Map.of("name", safe(gate.getString("name"))));
        return ResponseEntity.ok(Map.of("id", id, "active", req.isActive()));
    }

    private void ensureUniqueCampus(String schoolId, String name, String ignoreId) throws Exception {
        for (QueryDocumentSnapshot d : firestore.collection("campuses").whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (!d.getId().equals(ignoreId) && name.equalsIgnoreCase(safe(d.getString("name")))) {
                throw new IllegalArgumentException("A campus with that name already exists");
            }
        }
    }

    private void ensureUniqueGate(String schoolId, String campusId, String name, String ignoreId) throws Exception {
        for (QueryDocumentSnapshot d : firestore.collection("pickupGates").whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (!d.getId().equals(ignoreId) && campusId.equals(safe(d.getString("campusId")))
                    && name.equalsIgnoreCase(safe(d.getString("name")))) {
                throw new IllegalArgumentException("That pickup gate already exists for this campus");
            }
        }
    }

    private static Map<String,Object> campusMap(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", d.getId()); m.put("name", safe(d.getString("name"))); m.put("address", safe(d.getString("address")));
        m.put("active", d.getBoolean("active") == null || Boolean.TRUE.equals(d.getBoolean("active"))); return m;
    }
    private static Map<String,Object> gateMap(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", d.getId()); m.put("campusId", safe(d.getString("campusId"))); m.put("campusName", safe(d.getString("campusName")));
        m.put("name", safe(d.getString("name"))); m.put("description", safe(d.getString("description")));
        m.put("active", d.getBoolean("active") == null || Boolean.TRUE.equals(d.getBoolean("active"))); return m;
    }
    private static String safe(String v) { return v == null ? "" : v.trim(); }

    public static class CampusRequest {
        @NotBlank @Size(max=120) private String name; @Size(max=250) private String address;
        public String getName(){return name;} public void setName(String v){name=v;} public String getAddress(){return address;} public void setAddress(String v){address=v;}
    }
    public static class GateRequest {
        private String campusId; @NotBlank @Size(max=120) private String name; @Size(max=250) private String description;
        public String getCampusId(){return campusId;} public void setCampusId(String v){campusId=v;} public String getName(){return name;} public void setName(String v){name=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;}
    }
    public static class ActiveRequest { private boolean active; public boolean isActive(){return active;} public void setActive(boolean v){active=v;} }
}

package com.pickuppass.service;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.pickuppass.security.FirebaseUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** Central append-only audit writer for security- and admin-sensitive actions. */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final Firestore firestore;

    public AuditService(Firestore firestore) {
        this.firestore = firestore;
    }

    public void recordSystem(String schoolId,
                             String action,
                             String resourceType,
                             String resourceId,
                             Map<String, Object> details) {
        Map<String, Object> event = new HashMap<>();
        event.put("schoolId", schoolId);
        event.put("actorUid", "system");
        event.put("actorRole", "system");
        event.put("action", action);
        event.put("resourceType", resourceType);
        event.put("resourceId", resourceId != null ? resourceId : "");
        event.put("details", details != null ? details : Map.of());
        event.put("timestamp", FieldValue.serverTimestamp());
        try {
            if (schoolId != null && !schoolId.isBlank()) {
                firestore.collection("schools").document(schoolId).collection("auditEvents").add(event).get();
            } else {
                firestore.collection("systemAuditEvents").add(event).get();
            }
        } catch (Exception e) {
            log.error("AUDIT_WRITE_FAILED system action={} resourceType={} resourceId={} schoolId={}",
                    action, resourceType, resourceId, schoolId, e);
        }
    }

    public void record(FirebaseUserDetails actor,
                       String action,
                       String resourceType,
                       String resourceId,
                       Map<String, Object> details) {
        Map<String, Object> event = new HashMap<>();
        event.put("schoolId", actor != null ? actor.getSchoolId() : null);
        event.put("actorUid", actor != null ? actor.getUid() : "system");
        event.put("actorRole", actor != null ? actor.getRole() : "system");
        event.put("action", action);
        event.put("resourceType", resourceType);
        event.put("resourceId", resourceId != null ? resourceId : "");
        event.put("details", details != null ? details : Map.of());
        event.put("timestamp", FieldValue.serverTimestamp());

        try {
            if (actor != null && actor.getSchoolId() != null && !actor.getSchoolId().isBlank()) {
                firestore.collection("schools").document(actor.getSchoolId()).collection("auditEvents").add(event).get();
            } else {
                firestore.collection("systemAuditEvents").add(event).get();
            }
        } catch (Exception e) {
            // A failed audit write must be visible operationally, but should not turn a
            // successfully completed safety-critical action into a misleading client retry.
            log.error("AUDIT_WRITE_FAILED action={} resourceType={} resourceId={} actorUid={}",
                    action, resourceType, resourceId, actor != null ? actor.getUid() : "system", e);
        }
    }
}

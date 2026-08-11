package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.security.FirebaseUserDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduledBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledBroadcastService.class);
    private static final String COLLECTION = "broadcastJobs";
    private static final int MAX_HISTORY = 100;
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(10);

    private final Firestore firestore;
    private final BroadcastService broadcastService;
    private final AuditService auditService;

    public ScheduledBroadcastService(Firestore firestore,
                                     BroadcastService broadcastService,
                                     AuditService auditService) {
        this.firestore = firestore;
        this.broadcastService = broadcastService;
        this.auditService = auditService;
    }

    public String recordImmediate(FirebaseUserDetails actor,
                                  String title,
                                  String body,
                                  List<String> audience,
                                  int recipientCount) throws Exception {
        Map<String, Object> doc = baseDocument(actor, title, body, audience, "immediate");
        doc.put("status", "sent");
        doc.put("recipientCount", recipientCount);
        doc.put("sentAt", FieldValue.serverTimestamp());
        return firestore.collection(COLLECTION).add(doc).get().getId();
    }

    public String schedule(FirebaseUserDetails actor,
                           String title,
                           String body,
                           List<String> audience,
                           Instant scheduledAt) throws Exception {
        Map<String, Object> doc = baseDocument(actor, title, body, audience, "scheduled");
        doc.put("status", "scheduled");
        doc.put("scheduledAt", timestamp(scheduledAt));
        return firestore.collection(COLLECTION).add(doc).get().getId();
    }

    public boolean cancel(String schoolId, String broadcastId) throws Exception {
        DocumentReference ref = firestore.collection(COLLECTION).document(broadcastId);
        return firestore.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref).get();
            if (!snap.exists() || !schoolId.equals(snap.getString("schoolId"))) return false;
            String status = snap.getString("status");
            if (!"scheduled".equals(status)) return false;
            transaction.update(ref, Map.of(
                    "status", "cancelled",
                    "cancelledAt", FieldValue.serverTimestamp()
            ));
            return true;
        }).get();
    }

    public List<Map<String, Object>> history(String schoolId, int requestedLimit) throws Exception {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_HISTORY));
        List<QueryDocumentSnapshot> docs = firestore.collection(COLLECTION)
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments();

        List<DocumentSnapshot> sorted = new ArrayList<>(docs);
        sorted.sort(Comparator.comparing(this::createdAtInstant).reversed());

        List<Map<String, Object>> result = new ArrayList<>();
        for (DocumentSnapshot doc : sorted.stream().limit(limit).toList()) {
            result.add(toResponse(doc));
        }
        return result;
    }

    /**
     * Polls for due work. Claiming is transactional, so multiple Cloud Run
     * instances may run this scheduler without normally double-sending the
     * same job. A processing lease allows recovery if an instance dies mid-job.
     */
    @Scheduled(fixedDelayString = "${pickuppass.broadcast-scheduler-ms:30000}")
    public void deliverDueBroadcasts() {
        try {
            recoverExpiredLeases();
            Instant now = Instant.now();
            List<QueryDocumentSnapshot> due = firestore.collection(COLLECTION)
                    .whereEqualTo("status", "scheduled")
                    .whereLessThanOrEqualTo("scheduledAt", timestamp(now))
                    .orderBy("scheduledAt")
                    .limit(50)
                    .get().get().getDocuments();

            for (QueryDocumentSnapshot doc : due) {
                deliverOne(doc.getId());
            }
        } catch (Exception e) {
            log.error("Scheduled broadcast sweep failed", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void deliverOne(String id) {
        DocumentReference ref = firestore.collection(COLLECTION).document(id);
        try {
            boolean claimed = firestore.runTransaction(transaction -> {
                DocumentSnapshot snap = transaction.get(ref).get();
                if (!snap.exists() || !"scheduled".equals(snap.getString("status"))) return false;
                Timestamp scheduledAt = snap.getTimestamp("scheduledAt");
                if (scheduledAt == null || scheduledAt.toDate().toInstant().isAfter(Instant.now())) return false;
                transaction.update(ref, Map.of(
                        "status", "processing",
                        "processingStartedAt", FieldValue.serverTimestamp()
                ));
                return true;
            }).get();
            if (!claimed) return;

            DocumentSnapshot snap = ref.get().get();
            String schoolId = snap.getString("schoolId");
            String senderUid = snap.getString("senderUid");
            String title = snap.getString("title");
            String body = snap.getString("body");
            List<String> audience = (List<String>) snap.get("audience");
            if (audience == null) audience = List.of();

            int recipients = broadcastService.broadcastToSchool(schoolId, senderUid, title, body, audience);
            ref.update(Map.of(
                    "status", "sent",
                    "recipientCount", recipients,
                    "sentAt", FieldValue.serverTimestamp()
            )).get();

            auditService.recordSystem(schoolId, "broadcast.scheduled_sent", "broadcast", id, Map.of(
                    "title", title,
                    "audience", audience,
                    "recipientCount", recipients,
                    "senderUid", senderUid
            ));
        } catch (Exception e) {
            log.error("Scheduled broadcast {} failed", id, e);
            try {
                ref.update(Map.of(
                        "status", "failed",
                        "failedAt", FieldValue.serverTimestamp(),
                        "errorMessage", safeError(e)
                )).get();
                DocumentSnapshot snap = ref.get().get();
                String schoolId = snap.getString("schoolId");
                if (schoolId != null) {
                    auditService.recordSystem(schoolId, "broadcast.scheduled_failed", "broadcast", id,
                            Map.of("error", safeError(e)));
                }
            } catch (Exception updateError) {
                log.error("Could not mark scheduled broadcast {} as failed", id, updateError);
            }
        }
    }

    private void recoverExpiredLeases() throws Exception {
        Instant cutoff = Instant.now().minus(PROCESSING_LEASE);
        List<QueryDocumentSnapshot> processing = firestore.collection(COLLECTION)
                .whereEqualTo("status", "processing")
                .get().get().getDocuments();

        for (QueryDocumentSnapshot doc : processing) {
            Timestamp started = doc.getTimestamp("processingStartedAt");
            if (started != null && started.toDate().toInstant().isBefore(cutoff)) {
                doc.getReference().update(Map.of(
                        "status", "scheduled",
                        "leaseRecoveredAt", FieldValue.serverTimestamp()
                ));
            }
        }
    }

    private Map<String, Object> baseDocument(FirebaseUserDetails actor,
                                             String title,
                                             String body,
                                             List<String> audience,
                                             String mode) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("schoolId", actor.getSchoolId());
        doc.put("senderUid", actor.getUid());
        doc.put("senderRole", actor.getRole());
        doc.put("title", title);
        doc.put("body", body);
        doc.put("audience", List.copyOf(audience));
        doc.put("deliveryMode", mode);
        doc.put("createdAt", FieldValue.serverTimestamp());
        return doc;
    }

    private Map<String, Object> toResponse(DocumentSnapshot doc) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", doc.getId());
        item.put("title", string(doc, "title"));
        item.put("body", string(doc, "body"));
        item.put("status", string(doc, "status"));
        item.put("deliveryMode", string(doc, "deliveryMode"));
        item.put("audience", doc.get("audience") != null ? doc.get("audience") : List.of());
        item.put("recipientCount", doc.getLong("recipientCount") != null ? doc.getLong("recipientCount") : 0L);
        item.put("createdAt", iso(doc.getTimestamp("createdAt")));
        item.put("scheduledAt", iso(doc.getTimestamp("scheduledAt")));
        item.put("sentAt", iso(doc.getTimestamp("sentAt")));
        item.put("cancelledAt", iso(doc.getTimestamp("cancelledAt")));
        item.put("errorMessage", doc.getString("errorMessage"));
        return item;
    }

    private Instant createdAtInstant(DocumentSnapshot doc) {
        Timestamp ts = doc.getTimestamp("createdAt");
        return ts != null ? ts.toDate().toInstant() : Instant.EPOCH;
    }

    private String string(DocumentSnapshot doc, String field) {
        String value = doc.getString(field);
        return value != null ? value : "";
    }

    private String iso(Timestamp timestamp) {
        return timestamp != null ? timestamp.toDate().toInstant().toString() : null;
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

    private String safeError(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return e.getClass().getSimpleName();
        return message.length() <= 240 ? message : message.substring(0, 240);
    }
}

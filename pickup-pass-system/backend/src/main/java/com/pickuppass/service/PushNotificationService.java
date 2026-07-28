package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Notifies users via TWO independent channels:
 *
 *  1. A persisted record in the `notifications` collection — the source of
 *     truth for the in-app notification inbox, and written regardless of
 *     whether push delivery succeeds. A guardian/teacher who's never
 *     registered a device token (or whose token's gone stale) still sees
 *     the notification next time they open the app.
 *  2. A best-effort FCM push, for recipients who do have a registered
 *     device token, so they get a real-time system notification too.
 *
 * Failures in either channel are logged, never thrown — a notification
 * provider hiccup must never block or roll back the action that triggered
 * it (a pickup approval, an admin's broadcast send).
 *
 * Two entry points: notifyGuardiansOfPickup (resolves recipients itself
 * from a studentId) and notifyUsers (recipients already resolved by the
 * caller — used by BroadcastService, where "who gets this" is a School
 * Admin/Teacher-specific targeting decision that doesn't belong in a
 * generic notification service).
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

    /** Firestore batched writes cap at 500 operations; chunk any larger fan-out into multiple batches. */
    private static final int BATCH_CHUNK_SIZE = 450;

    private final Firestore firestore;
    private final FirebaseMessaging messaging;

    public PushNotificationService(Firestore firestore) {
        this.firestore = firestore;
        this.messaging = FirebaseMessaging.getInstance();
    }

    public void notifyGuardiansOfPickup(String studentId, String pickedUpByUid) {
        try {
            DocumentSnapshot studentSnap = firestore.collection("students").document(studentId).get().get();
            if (!studentSnap.exists()) return;

            String studentName = studentSnap.getString("fullName");
            String schoolId = studentSnap.getString("schoolId");
            @SuppressWarnings("unchecked")
            List<String> guardianUids = (List<String>) studentSnap.get("guardianUids");
            if (guardianUids == null || guardianUids.isEmpty()) return;

            String pickerName = resolveDisplayName(pickedUpByUid);

            String title = studentName + " has been picked up";
            String body = pickerName + " just picked up " + studentName + " from school.";

            notifyUsers(guardianUids, schoolId, title, body, "pickup_confirmation", studentId, null);
        } catch (Exception e) {
            log.warn("Pickup notification failed for student {}: {}", studentId, e.getMessage());
        }
    }

    /**
     * Generic fan-out: persists a notifications doc for every recipient
     * (batched, so a broadcast to a whole school is a handful of writes
     * rather than one round-trip per person) and best-effort pushes to
     * each. Used directly by BroadcastService; notifyGuardiansOfPickup
     * above is a thin wrapper that resolves its own recipient list first.
     *
     * @param studentId nullable — only meaningful for pickup_confirmation;
     *                  broadcasts have no associated student.
     * @param senderName nullable — shown on broadcast notifications as
     *                   "From: <name>"; irrelevant for pickup confirmations.
     */
    public void notifyUsers(
            List<String> recipientUids, String schoolId, String title, String body, String type,
            String studentId, String senderName) {

        recordNotifications(recipientUids, schoolId, title, body, type, studentId, senderName);
        for (String uid : recipientUids) {
            sendPush(uid, title, body, type, studentId);
        }
    }

    /**
     * Writes the persisted inbox record for every recipient in batched
     * writes. This is what "My Notifications" screens (web + Android, both
     * parent- and teacher-facing) actually read from — never the FCM push
     * itself, which is fire-and-forget and has no history.
     */
    private void recordNotifications(
            List<String> recipientUids, String schoolId, String title, String body, String type,
            String studentId, String senderName) {
        try {
            for (int start = 0; start < recipientUids.size(); start += BATCH_CHUNK_SIZE) {
                int end = Math.min(start + BATCH_CHUNK_SIZE, recipientUids.size());
                WriteBatch batch = firestore.batch();

                for (String recipientUid : recipientUids.subList(start, end)) {
                    Map<String, Object> notification = new HashMap<>();
                    notification.put("recipientUid", recipientUid);
                    notification.put("schoolId", schoolId);
                    notification.put("title", title);
                    notification.put("body", body);
                    notification.put("type", type);
                    if (studentId != null) notification.put("studentId", studentId);
                    if (senderName != null) notification.put("senderName", senderName);
                    notification.put("read", false);
                    notification.put("createdAt", FieldValue.serverTimestamp());
                    batch.set(firestore.collection("notifications").document(), notification);
                }

                batch.commit().get();
            }
        } catch (Exception e) {
            log.warn("Could not record in-app notifications (type={}): {}", type, e.getMessage());
        }
    }

    private String resolveDisplayName(String uid) {
        try {
            DocumentSnapshot snap = firestore.collection("users").document(uid).get().get();
            String name = snap.exists() ? snap.getString("displayName") : null;
            return (name != null && !name.isBlank()) ? name : "An authorized guardian";
        } catch (Exception e) {
            return "An authorized guardian";
        }
    }

    @SuppressWarnings("unchecked")
    private void sendPush(String uid, String title, String body, String type, String studentId) {
        try {
            DocumentSnapshot userSnap = firestore.collection("users").document(uid).get().get();
            if (!userSnap.exists()) return;

            List<String> tokens = (List<String>) userSnap.get("fcmTokens");
            if (tokens == null || tokens.isEmpty()) return;

            MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putData("type", type);
            if (studentId != null) {
                messageBuilder.putData("studentId", studentId);
            }

            BatchResponse response = messaging.sendEachForMulticast(messageBuilder.build());
            pruneInvalidTokens(uid, tokens, response);

        } catch (ExecutionException | InterruptedException e) {
            log.warn("Could not load device tokens for user {}: {}", uid, e.getMessage());
        } catch (FirebaseMessagingException e) {
            log.warn("FCM send failed for user {}: {}", uid, e.getMessage());
        }
    }

    /** Removes tokens FCM reports as unregistered/invalid so the list doesn't grow stale forever. */
    private void pruneInvalidTokens(String uid, List<String> tokens, BatchResponse response) {
        List<String> invalidTokens = new ArrayList<>();
        List<SendResponse> responses = response.getResponses();

        for (int i = 0; i < responses.size(); i++) {
            SendResponse r = responses.get(i);
            if (!r.isSuccessful() && r.getException() != null) {
                MessagingErrorCode code = r.getException().getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    invalidTokens.add(tokens.get(i));
                }
            }
        }

        if (!invalidTokens.isEmpty()) {
            firestore.collection("users").document(uid)
                    .update("fcmTokens", FieldValue.arrayRemove(invalidTokens.toArray()));
        }
    }
}

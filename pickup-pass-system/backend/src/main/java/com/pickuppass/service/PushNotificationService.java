package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.messaging.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Sends "your child was just picked up" push notifications to every
 * authorized guardian of a student once a dismissal is approved.
 *
 * Notification failures are intentionally swallowed (logged, not thrown) —
 * a push provider hiccup must never block or roll back the pickup approval
 * itself, which is the safety-critical part of the flow.
 */
@Service
public class PushNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationService.class);

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
            @SuppressWarnings("unchecked")
            List<String> guardianUids = (List<String>) studentSnap.get("guardianUids");
            if (guardianUids == null || guardianUids.isEmpty()) return;

            String pickerName = resolveDisplayName(pickedUpByUid);

            String title = studentName + " has been picked up";
            String body = pickerName + " just picked up " + studentName + " from school.";

            for (String guardianUid : guardianUids) {
                sendToUser(guardianUid, title, body, studentId);
            }
        } catch (Exception e) {
            log.warn("Pickup notification failed for student {}: {}", studentId, e.getMessage());
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
    private void sendToUser(String uid, String title, String body, String studentId) {
        try {
            DocumentSnapshot userSnap = firestore.collection("users").document(uid).get().get();
            if (!userSnap.exists()) return;

            List<String> tokens = (List<String>) userSnap.get("fcmTokens");
            if (tokens == null || tokens.isEmpty()) return;

            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .putData("type", "pickup_confirmation")
                    .putData("studentId", studentId)
                    .build();

            BatchResponse response = messaging.sendEachForMulticast(message);
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

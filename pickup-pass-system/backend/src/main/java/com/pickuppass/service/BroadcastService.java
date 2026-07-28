package com.pickuppass.service;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.exception.ForbiddenException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves WHO should receive a broadcast — the one piece of logic that's
 * genuinely specific to "a school admin or teacher wants to announce
 * something," as opposed to PushNotificationService's generic "notify this
 * list of people" primitive, which has no opinion on how that list was
 * built.
 *
 * Deliberately does its own Firestore queries + in-application filtering
 * rather than composite Firestore queries (e.g. whereIn on a computed
 * "gradeSection" field) — a school's full roster and staff list are both
 * small, bounded collections, and filtering roster-sized data in Java
 * avoids needing any new Firestore indexes AND avoids a silent
 * backward-compatibility trap where students created before this feature
 * existed would need a one-time migration to become broadcast-reachable.
 */
@Service
public class BroadcastService {

    private final Firestore firestore;
    private final PushNotificationService pushNotificationService;

    public BroadcastService(Firestore firestore, PushNotificationService pushNotificationService) {
        this.firestore = firestore;
        this.pushNotificationService = pushNotificationService;
    }

    /**
     * @param audienceRoles subset of {"teacher","parent"} — validated by the controller
     * @return number of recipients notified
     */
    public int broadcastToSchool(
            String schoolId, String senderUid, String title, String body, List<String> audienceRoles) throws Exception {

        String senderName = resolveSenderName(senderUid);

        List<QueryDocumentSnapshot> schoolUsers = firestore.collection("users")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments();

        List<String> recipientUids = new ArrayList<>();
        for (QueryDocumentSnapshot doc : schoolUsers) {
            if (doc.getId().equals(senderUid)) continue; // don't notify the sender of their own broadcast
            String role = doc.getString("role");
            if (role != null && audienceRoles.contains(role)) {
                recipientUids.add(doc.getId());
            }
        }

        if (!recipientUids.isEmpty()) {
            pushNotificationService.notifyUsers(recipientUids, schoolId, title, body, "broadcast", null, senderName);
        }
        return recipientUids.size();
    }

    /**
     * @return number of recipients notified
     * @throws ForbiddenException if the teacher has no assigned sections yet
     */
    @SuppressWarnings("unchecked")
    public int broadcastToSection(
            String schoolId, String teacherUid, String title, String body) throws Exception {

        DocumentSnapshot teacherSnap = firestore.collection("users").document(teacherUid).get().get();
        List<Map<String, String>> assignedSections = (List<Map<String, String>>) teacherSnap.get("assignedSections");

        if (assignedSections == null || assignedSections.isEmpty()) {
            throw new ForbiddenException(
                    "You don't have any assigned sections yet — ask your school admin to assign you one first.");
        }

        String senderName = displayNameOrFallback(teacherSnap);

        List<QueryDocumentSnapshot> allStudents = firestore.collection("students")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments();

        Set<String> recipientUids = new HashSet<>(); // dedupe: one guardian can have multiple kids in the section
        for (QueryDocumentSnapshot studentDoc : allStudents) {
            String grade = studentDoc.getString("grade");
            String section = studentDoc.getString("section");
            if (!matchesAnySection(grade, section, assignedSections)) continue;

            List<String> guardianUids = (List<String>) studentDoc.get("guardianUids");
            if (guardianUids != null) {
                recipientUids.addAll(guardianUids);
            }
        }

        List<String> recipients = new ArrayList<>(recipientUids);
        if (!recipients.isEmpty()) {
            pushNotificationService.notifyUsers(recipients, schoolId, title, body, "broadcast", null, senderName);
        }
        return recipients.size();
    }

    private boolean matchesAnySection(String grade, String section, List<Map<String, String>> assignedSections) {
        if (grade == null || section == null) return false;
        for (Map<String, String> assigned : assignedSections) {
            if (grade.equals(assigned.get("grade")) && section.equals(assigned.get("section"))) {
                return true;
            }
        }
        return false;
    }

    private String resolveSenderName(String uid) throws Exception {
        DocumentSnapshot snap = firestore.collection("users").document(uid).get().get();
        return displayNameOrFallback(snap);
    }

    private String displayNameOrFallback(DocumentSnapshot snap) {
        String name = snap.exists() ? snap.getString("displayName") : null;
        return (name != null && !name.isBlank()) ? name : "School Announcement";
    }
}

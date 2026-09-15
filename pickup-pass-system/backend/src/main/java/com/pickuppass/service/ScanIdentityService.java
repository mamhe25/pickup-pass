package com.pickuppass.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the identity payload displayed immediately after a QR is verified.
 * Student and guardian documents are independent, so both are requested at
 * once. This keeps the scanner's critical response to one backend request and
 * lets Android/web skip their own follow-up Firestore reads.
 */
@Service
public class ScanIdentityService {

    private final Firestore firestore;

    public ScanIdentityService(Firestore firestore) {
        this.firestore = firestore;
    }

    public Map<String, Object> load(
            String studentId,
            String guardianUid,
            String schoolId) throws Exception {
        ApiFuture<DocumentSnapshot> studentFuture =
                firestore.collection("students").document(studentId).get();
        ApiFuture<DocumentSnapshot> guardianFuture =
                firestore.collection("users").document(guardianUid).get();

        DocumentSnapshot student = studentFuture.get();
        DocumentSnapshot guardian = guardianFuture.get();

        if (!student.exists()
                || !guardian.exists()
                || !schoolId.equals(student.getString("schoolId"))
                || !schoolId.equals(guardian.getString("schoolId"))) {
            return Map.of();
        }

        String relationship = "";
        boolean primary = false;
        Object guardiansRaw = student.get("guardians");
        if (guardiansRaw instanceof Map<?, ?> guardians) {
            Object entryRaw = guardians.get(guardianUid);
            if (entryRaw instanceof Map<?, ?> entry) {
                relationship = stringValue(entry.get("relationship"));
                primary = Boolean.TRUE.equals(entry.get("isPrimary"));
            }
        }

        Map<String, Object> studentPayload = new LinkedHashMap<>();
        studentPayload.put("id", studentId);
        studentPayload.put("fullName", stringValue(student.getString("fullName")));
        studentPayload.put("grade", stringValue(student.getString("grade")));
        studentPayload.put("section", stringValue(student.getString("section")));
        studentPayload.put(
                "studentNumber",
                firstNonBlank(student.getString("studentNumber"), student.getString("lrn")));
        studentPayload.put("guardianRelationship", relationship);
        studentPayload.put("guardianPrimary", primary);

        Map<String, Object> guardianPayload = new LinkedHashMap<>();
        guardianPayload.put("uid", guardianUid);
        guardianPayload.put(
                "displayName",
                firstNonBlank(guardian.getString("displayName"), guardian.getString("email")));
        guardianPayload.put("photoUrl", stringValue(guardian.getString("photoUrl")));
        guardianPayload.put(
                "photoValidationStatus",
                stringValue(guardian.getString("photoValidationStatus")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("student", studentPayload);
        result.put("guardian", guardianPayload);
        return result;
    }

    private static String firstNonBlank(String primary, String fallback) {
        String first = stringValue(primary);
        return first.isBlank() ? stringValue(fallback) : first;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}

package com.pickuppass.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.security.FirebaseUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Live school-admin view of today's release status. This is intentionally a
 * dashboard, not a pickup queue: parents can still present a valid QR at any
 * time allowed by the school's pickup policy.
 */
@RestController
@RequestMapping("/api/school-admin/dismissal-dashboard")
public class DismissalDashboardController {

    private final Firestore firestore;
    private final ZoneId schoolTimeZone;

    public DismissalDashboardController(Firestore firestore,
                                        @Value("${app.school-time-zone:Asia/Manila}") String timeZone) {
        this.firestore = firestore;
        this.schoolTimeZone = ZoneId.of(timeZone);
    }

    @GetMapping
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> getDashboard(
            @RequestParam(required = false) String businessDate,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        LocalDate date;
        try {
            date = businessDate == null || businessDate.isBlank()
                    ? LocalDate.now(schoolTimeZone)
                    : LocalDate.parse(businessDate);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "businessDate must use YYYY-MM-DD"));
        }

        String schoolId = admin.getSchoolId();
        String dateText = date.toString();

        List<QueryDocumentSnapshot> studentDocs = firestore.collection("students")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments();

        Map<String, Map<String, Object>> studentsById = new HashMap<>();
        for (QueryDocumentSnapshot doc : studentDocs) {
            Map<String, Object> item = new HashMap<>();
            item.put("studentId", doc.getId());
            item.put("studentName", value(doc.getString("fullName"), "Unknown student"));
            item.put("grade", value(doc.getString("grade"), ""));
            item.put("section", value(doc.getString("section"), ""));
            studentsById.put(doc.getId(), item);
        }

        List<QueryDocumentSnapshot> userDocs = firestore.collection("users")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments();
        Map<String, String> namesByUid = new HashMap<>();
        for (QueryDocumentSnapshot doc : userDocs) {
            namesByUid.put(doc.getId(), value(doc.getString("displayName"), value(doc.getString("email"), "Unknown user")));
        }

        List<QueryDocumentSnapshot> releaseDocs = firestore.collection("exitLogs")
                .whereEqualTo("schoolId", schoolId)
                .whereEqualTo("businessDate", dateText)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get().get().getDocuments();

        Set<String> releasedStudentIds = new HashSet<>();
        List<Map<String, Object>> recentReleases = new ArrayList<>();
        for (QueryDocumentSnapshot doc : releaseDocs) {
            String studentId = doc.getString("studentId");
            if (studentId != null) releasedStudentIds.add(studentId);

            if (recentReleases.size() < 50) {
                Map<String, Object> item = new HashMap<>();
                item.put("exitLogId", doc.getId());
                item.put("studentId", value(studentId, ""));
                Map<String, Object> student = studentId == null ? null : studentsById.get(studentId);
                item.put("studentName", student != null ? student.get("studentName") : "Unknown student");
                item.put("grade", student != null ? student.get("grade") : "");
                item.put("section", student != null ? student.get("section") : "");
                String guardianUid = doc.getString("parentUid");
                String staffUid = doc.getString("verifiedByUid");
                item.put("guardianUid", value(guardianUid, ""));
                item.put("guardianName", guardianUid == null ? "Unknown guardian" : namesByUid.getOrDefault(guardianUid, "Unknown guardian"));
                item.put("staffName", staffUid == null ? "Unknown staff" : namesByUid.getOrDefault(staffUid, "Unknown staff"));
                item.put("method", value(doc.getString("method"), "qr_scan"));
                Timestamp timestamp = doc.getTimestamp("timestamp");
                item.put("timestamp", timestamp == null ? null : timestamp.toDate());
                recentReleases.add(item);
            }
        }

        List<Map<String, Object>> remainingStudents = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : studentsById.entrySet()) {
            if (!releasedStudentIds.contains(entry.getKey())) remainingStudents.add(entry.getValue());
        }
        remainingStudents.sort(Comparator.comparing(v -> String.valueOf(v.get("studentName")), String.CASE_INSENSITIVE_ORDER));

        int total = studentsById.size();
        int released = releasedStudentIds.size();
        int remaining = Math.max(0, total - released);
        double releaseRate = total == 0 ? 0.0 : Math.round((released * 1000.0 / total)) / 10.0;
        boolean remainingTruncated = remainingStudents.size() > 250;
        if (remainingTruncated) remainingStudents = new ArrayList<>(remainingStudents.subList(0, 250));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("businessDate", dateText);
        body.put("timeZone", schoolTimeZone.getId());
        body.put("totalStudents", total);
        body.put("releasedCount", released);
        body.put("remainingCount", remaining);
        body.put("releaseRatePercent", releaseRate);
        body.put("recentReleases", recentReleases);
        body.put("remainingStudents", remainingStudents);
        body.put("remainingTruncated", remainingTruncated);
        return ResponseEntity.ok(body);
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

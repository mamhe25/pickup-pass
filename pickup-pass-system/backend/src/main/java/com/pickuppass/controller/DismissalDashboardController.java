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
            String studentStatus = value(doc.getString("status"), "active");
            if (!"active".equalsIgnoreCase(studentStatus)) continue;
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
        Map<String, MutableGateActivity> gateActivity = new LinkedHashMap<>();
        Map<String, MutableCampusActivity> campusActivity = new LinkedHashMap<>();
        int qrReleaseCount = 0;
        int manualOverrideCount = 0;

        // Seed active configured gates so a quiet gate still appears with a zero count.
        List<QueryDocumentSnapshot> activeGateDocs = firestore.collection("pickupGates")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments();
        for (QueryDocumentSnapshot gateDoc : activeGateDocs) {
            if (Boolean.FALSE.equals(gateDoc.getBoolean("active"))) continue;
            String gateId = gateDoc.getId();
            String gateName = value(gateDoc.getString("name"), "Unnamed gate");
            String campusId = value(gateDoc.getString("campusId"), "");
            String campusName = value(gateDoc.getString("campusName"), "");
            gateActivity.put(gateId, new MutableGateActivity(gateId, gateName, campusId, campusName));
            if (!campusId.isBlank()) {
                campusActivity.putIfAbsent(campusId, new MutableCampusActivity(campusId, campusName));
            }
        }

        for (QueryDocumentSnapshot doc : releaseDocs) {
            String studentId = doc.getString("studentId");
            if (studentId != null) releasedStudentIds.add(studentId);

            String method = value(doc.getString("method"), "qr_scan");
            if ("manual_override".equalsIgnoreCase(method)) manualOverrideCount++;
            else qrReleaseCount++;

            String gateId = value(doc.getString("pickupGateId"), "");
            String gateName = value(doc.getString("pickupGateNameSnapshot"), "");
            String campusId = value(doc.getString("campusId"), "");
            String campusName = value(doc.getString("campusNameSnapshot"), "");
            if (!gateId.isBlank()) {
                MutableGateActivity gate = gateActivity.computeIfAbsent(
                        gateId, id -> new MutableGateActivity(id, gateName, campusId, campusName));
                gate.releaseCount++;
                if ("manual_override".equalsIgnoreCase(method)) gate.manualOverrideCount++;
                else gate.qrReleaseCount++;
            }
            if (!campusId.isBlank()) {
                MutableCampusActivity campus = campusActivity.computeIfAbsent(
                        campusId, id -> new MutableCampusActivity(id, campusName));
                campus.releaseCount++;
            }

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
                item.put("method", method);
                item.put("pickupGateId", gateId);
                item.put("pickupGateName", gateName);
                item.put("campusId", campusId);
                item.put("campusName", campusName);
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
        List<Map<String, Object>> gateActivityItems = gateActivity.values().stream()
                .sorted(Comparator.comparingInt(MutableGateActivity::releaseCount).reversed()
                        .thenComparing(MutableGateActivity::gateName, String.CASE_INSENSITIVE_ORDER))
                .map(MutableGateActivity::toMap)
                .toList();
        List<Map<String, Object>> campusActivityItems = campusActivity.values().stream()
                .sorted(Comparator.comparingInt(MutableCampusActivity::releaseCount).reversed()
                        .thenComparing(MutableCampusActivity::campusName, String.CASE_INSENSITIVE_ORDER))
                .map(MutableCampusActivity::toMap)
                .toList();

        body.put("releaseRatePercent", releaseRate);
        body.put("qrReleaseCount", qrReleaseCount);
        body.put("manualOverrideCount", manualOverrideCount);
        body.put("gateActivity", gateActivityItems);
        body.put("campusActivity", campusActivityItems);
        body.put("recentReleases", recentReleases);
        body.put("remainingStudents", remainingStudents);
        body.put("remainingTruncated", remainingTruncated);
        return ResponseEntity.ok(body);
    }

    private static final class MutableGateActivity {
        private final String gateId;
        private final String gateName;
        private final String campusId;
        private final String campusName;
        private int releaseCount;
        private int qrReleaseCount;
        private int manualOverrideCount;

        private MutableGateActivity(String gateId, String gateName, String campusId, String campusName) {
            this.gateId = gateId;
            this.gateName = value(gateName, "Unnamed gate");
            this.campusId = value(campusId, "");
            this.campusName = value(campusName, "");
        }

        private int releaseCount() { return releaseCount; }
        private String gateName() { return gateName; }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("pickupGateId", gateId);
            map.put("pickupGateName", gateName);
            map.put("campusId", campusId);
            map.put("campusName", campusName);
            map.put("releaseCount", releaseCount);
            map.put("qrReleaseCount", qrReleaseCount);
            map.put("manualOverrideCount", manualOverrideCount);
            return map;
        }
    }

    private static final class MutableCampusActivity {
        private final String campusId;
        private final String campusName;
        private int releaseCount;

        private MutableCampusActivity(String campusId, String campusName) {
            this.campusId = campusId;
            this.campusName = value(campusName, "Campus");
        }

        private int releaseCount() { return releaseCount; }
        private String campusName() { return campusName; }

        private Map<String, Object> toMap() {
            return Map.of(
                    "campusId", campusId,
                    "campusName", campusName,
                    "releaseCount", releaseCount
            );
        }
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

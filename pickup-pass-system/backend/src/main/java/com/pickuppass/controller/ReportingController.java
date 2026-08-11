package com.pickuppass.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Tenant-isolated dismissal reporting for school administrators.
 * Reports are derived from immutable exitLogs; they never alter pickup records.
 */
@RestController
@RequestMapping("/api/school-admin/reports/dismissals")
public class ReportingController {

    private static final int MAX_RANGE_DAYS = 366;
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Firestore firestore;
    private final AuditService auditService;
    private final ZoneId schoolTimeZone;

    public ReportingController(Firestore firestore,
                               AuditService auditService,
                               @Value("${app.school-time-zone:Asia/Manila}") String timeZone) {
        this.firestore = firestore;
        this.auditService = auditService;
        this.schoolTimeZone = ZoneId.of(timeZone);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> summary(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String section,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DateRange range = parseRange(from, to);
        if (range.error != null) return ResponseEntity.badRequest().body(Map.of("error", range.error));

        ReportData report = loadReport(admin.getSchoolId(), range, clean(grade), clean(section));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", range.from.toString());
        body.put("to", range.to.toString());
        body.put("timeZone", schoolTimeZone.getId());
        body.put("grade", clean(grade));
        body.put("section", clean(section));
        body.put("totalReleases", report.rows.size());
        body.put("uniqueStudentsReleased", report.uniqueStudentIds.size());
        body.put("qrReleases", report.qrReleases);
        body.put("manualOverrides", report.manualOverrides);
        body.put("dailyCounts", report.dailyCounts);
        body.put("gradeSectionCounts", report.gradeSectionCounts);
        return ResponseEntity.ok(body);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> exportCsv(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String section,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        DateRange range = parseRange(from, to);
        if (range.error != null) return ResponseEntity.badRequest().body(range.error.getBytes(StandardCharsets.UTF_8));

        String gradeFilter = clean(grade);
        String sectionFilter = clean(section);
        ReportData report = loadReport(admin.getSchoolId(), range, gradeFilter, sectionFilter);

        StringBuilder csv = new StringBuilder(16_384);
        csv.append("Business Date,Time,Student Number,Student Name,Grade,Section,Guardian,Verified By,Method,Exit Log ID\r\n");
        for (ReportRow row : report.rows) {
            csv.append(csv(row.businessDate)).append(',')
                    .append(csv(row.time)).append(',')
                    .append(csv(row.studentNumber)).append(',')
                    .append(csv(row.studentName)).append(',')
                    .append(csv(row.grade)).append(',')
                    .append(csv(row.section)).append(',')
                    .append(csv(row.guardianName)).append(',')
                    .append(csv(row.staffName)).append(',')
                    .append(csv(row.method)).append(',')
                    .append(csv(row.exitLogId)).append("\r\n");
        }

        String fileName = "pickuppass-dismissals-" + range.from + "-to-" + range.to + ".csv";
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);

        auditService.record(admin, "DISMISSAL_REPORT_EXPORTED", "report", fileName, Map.of(
                "from", range.from.toString(),
                "to", range.to.toString(),
                "grade", gradeFilter == null ? "" : gradeFilter,
                "section", sectionFilter == null ? "" : sectionFilter,
                "rowCount", report.rows.size()
        ));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .contentLength(bytes.length)
                .body(bytes);
    }

    private ReportData loadReport(String schoolId, DateRange range, String gradeFilter, String sectionFilter) throws Exception {
        List<QueryDocumentSnapshot> studentDocs = firestore.collection("students")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments();

        Map<String, StudentInfo> students = new HashMap<>();
        for (QueryDocumentSnapshot doc : studentDocs) {
            students.put(doc.getId(), new StudentInfo(
                    value(doc.getString("studentNumber"), value(doc.getString("lrn"), "")),
                    value(doc.getString("fullName"), "Unknown student"),
                    value(doc.getString("grade"), ""),
                    value(doc.getString("section"), "")
            ));
        }

        List<QueryDocumentSnapshot> userDocs = firestore.collection("users")
                .whereEqualTo("schoolId", schoolId)
                .get().get().getDocuments();
        Map<String, String> userNames = new HashMap<>();
        for (QueryDocumentSnapshot doc : userDocs) {
            userNames.put(doc.getId(), value(doc.getString("displayName"), value(doc.getString("email"), "Unknown user")));
        }

        List<QueryDocumentSnapshot> logs = firestore.collection("exitLogs")
                .whereEqualTo("schoolId", schoolId)
                .whereGreaterThanOrEqualTo("businessDate", range.from.toString())
                .whereLessThanOrEqualTo("businessDate", range.to.toString())
                .orderBy("businessDate", Query.Direction.ASCENDING)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get().get().getDocuments();

        ReportData result = new ReportData();
        for (QueryDocumentSnapshot log : logs) {
            String studentId = log.getString("studentId");
            StudentInfo student = studentId == null ? null : students.get(studentId);
            String rowGrade = value(log.getString("gradeSnapshot"), student == null ? value(log.getString("grade"), "") : student.grade);
            String rowSection = value(log.getString("sectionSnapshot"), student == null ? value(log.getString("section"), "") : student.section);

            if (gradeFilter != null && !gradeFilter.equalsIgnoreCase(rowGrade)) continue;
            if (sectionFilter != null && !sectionFilter.equalsIgnoreCase(rowSection)) continue;

            String method = value(log.getString("method"), "qr_scan");
            Timestamp ts = log.getTimestamp("timestamp");
            ZonedDateTime localTime = ts == null
                    ? range.from.atStartOfDay(schoolTimeZone)
                    : ts.toDate().toInstant().atZone(schoolTimeZone);
            String businessDate = value(log.getString("businessDate"), localTime.toLocalDate().format(CSV_DATE));

            String guardianUid = log.getString("parentUid");
            String staffUid = log.getString("verifiedByUid");
            ReportRow row = new ReportRow(
                    log.getId(),
                    businessDate,
                    localTime.toLocalTime().format(CSV_TIME),
                    value(log.getString("studentNumberSnapshot"), student == null ? "" : student.studentNumber),
                    value(log.getString("studentNameSnapshot"), student == null ? "Unknown student" : student.fullName),
                    rowGrade,
                    rowSection,
                    value(log.getString("guardianNameSnapshot"), guardianUid == null ? "Unknown guardian" : userNames.getOrDefault(guardianUid, "Unknown guardian")),
                    value(log.getString("verifiedByNameSnapshot"), staffUid == null ? "Unknown staff" : userNames.getOrDefault(staffUid, "Unknown staff")),
                    method
            );
            result.rows.add(row);
            if (studentId != null) result.uniqueStudentIds.add(studentId);
            if ("manual_override".equalsIgnoreCase(method)) result.manualOverrides++; else result.qrReleases++;
            result.dailyCounts.merge(businessDate, 1, Integer::sum);
            String group = (rowGrade.isBlank() ? "Unassigned" : "Grade " + rowGrade) +
                    (rowSection.isBlank() ? "" : " · " + rowSection);
            result.gradeSectionCounts.merge(group, 1, Integer::sum);
        }
        return result;
    }

    private DateRange parseRange(String from, String to) {
        try {
            LocalDate fromDate = LocalDate.parse(from);
            LocalDate toDate = LocalDate.parse(to);
            if (toDate.isBefore(fromDate)) return new DateRange(fromDate, toDate, "'to' must be on or after 'from'");
            long days = ChronoUnit.DAYS.between(fromDate, toDate) + 1;
            if (days > MAX_RANGE_DAYS) return new DateRange(fromDate, toDate, "Date range cannot exceed " + MAX_RANGE_DAYS + " days");
            return new DateRange(fromDate, toDate, null);
        } catch (DateTimeParseException e) {
            return new DateRange(null, null, "from and to must use YYYY-MM-DD");
        }
    }

    private static String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        // CSV formula injection protection for spreadsheet applications.
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private record DateRange(LocalDate from, LocalDate to, String error) { }
    private record StudentInfo(String studentNumber, String fullName, String grade, String section) { }
    private record ReportRow(String exitLogId, String businessDate, String time, String studentNumber,
                             String studentName, String grade, String section, String guardianName,
                             String staffName, String method) { }

    private static class ReportData {
        private final List<ReportRow> rows = new ArrayList<>();
        private final Set<String> uniqueStudentIds = new HashSet<>();
        private int qrReleases = 0;
        private int manualOverrides = 0;
        private final Map<String, Integer> dailyCounts = new TreeMap<>();
        private final Map<String, Integer> gradeSectionCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }
}

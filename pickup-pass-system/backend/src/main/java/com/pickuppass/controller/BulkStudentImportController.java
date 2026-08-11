package com.pickuppass.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.util.NameFormatter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Production-safe bulk roster import for one school tenant.
 *
 * Flow:
 *   1) Android uploads CSV/XLS/XLSX with dryRun=true.
 *   2) Server validates every row and returns a preview/error list without writing.
 *   3) Admin confirms; the same file is uploaded with dryRun=false.
 *   4) The server re-validates before any write, then inserts the roster in batches.
 *
 * Required columns (case/spacing-insensitive): firstName, lastName, grade, section.
 * Optional: studentNumber/LRN, middleInitial, suffix.
 */
@RestController
@RequestMapping("/api/school-admin/students")
public class BulkStudentImportController {

    private static final int MAX_ROWS = 5000;
    private static final long MAX_FILE_BYTES = 10L * 1024L * 1024L;
    private static final int MAX_REPORTED_ERRORS = 100;
    private static final int FIRESTORE_BATCH_LIMIT = 400;

    private final Firestore firestore;
    private final AuditService auditService;

    public BulkStudentImportController(Firestore firestore, AuditService auditService) {
        this.firestore = firestore;
        this.auditService = auditService;
    }

    @PostMapping(value = "/import", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('school_admin')")
    public ResponseEntity<?> importStudents(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "dryRun", required = false) String dryRunRaw,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        boolean dryRun = dryRunRaw == null || Boolean.parseBoolean(dryRunRaw);
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose a CSV or Excel file"));
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must be 10 MB or smaller"));
        }

        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("upload");
        List<Map<String, String>> rawRows;
        try {
            rawRows = parseFile(file, filename);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }

        if (rawRows.size() > MAX_ROWS) {
            return ResponseEntity.badRequest().body(Map.of("error", "A single import can contain at most " + MAX_ROWS + " students"));
        }

        ImportContext context = loadContext(admin.getSchoolId());
        ValidationResult result = validate(rawRows, context, admin.getSchoolId());

        if (!dryRun && result.invalidRows == 0) {
            int imported = writeStudents(result.validStudents, admin, context);
            result.importedRows = imported;
            auditService.record(admin, "students.bulk_imported", "studentRoster", admin.getSchoolId(), Map.of(
                    "fileName", filename,
                    "totalRows", result.totalRows,
                    "importedRows", imported,
                    "duplicateRows", result.duplicateRows,
                    "academicYearId", context.currentAcademicYearId));
        }

        return ResponseEntity.ok(result.toResponse(dryRun));
    }

    private List<Map<String, String>> parseFile(MultipartFile file, String filename) throws Exception {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".csv") || "text/csv".equalsIgnoreCase(file.getContentType())) {
            return parseCsv(file);
        }
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return parseWorkbook(file);
        }
        throw new IllegalArgumentException("Unsupported file type. Use .csv, .xlsx, or .xls");
    }

    private List<Map<String, String>> parseCsv(MultipartFile file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            Map<String, String> headerMap = normalizeHeaders(parser.getHeaderNames());
            validateRequiredHeaders(headerMap);
            List<Map<String, String>> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                Map<String, String> row = new HashMap<>();
                for (String canonical : CANONICAL_HEADERS) {
                    String actual = headerMap.get(canonical);
                    row.put(canonical, actual == null ? "" : safe(record.isMapped(actual) ? record.get(actual) : ""));
                }
                if (!isBlankRow(row)) rows.add(row);
            }
            return rows;
        }
    }

    private List<Map<String, String>> parseWorkbook(MultipartFile file) throws Exception {
        byte[] bytes = file.getBytes();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new IllegalArgumentException("The spreadsheet is empty");
            }
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw new IllegalArgumentException("The spreadsheet has no header row");

            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) headers.add(formatter.formatCellValue(cell));
            Map<String, String> headerMap = normalizeHeaders(headers);
            validateRequiredHeaders(headerMap);

            Map<String, Integer> actualIndex = new HashMap<>();
            for (int i = 0; i < headers.size(); i++) actualIndex.put(headers.get(i), i);

            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = headerRow.getRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row excelRow = sheet.getRow(r);
                if (excelRow == null) continue;
                Map<String, String> row = new HashMap<>();
                for (String canonical : CANONICAL_HEADERS) {
                    String actual = headerMap.get(canonical);
                    Integer idx = actual == null ? null : actualIndex.get(actual);
                    Cell cell = idx == null ? null : excelRow.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    row.put(canonical, safe(cell == null ? "" : formatter.formatCellValue(cell)));
                }
                if (!isBlankRow(row)) rows.add(row);
            }
            return rows;
        }
    }

    private ValidationResult validate(List<Map<String, String>> rows, ImportContext context, String schoolId) {
        ValidationResult result = new ValidationResult();
        result.totalRows = rows.size();
        Set<String> keysSeenInFile = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            int sheetRow = i + 2;
            Map<String, String> row = rows.get(i);
            List<ImportError> rowErrors = new ArrayList<>();

            String firstName = safe(row.get("firstName"));
            String lastName = safe(row.get("lastName"));
            String middleInitial = safe(row.get("middleInitial"));
            String suffix = safe(row.get("suffix"));
            String studentNumber = safe(row.get("studentNumber"));
            String grade = safe(row.get("grade"));
            String section = safe(row.get("section"));

            if (firstName.isBlank()) rowErrors.add(new ImportError(sheetRow, "firstName", "First name is required"));
            if (lastName.isBlank()) rowErrors.add(new ImportError(sheetRow, "lastName", "Last name is required"));
            if (grade.isBlank()) rowErrors.add(new ImportError(sheetRow, "grade", "Grade is required"));
            if (section.isBlank()) rowErrors.add(new ImportError(sheetRow, "section", "Section is required"));

            GradeSectionPlacement placement = context.resolve(grade, section);
            if (!context.sectionsByKey.isEmpty() && placement == null) {
                rowErrors.add(new ImportError(sheetRow, "grade/section", "Grade and section are not active in the current school year"));
            }

            if (!rowErrors.isEmpty()) {
                result.invalidRows++;
                result.addErrors(rowErrors);
                continue;
            }

            String fullName = NameFormatter.format(lastName, firstName, middleInitial, suffix);
            String duplicateKey = duplicateKey(studentNumber, fullName, grade, section);
            if (context.existingStudentKeys.contains(duplicateKey) || !keysSeenInFile.add(duplicateKey)) {
                result.duplicateRows++;
                continue;
            }

            GradeSectionPlacement effective = placement != null
                    ? placement
                    : new GradeSectionPlacement(grade, section, "", context.currentAcademicYearId, context.currentAcademicYearName);
            ValidStudent student = new ValidStudent(studentNumber, lastName, firstName, middleInitial, suffix,
                    fullName, effective.grade, effective.section, effective.gradeSectionId,
                    effective.academicYearId, effective.academicYearName);
            result.validStudents.add(student);
            if (result.sample.size() < 10) result.sample.add(student.toSample());
        }
        result.validRows = result.validStudents.size();
        return result;
    }

    private ImportContext loadContext(String schoolId) throws Exception {
        String currentYearId = "";
        String currentYearName = "";
        for (QueryDocumentSnapshot doc : firestore.collection("academicYears")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (Boolean.TRUE.equals(doc.getBoolean("isCurrent"))) {
                currentYearId = doc.getId();
                currentYearName = safe(doc.getString("name"));
                break;
            }
        }

        Map<String, GradeSectionPlacement> sections = new HashMap<>();
        for (QueryDocumentSnapshot doc : firestore.collection("gradeSections")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            if (Boolean.FALSE.equals(doc.getBoolean("active"))) continue;
            String academicYearId = safe(doc.getString("academicYearId"));
            if (!currentYearId.isBlank() && !currentYearId.equals(academicYearId)) continue;
            String grade = safe(doc.getString("gradeLevel"));
            String section = safe(doc.getString("sectionName"));
            sections.put(sectionKey(grade, section), new GradeSectionPlacement(
                    grade, section, doc.getId(), academicYearId, safe(doc.getString("academicYearName"))));
        }

        Set<String> existing = new HashSet<>();
        for (QueryDocumentSnapshot doc : firestore.collection("students")
                .whereEqualTo("schoolId", schoolId).get().get().getDocuments()) {
            String studentNumber = safe(doc.getString("studentNumber"));
            String fullName = safe(doc.getString("fullName"));
            String grade = safe(doc.getString("grade"));
            String section = safe(doc.getString("section"));
            existing.add(duplicateKey(studentNumber, fullName, grade, section));
        }

        return new ImportContext(currentYearId, currentYearName, sections, existing);
    }

    private int writeStudents(List<ValidStudent> students, FirebaseUserDetails admin, ImportContext context) throws Exception {
        int imported = 0;
        WriteBatch batch = firestore.batch();
        int batchCount = 0;

        for (ValidStudent s : students) {
            DocumentReference ref = firestore.collection("students").document();
            Map<String, Object> data = new HashMap<>();
            data.put("schoolId", admin.getSchoolId());
            data.put("studentNumber", s.studentNumber);
            data.put("fullName", s.fullName);
            data.put("lastName", s.lastName);
            data.put("firstName", s.firstName);
            data.put("middleInitial", s.middleInitial);
            data.put("suffix", s.suffix);
            data.put("grade", s.grade);
            data.put("section", s.section);
            if (!s.gradeSectionId.isBlank()) data.put("gradeSectionId", s.gradeSectionId);
            if (!s.academicYearId.isBlank()) data.put("academicYearId", s.academicYearId);
            if (!s.academicYearName.isBlank()) data.put("academicYearName", s.academicYearName);
            data.put("status", "active");
            data.put("guardianUids", List.of());
            data.put("guardians", Map.of());
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("createdBy", admin.getUid());
            data.put("imported", true);
            batch.set(ref, data);
            batchCount++;
            imported++;

            if (batchCount >= FIRESTORE_BATCH_LIMIT) {
                batch.commit().get();
                batch = firestore.batch();
                batchCount = 0;
            }
        }
        if (batchCount > 0) batch.commit().get();
        return imported;
    }

    private static final List<String> CANONICAL_HEADERS = List.of(
            "studentNumber", "lastName", "firstName", "middleInitial", "suffix", "grade", "section");

    private static Map<String, String> normalizeHeaders(List<String> headers) {
        Map<String, String> result = new HashMap<>();
        for (String actual : headers) {
            String normalized = normalize(actual);
            if (Set.of("studentnumber", "studentno", "studentid", "lrn", "learnerreferencenumber").contains(normalized)) result.put("studentNumber", actual);
            else if (Set.of("lastname", "surname", "familyname").contains(normalized)) result.put("lastName", actual);
            else if (Set.of("firstname", "givenname").contains(normalized)) result.put("firstName", actual);
            else if (Set.of("middleinitial", "mi", "middlename").contains(normalized)) result.put("middleInitial", actual);
            else if (Set.of("suffix", "namesuffix").contains(normalized)) result.put("suffix", actual);
            else if (Set.of("grade", "gradelevel", "yearlevel").contains(normalized)) result.put("grade", actual);
            else if (Set.of("section", "sectionname", "classsection").contains(normalized)) result.put("section", actual);
        }
        return result;
    }

    private static void validateRequiredHeaders(Map<String, String> headers) {
        List<String> missing = new ArrayList<>();
        for (String required : List.of("lastName", "firstName", "grade", "section")) {
            if (!headers.containsKey(required)) missing.add(required);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required column(s): " + String.join(", ", missing));
        }
    }

    private static boolean isBlankRow(Map<String, String> row) {
        return row.values().stream().allMatch(v -> v == null || v.isBlank());
    }

    private static String duplicateKey(String studentNumber, String fullName, String grade, String section) {
        if (studentNumber != null && !studentNumber.isBlank()) return "id:" + normalize(studentNumber);
        return "name:" + normalize(fullName) + "|" + normalize(grade) + "|" + normalize(section);
    }

    private static String sectionKey(String grade, String section) {
        return normalize(grade) + "|" + normalize(section);
    }

    private static String normalize(String v) {
        return safe(v).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String safe(String v) { return v == null ? "" : v.trim(); }

    private static class ImportContext {
        final String currentAcademicYearId;
        final String currentAcademicYearName;
        final Map<String, GradeSectionPlacement> sectionsByKey;
        final Set<String> existingStudentKeys;

        ImportContext(String currentAcademicYearId, String currentAcademicYearName,
                      Map<String, GradeSectionPlacement> sectionsByKey, Set<String> existingStudentKeys) {
            this.currentAcademicYearId = currentAcademicYearId;
            this.currentAcademicYearName = currentAcademicYearName;
            this.sectionsByKey = sectionsByKey;
            this.existingStudentKeys = existingStudentKeys;
        }

        GradeSectionPlacement resolve(String grade, String section) {
            return sectionsByKey.get(sectionKey(grade, section));
        }
    }

    private static class ValidationResult {
        int totalRows;
        int validRows;
        int invalidRows;
        int duplicateRows;
        int importedRows;
        final List<ImportError> errors = new ArrayList<>();
        final List<Map<String, String>> sample = new ArrayList<>();
        final List<ValidStudent> validStudents = new ArrayList<>();

        void addErrors(List<ImportError> newErrors) {
            for (ImportError error : newErrors) {
                if (errors.size() >= MAX_REPORTED_ERRORS) return;
                errors.add(error);
            }
        }

        Map<String, Object> toResponse(boolean dryRun) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("dryRun", dryRun);
            body.put("totalRows", totalRows);
            body.put("validRows", validRows);
            body.put("invalidRows", invalidRows);
            body.put("duplicateRows", duplicateRows);
            body.put("importedRows", importedRows);
            body.put("readyToImport", invalidRows == 0 && validRows > 0);
            body.put("errors", errors.stream().map(ImportError::toMap).toList());
            body.put("sample", sample);
            return body;
        }
    }

    private record ImportError(int row, String field, String message) {
        Map<String, Object> toMap() { return Map.of("row", row, "field", field, "message", message); }
    }

    private static class GradeSectionPlacement {
        final String grade;
        final String section;
        final String gradeSectionId;
        final String academicYearId;
        final String academicYearName;

        GradeSectionPlacement(String grade, String section, String gradeSectionId, String academicYearId, String academicYearName) {
            this.grade = grade;
            this.section = section;
            this.gradeSectionId = gradeSectionId;
            this.academicYearId = academicYearId;
            this.academicYearName = academicYearName;
        }
    }

    private static class ValidStudent {
        final String studentNumber, lastName, firstName, middleInitial, suffix, fullName;
        final String grade, section, gradeSectionId, academicYearId, academicYearName;

        ValidStudent(String studentNumber, String lastName, String firstName, String middleInitial, String suffix,
                     String fullName, String grade, String section, String gradeSectionId,
                     String academicYearId, String academicYearName) {
            this.studentNumber = studentNumber;
            this.lastName = lastName;
            this.firstName = firstName;
            this.middleInitial = middleInitial;
            this.suffix = suffix;
            this.fullName = fullName;
            this.grade = grade;
            this.section = section;
            this.gradeSectionId = gradeSectionId;
            this.academicYearId = academicYearId;
            this.academicYearName = academicYearName;
        }

        Map<String, String> toSample() {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("studentNumber", studentNumber);
            m.put("fullName", fullName);
            m.put("grade", grade);
            m.put("section", section);
            return m;
        }
    }
}

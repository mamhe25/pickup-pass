package com.pickuppass.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.pickuppass.security.FirebaseUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a tenant-scoped operational export that is downloaded directly by an
 * authorized administrator. The export is deliberately not persisted in
 * Firestore or Cloud Storage, so enabling self-service export adds no recurring
 * storage service of its own.
 *
 * <p>This is a portability/support export, not an automatic production restore
 * mechanism. Platform-level Firestore backup/restore remains master-admin only.</p>
 */
@Service
public class TenantDataExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withLocale(Locale.ROOT).withZone(ZoneOffset.UTC);

    private static final List<String> TENANT_COLLECTIONS = List.of(
            "students",
            "users",
            "academicYears",
            "gradeSections",
            "campuses",
            "pickupGates",
            "broadcastJobs",
            "exitLogs"
    );

    private static final Set<String> EXACT_REDACT_FIELDS = Set.of(
            "password", "passwordHash", "refreshToken", "accessToken", "idToken",
            "fcmToken", "fcmTokens", "verificationCode", "bootstrapSecret",
            "qrSigningSecret", "privateKey", "serviceAccountKey"
    );

    private final Firestore firestore;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final int maxDocuments;
    private final int maxArchiveBytes;

    public TenantDataExportService(
            Firestore firestore,
            ObjectMapper objectMapper,
            AuditService auditService,
            @Value("${pickuppass.data-export.max-documents:25000}") int maxDocuments,
            @Value("${pickuppass.data-export.max-archive-bytes:52428800}") int maxArchiveBytes) {
        this.firestore = firestore;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.maxDocuments = Math.max(1000, Math.min(maxDocuments, 100000));
        this.maxArchiveBytes = Math.max(5 * 1024 * 1024, Math.min(maxArchiveBytes, 200 * 1024 * 1024));
    }

    public ExportResult exportSchool(String schoolId, FirebaseUserDetails actor) throws Exception {
        if (schoolId == null || schoolId.isBlank()) throw new IllegalArgumentException("schoolId is required");
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        if (!school.exists()) throw new IllegalArgumentException("School not found");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxArchiveBytes, 4 * 1024 * 1024));
        int[] documentCount = {0};
        Map<String, Integer> counts = new LinkedHashMap<>();

        try (ZipOutputStream zip = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            Map<String, Object> schoolRecord = sanitizeDocument(school);
            writeJsonEntry(zip, "school.json", schoolRecord);
            documentCount[0]++;
            counts.put("school", 1);

            for (String collection : TENANT_COLLECTIONS) {
                QuerySnapshot snapshot = firestore.collection(collection)
                        .whereEqualTo("schoolId", schoolId)
                        .get().get();
                List<Map<String, Object>> rows = new ArrayList<>();
                for (QueryDocumentSnapshot doc : snapshot.getDocuments()) {
                    enforceDocumentLimit(++documentCount[0]);
                    rows.add(sanitizeDocument(doc));
                }
                counts.put(collection, rows.size());
                writeJsonLinesEntry(zip, collection + ".jsonl", rows);
                enforceArchiveLimit(buffer.size());
            }

            QuerySnapshot auditSnapshot = firestore.collection("schools").document(schoolId)
                    .collection("auditEvents").get().get();
            List<Map<String, Object>> auditRows = new ArrayList<>();
            for (QueryDocumentSnapshot doc : auditSnapshot.getDocuments()) {
                enforceDocumentLimit(++documentCount[0]);
                auditRows.add(sanitizeDocument(doc));
            }
            counts.put("auditEvents", auditRows.size());
            writeJsonLinesEntry(zip, "auditEvents.jsonl", auditRows);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("format", "pickuppass-tenant-export-v1");
            manifest.put("schoolId", schoolId);
            manifest.put("schoolName", school.getString("schoolName"));
            manifest.put("generatedAt", Instant.now().toString());
            manifest.put("generatedByRole", actor == null ? "system" : actor.getRole());
            manifest.put("documentCount", documentCount[0]);
            manifest.put("counts", counts);
            manifest.put("purpose", "tenant_portability_and_support_backup");
            manifest.put("restoreBehavior", "not_automatic");
            manifest.put("sensitiveFieldsRedacted", true);
            manifest.put("notes", List.of(
                    "This archive is downloaded directly and is not stored by PickupPass.",
                    "It is not a replacement for platform-level Firestore backups.",
                    "Authentication secrets, tokens, device sessions, billing internals, and platform security telemetry are excluded."
            ));
            writeJsonEntry(zip, "manifest.json", manifest);
        }

        byte[] bytes = buffer.toByteArray();
        enforceArchiveLimit(bytes.length);
        String fileName = "PickupPass_" + safeFileName(school.getString("schoolName"))
                + "_Data_Export_" + FILE_TS.format(Instant.now()) + ".zip";

        auditService.record(actor, "school.data_export_downloaded", "school", schoolId,
                Map.of("documentCount", documentCount[0], "archiveBytes", bytes.length));
        return new ExportResult(fileName, bytes, documentCount[0]);
    }

    private Map<String, Object> sanitizeDocument(DocumentSnapshot doc) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("_documentId", doc.getId());
        Map<String, Object> source = doc.getData();
        if (source != null) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (shouldRedact(entry.getKey())) continue;
                out.put(entry.getKey(), normalize(entry.getValue()));
            }
        }
        return out;
    }

    private boolean shouldRedact(String key) {
        if (key == null) return false;
        if (EXACT_REDACT_FIELDS.contains(key)) return true;
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("secret")
                || normalized.startsWith("billing")
                || normalized.startsWith("payment")
                || normalized.contains("webhook")
                || normalized.endsWith("token")
                || normalized.endsWith("tokens")
                || normalized.contains("nonce")
                || normalized.contains("sessionkey")
                || normalized.contains("verificationcode");
    }

    @SuppressWarnings("unchecked")
    private Object normalize(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp ts) return ts.toDate().toInstant().toString();
        if (value instanceof Date date) return date.toInstant().toString();
        if (value instanceof DocumentReference ref) return ref.getPath();
        if (value instanceof byte[] bytes) return Base64.getEncoder().encodeToString(bytes);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                if (!shouldRedact(key)) normalized.put(key, normalize(entry.getValue()));
            }
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : collection) normalized.add(normalize(item));
            return normalized;
        }
        return value;
    }

    private void writeJsonEntry(ZipOutputStream zip, String name, Object value) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        zip.closeEntry();
    }

    private void writeJsonLinesEntry(ZipOutputStream zip, String name, List<Map<String, Object>> rows) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        for (Map<String, Object> row : rows) {
            zip.write(objectMapper.writeValueAsBytes(row));
            zip.write('\n');
        }
        zip.closeEntry();
    }

    private void enforceDocumentLimit(int count) {
        if (count > maxDocuments) {
            throw new IllegalStateException("School export exceeds the configured document safety limit");
        }
    }

    private void enforceArchiveLimit(int bytes) {
        if (bytes > maxArchiveBytes) {
            throw new IllegalStateException("School export exceeds the configured archive-size safety limit");
        }
    }

    private String safeFileName(String value) {
        String name = value == null || value.isBlank() ? "School" : value.trim();
        name = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        name = name.replaceAll("_+", "_");
        return name.length() > 50 ? name.substring(0, 50) : name;
    }

    public record ExportResult(String fileName, byte[] bytes, int documentCount) { }
}

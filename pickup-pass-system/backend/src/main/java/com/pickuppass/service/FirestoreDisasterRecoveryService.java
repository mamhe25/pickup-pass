package com.pickuppass.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.pickuppass.security.FirebaseUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Disaster-recovery orchestration around Google Cloud Firestore's native
 * scheduled backups, PITR, delete protection, and isolated restore capability.
 *
 * <p>Important design choice: PickupPass never restores over the production
 * database. Recovery drills always create a separate database. A production
 * cutover remains an explicit infrastructure action outside this service.</p>
 */
@Service
public class FirestoreDisasterRecoveryService {

    private static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";
    private static final String FIRESTORE_API = "https://firestore.googleapis.com/v1/";
    private static final Pattern RESOURCE_SAFE = Pattern.compile("[A-Za-z0-9_()/.:-]+");
    private static final Pattern BACKUP_RESOURCE = Pattern.compile(
            "projects/[a-zA-Z0-9._-]+/locations/[a-zA-Z0-9._-]+/backups/[a-zA-Z0-9._-]+");
    private static final String APPLY_CONFIRMATION = "ENABLE BACKUP PROTECTION";
    private static final String FREE_CONFIRMATION = "ENABLE FREE SAFEGUARDS";
    private static final String STARTUP_CONFIRMATION = "ENABLE STARTUP BACKUP";
    private static final String RESTORE_CONFIRMATION = "RESTORE TO ISOLATED DATABASE";

    private final Firestore firestore;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final boolean allowRestoreDrills;
    private final String projectId;
    private final String databaseId;
    private final String credentialsPath;
    private final int dailyRetentionDays;
    private final int weeklyRetentionDays;
    private final String weeklyDay;
    private final int maxBackupAgeHours;
    private final String defaultProfile;

    private volatile GoogleCredentials credentials;

    public FirestoreDisasterRecoveryService(
            Firestore firestore,
            ObjectMapper objectMapper,
            AuditService auditService,
            @Value("${pickuppass.disaster-recovery.enabled:false}") boolean enabled,
            @Value("${pickuppass.disaster-recovery.allow-restore-drills:false}") boolean allowRestoreDrills,
            @Value("${pickuppass.disaster-recovery.project-id:}") String projectId,
            @Value("${pickuppass.disaster-recovery.database-id:(default)}") String databaseId,
            @Value("${pickuppass.disaster-recovery.credentials-path:}") String credentialsPath,
            @Value("${pickuppass.disaster-recovery.daily-retention-days:14}") int dailyRetentionDays,
            @Value("${pickuppass.disaster-recovery.weekly-retention-days:84}") int weeklyRetentionDays,
            @Value("${pickuppass.disaster-recovery.weekly-day:SUNDAY}") String weeklyDay,
            @Value("${pickuppass.disaster-recovery.max-backup-age-hours:48}") int maxBackupAgeHours,
            @Value("${pickuppass.disaster-recovery.default-profile:startup}") String defaultProfile) {
        this.firestore = firestore;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
        this.enabled = enabled;
        this.allowRestoreDrills = allowRestoreDrills;
        this.projectId = trim(projectId);
        this.databaseId = trim(databaseId).isBlank() ? "(default)" : trim(databaseId);
        this.credentialsPath = trim(credentialsPath);
        this.dailyRetentionDays = clamp(dailyRetentionDays, 1, 98);
        this.weeklyRetentionDays = clamp(weeklyRetentionDays, 1, 98);
        this.weeklyDay = normalizeDay(weeklyDay);
        this.maxBackupAgeHours = clamp(maxBackupAgeHours, 12, 168);
        this.defaultProfile = normalizeProfile(defaultProfile);
    }

    public Map<String, Object> overview() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", enabled);
        response.put("allowRestoreDrills", allowRestoreDrills);
        response.put("databaseId", databaseId);
        response.put("recommendedDailyRetentionDays", dailyRetentionDays);
        response.put("recommendedWeeklyRetentionDays", weeklyRetentionDays);
        response.put("recommendedWeeklyDay", weeklyDay);
        response.put("maxBackupAgeHours", maxBackupAgeHours);
        response.put("defaultProfile", defaultProfile);
        response.put("startupOptimized", true);
        response.put("platformOwnerControlsNativeBackup", true);
        response.put("schoolAdminsCanControlNativeBackup", false);
        response.put("retentionPolicies", retentionPolicies());
        response.put("recoveryJobs", recentRecoveryJobs(10));

        if (!enabled) {
            response.put("configured", false);
            response.put("message", "Disaster recovery integration is disabled. Configure the backend environment before enabling native Firestore protection controls.");
            response.put("backups", List.of());
            return response;
        }

        try {
            validateConfiguration();
            Map<String, Object> protectionControl = reconcileProtectionOperation();
            Map<String, Object> database = getJson(databaseResource());
            String locationId = Objects.toString(database.get("locationId"), "");
            List<Map<String, Object>> schedules = listBackupSchedules();
            Map<String, Object> daily = findSchedule(schedules, "dailyRecurrence");
            Map<String, Object> weekly = findSchedule(schedules, "weeklyRecurrence");
            List<Map<String, Object>> backups = locationId.isBlank() ? List.of() : listBackups(locationId, 20);

            response.put("configured", true);
            response.put("projectId", projectId);
            response.put("locationId", locationId);
            response.put("pitrEnabled", "POINT_IN_TIME_RECOVERY_ENABLED".equals(database.get("pointInTimeRecoveryEnablement")));
            response.put("deleteProtectionEnabled", "DELETE_PROTECTION_ENABLED".equals(database.get("deleteProtectionState")));
            response.put("versionRetentionPeriod", Objects.toString(database.get("versionRetentionPeriod"), ""));
            response.put("earliestVersionTime", Objects.toString(database.get("earliestVersionTime"), ""));
            response.put("dailySchedule", daily);
            response.put("weeklySchedule", weekly);
            response.put("backups", backups);
            Map<String, Object> latestReady = backups.stream().filter(b -> "READY".equals(b.get("state"))).findFirst().orElse(Map.of());
            response.put("latestReadyBackup", latestReady);
            response.put("databaseProtectionUpdatePending", Boolean.TRUE.equals(protectionControl.get("pending")));
            response.put("databaseProtectionUpdateStatus", Objects.toString(protectionControl.get("status"), ""));
            response.put("databaseProtectionOperation", Objects.toString(protectionControl.get("operationName"), ""));
            String activeProfile = normalizeProfile(Objects.toString(protectionControl.get("profile"), defaultProfile));
            response.put("activeProfile", activeProfile);
            long latestBackupAgeHours = backupAgeHours(Objects.toString(latestReady.get("snapshotTime"), ""));
            boolean deleteProtected = "DELETE_PROTECTION_ENABLED".equals(database.get("deleteProtectionState"));
            boolean pitrProtected = "POINT_IN_TIME_RECOVERY_ENABLED".equals(database.get("pointInTimeRecoveryEnablement"));
            boolean recentReadyBackup = latestBackupAgeHours >= 0 && latestBackupAgeHours <= maxBackupAgeHours;
            boolean nativeProtectionConfigured;
            if ("free".equals(activeProfile)) {
                nativeProtectionConfigured = deleteProtected;
            } else if ("growth".equals(activeProfile)) {
                nativeProtectionConfigured = deleteProtected && pitrProtected && daily != null && weekly != null && recentReadyBackup;
            } else {
                nativeProtectionConfigured = deleteProtected && daily != null && recentReadyBackup;
            }
            response.put("latestBackupAgeHours", latestBackupAgeHours);
            response.put("protectionHealthy", nativeProtectionConfigured);
            response.put("healthState", nativeProtectionConfigured ? "healthy" : "warning");
            response.put("paidProtectionStillEnabled", pitrProtected || weekly != null);
            response.put("costProfile", Map.of(
                    "free", "Delete protection only; no scheduled backup created by PickupPass.",
                    "startup", "Delete protection plus one daily backup schedule; PITR and weekly backup are not enabled by this profile.",
                    "growth", "Daily + weekly backups, PITR, and delete protection."
            ));
        } catch (Exception e) {
            response.put("configured", false);
            response.put("message", safeMessage(e));
            response.put("backups", List.of());
        }
        return response;
    }

    @Scheduled(
            initialDelayString = "${pickuppass.disaster-recovery.health-initial-delay-ms:120000}",
            fixedDelayString = "${pickuppass.disaster-recovery.health-scan-ms:21600000}")
    public void refreshHealthSnapshotSafely() {
        if (!enabled) return;
        try {
            Map<String, Object> current = overview();
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("configured", current.getOrDefault("configured", false));
            snapshot.put("healthState", current.getOrDefault("healthState", "unavailable"));
            snapshot.put("protectionHealthy", current.getOrDefault("protectionHealthy", false));
            snapshot.put("pitrEnabled", current.getOrDefault("pitrEnabled", false));
            snapshot.put("deleteProtectionEnabled", current.getOrDefault("deleteProtectionEnabled", false));
            snapshot.put("activeProfile", current.getOrDefault("activeProfile", defaultProfile));
            snapshot.put("latestBackupAgeHours", current.getOrDefault("latestBackupAgeHours", -1));
            snapshot.put("latestReadyBackup", current.getOrDefault("latestReadyBackup", Map.of()));
            snapshot.put("lastCheckedAt", FieldValue.serverTimestamp());
            snapshot.put("message", Objects.toString(current.get("message"), ""));
            firestore.collection("disasterRecoveryHealth").document("global").set(snapshot).get();
        } catch (Exception ignored) {
            // Backup-health telemetry must never impact the student pickup API.
        }
    }

    public Map<String, Object> applyFreeSafeguards(String confirmationText, FirebaseUserDetails actor) throws Exception {
        ensureEnabled();
        validateConfiguration();
        if (!FREE_CONFIRMATION.equals(trim(confirmationText))) {
            throw new IllegalArgumentException("Confirmation text does not match the required free-safeguards phrase");
        }
        Map<String, Object> database = enableDeleteProtectionOnly();
        persistProtectionControl(database, actor, "free");
        boolean pending = Boolean.TRUE.equals(database.get("databaseProtectionUpdatePending"));
        auditService.record(actor, pending ? "disaster_recovery.free_safeguards_requested" : "disaster_recovery.free_safeguards_applied",
                "firestore_database", databaseId, Map.of("profile", "free", "deleteProtection", true));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", pending ? "protection_update_started" : "protection_applied");
        result.put("profile", "free");
        result.put("scheduledBackupCreated", false);
        result.put("pitrRequested", false);
        result.put("deleteProtectionEnabled", true);
        result.put("databaseUpdatePending", pending);
        return result;
    }

    public Map<String, Object> applyStartupProtection(String confirmationText, FirebaseUserDetails actor) throws Exception {
        ensureEnabled();
        validateConfiguration();
        if (!STARTUP_CONFIRMATION.equals(trim(confirmationText))) {
            throw new IllegalArgumentException("Confirmation text does not match the required startup-backup phrase");
        }
        List<Map<String, Object>> schedules = listBackupSchedules();
        Map<String, Object> daily = findSchedule(schedules, "dailyRecurrence");
        daily = upsertBackupSchedule(daily, true, dailyRetentionDays, null);
        Map<String, Object> database = enableDeleteProtectionOnly();
        persistProtectionControl(database, actor, "startup");
        boolean pending = Boolean.TRUE.equals(database.get("databaseProtectionUpdatePending"));
        auditService.record(actor, pending ? "disaster_recovery.startup_protection_requested" : "disaster_recovery.startup_protection_applied",
                "firestore_database", databaseId, Map.of(
                        "profile", "startup",
                        "dailyRetentionDays", dailyRetentionDays,
                        "pitrRequested", false,
                        "weeklyBackupRequested", false,
                        "deleteProtection", true));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", pending ? "protection_update_started" : "protection_applied");
        result.put("profile", "startup");
        result.put("dailySchedule", daily);
        result.put("pitrRequested", false);
        result.put("weeklyBackupRequested", false);
        result.put("deleteProtectionEnabled", true);
        result.put("databaseUpdatePending", pending);
        return result;
    }

    /** Enterprise/growth profile retained as an explicit opt-in. */
    public Map<String, Object> applyRecommendedProtection(String confirmationText, FirebaseUserDetails actor) throws Exception {
        ensureEnabled();
        validateConfiguration();
        if (!APPLY_CONFIRMATION.equals(trim(confirmationText))) {
            throw new IllegalArgumentException("Confirmation text does not match the required protection phrase");
        }

        List<Map<String, Object>> schedules = listBackupSchedules();
        Map<String, Object> daily = findSchedule(schedules, "dailyRecurrence");
        Map<String, Object> weekly = findSchedule(schedules, "weeklyRecurrence");

        daily = upsertBackupSchedule(daily, true, dailyRetentionDays, null);
        weekly = upsertBackupSchedule(weekly, false, weeklyRetentionDays, weeklyDay);
        Map<String, Object> database = enablePitrAndDeleteProtection();
        persistProtectionControl(database, actor, "growth");

        boolean databaseUpdatePending = Boolean.TRUE.equals(database.get("databaseProtectionUpdatePending"));
        boolean pitrEnabledNow = "POINT_IN_TIME_RECOVERY_ENABLED".equals(database.get("pointInTimeRecoveryEnablement"));
        boolean deleteProtectionEnabledNow = "DELETE_PROTECTION_ENABLED".equals(database.get("deleteProtectionState"));
        auditService.record(actor,
                databaseUpdatePending ? "disaster_recovery.protection_requested" : "disaster_recovery.protection_applied",
                "firestore_database", databaseId, Map.of(
                        "dailyRetentionDays", dailyRetentionDays,
                        "weeklyRetentionDays", weeklyRetentionDays,
                        "weeklyDay", weeklyDay,
                        "pitrEnabledNow", pitrEnabledNow,
                        "deleteProtectionEnabledNow", deleteProtectionEnabledNow,
                        "databaseUpdatePending", databaseUpdatePending,
                        "profile", "growth"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", databaseUpdatePending ? "protection_update_started" : "protection_applied");
        result.put("dailySchedule", daily);
        result.put("weeklySchedule", weekly);
        result.put("pitrEnabled", pitrEnabledNow);
        result.put("deleteProtectionEnabled", deleteProtectionEnabledNow);
        result.put("databaseUpdatePending", databaseUpdatePending);
        result.put("databaseProtectionOperation", Objects.toString(database.get("databaseProtectionOperation"), ""));
        return result;
    }

    public Map<String, Object> startRecoveryDrill(String backupName,
                                                  String reason,
                                                  String confirmationText,
                                                  FirebaseUserDetails actor) throws Exception {
        ensureEnabled();
        validateConfiguration();
        if (!allowRestoreDrills) {
            throw new IllegalStateException("Recovery drills are disabled by backend configuration");
        }
        if (!RESTORE_CONFIRMATION.equals(trim(confirmationText))) {
            throw new IllegalArgumentException("Confirmation text does not match the required restore phrase");
        }
        if (trim(reason).length() < 10) {
            throw new IllegalArgumentException("A recovery-drill reason of at least 10 characters is required");
        }
        validateBackupResource(backupName);

        Map<String, Object> backup = getJson(trim(backupName));
        String sourceDatabase = Objects.toString(backup.get("database"), "");
        if (!databaseResource().equals(sourceDatabase)) {
            throw new IllegalArgumentException("Backup does not belong to the configured PickupPass Firestore database");
        }
        if (!"READY".equals(Objects.toString(backup.get("state"), ""))) {
            throw new IllegalStateException("Only a READY backup can be used for a recovery drill");
        }

        String targetDatabaseId = recoveryDatabaseId();
        Map<String, Object> body = Map.of(
                "databaseId", targetDatabaseId,
                "backup", trim(backupName));
        Map<String, Object> operation = postJson("projects/" + projectId + "/databases:restore", body);
        String operationName = Objects.toString(operation.get("name"), "");
        if (operationName.isBlank()) {
            throw new IllegalStateException("Firestore restore did not return a long-running operation name");
        }

        DocumentReference ref = firestore.collection("disasterRecoveryJobs").document();
        Map<String, Object> job = new HashMap<>();
        job.put("kind", "isolated_restore_drill");
        job.put("status", "running");
        job.put("backupName", trim(backupName));
        job.put("backupSnapshotTime", Objects.toString(backup.get("snapshotTime"), ""));
        job.put("targetDatabaseId", targetDatabaseId);
        job.put("operationName", operationName);
        job.put("reason", trim(reason));
        job.put("requestedBy", actor == null ? "system" : actor.getUid());
        job.put("requestedAt", FieldValue.serverTimestamp());
        job.put("lastCheckedAt", FieldValue.serverTimestamp());
        job.put("productionCutoverAutomatic", false);
        ref.set(job).get();

        auditService.record(actor, "disaster_recovery.restore_drill_started", "recovery_job", ref.getId(), Map.of(
                "backupName", trim(backupName),
                "targetDatabaseId", targetDatabaseId,
                "operationName", operationName));

        DocumentSnapshot stored = ref.get().get();
        return normalizeJob(stored);
    }

    public Map<String, Object> refreshRecoveryJob(String jobId, FirebaseUserDetails actor) throws Exception {
        ensureEnabled();
        validateConfiguration();
        String safeJobId = requireSimpleId(jobId, "jobId");
        DocumentReference ref = firestore.collection("disasterRecoveryJobs").document(safeJobId);
        DocumentSnapshot doc = ref.get().get();
        if (!doc.exists()) throw new IllegalArgumentException("Recovery job not found");

        String operationName = Objects.toString(doc.get("operationName"), "");
        validateOperationResource(operationName);
        Map<String, Object> operation = getJson(operationName);
        boolean done = Boolean.TRUE.equals(operation.get("done"));

        Map<String, Object> update = new HashMap<>();
        update.put("lastCheckedAt", FieldValue.serverTimestamp());
        update.put("operationDone", done);
        if (done) {
            if (operation.containsKey("error")) {
                update.put("status", "failed");
                update.put("error", safeJson(operation.get("error"), 1000));
                update.put("completedAt", FieldValue.serverTimestamp());
            } else {
                String targetDatabaseId = Objects.toString(doc.get("targetDatabaseId"), "");
                Map<String, Object> restoredDatabase = getJson(databaseResource(targetDatabaseId));
                String restoredBackup = extractRestoredBackup(restoredDatabase);
                String expectedBackup = Objects.toString(doc.get("backupName"), "");
                boolean verified = expectedBackup.equals(restoredBackup);
                update.put("status", verified ? "verified" : "restore_completed_unverified");
                update.put("sourceVerified", verified);
                update.put("restoredDatabaseName", Objects.toString(restoredDatabase.get("name"), ""));
                update.put("completedAt", FieldValue.serverTimestamp());
            }
        }
        ref.update(update).get();

        auditService.record(actor, "disaster_recovery.restore_drill_refreshed", "recovery_job", safeJobId, Map.of(
                "done", done,
                "status", Objects.toString(update.getOrDefault("status", doc.get("status")), "running")));
        return normalizeJob(ref.get().get());
    }

    private Map<String, Object> reconcileProtectionOperation() {
        try {
            DocumentReference ref = firestore.collection("disasterRecoveryControl").document("global");
            DocumentSnapshot doc = ref.get().get();
            if (!doc.exists()) return Map.of();

            String operationName = Objects.toString(doc.get("operationName"), "");
            boolean pending = Boolean.TRUE.equals(doc.getBoolean("pending"));
            if (!pending || operationName.isBlank()) {
                return Map.of(
                        "pending", false,
                        "status", Objects.toString(doc.get("status"), ""),
                        "operationName", operationName,
                        "profile", normalizeProfile(Objects.toString(doc.get("profile"), defaultProfile)));
            }

            validateOperationResource(operationName);
            Map<String, Object> operation = getJson(operationName);
            if (!Boolean.TRUE.equals(operation.get("done"))) {
                return Map.of("pending", true, "status", "running", "operationName", operationName,
                        "profile", normalizeProfile(Objects.toString(doc.get("profile"), defaultProfile)));
            }

            Map<String, Object> update = new HashMap<>();
            update.put("pending", false);
            update.put("completedAt", FieldValue.serverTimestamp());
            if (operation.containsKey("error")) {
                update.put("status", "failed");
                update.put("lastError", safeJson(operation.get("error"), 700));
            } else {
                update.put("status", "applied");
                update.put("lastError", "");
            }
            ref.update(update).get();
            return Map.of(
                    "pending", false,
                    "status", Objects.toString(update.get("status"), ""),
                    "operationName", operationName,
                    "profile", normalizeProfile(Objects.toString(doc.get("profile"), defaultProfile)));
        } catch (Exception e) {
            return Map.of("pending", true, "status", "monitor_unavailable", "operationName", "");
        }
    }

    private void persistProtectionControl(Map<String, Object> database, FirebaseUserDetails actor, String profile) {
        try {
            boolean pending = Boolean.TRUE.equals(database.get("databaseProtectionUpdatePending"));
            String operationName = Objects.toString(database.get("databaseProtectionOperation"), "");
            Map<String, Object> control = new LinkedHashMap<>();
            control.put("pending", pending);
            control.put("status", pending ? "running" : "applied");
            control.put("operationName", operationName);
            control.put("requestedBy", actor == null ? "system" : actor.getUid());
            control.put("profile", normalizeProfile(profile));
            control.put("updatedAt", FieldValue.serverTimestamp());
            if (pending) control.put("requestedAt", FieldValue.serverTimestamp());
            firestore.collection("disasterRecoveryControl").document("global").set(control).get();
        } catch (Exception ignored) {
            // Control telemetry must not turn a successful Google Cloud request into a failure.
        }
    }

    private Map<String, Object> enableDeleteProtectionOnly() throws Exception {
        Map<String, Object> current = getJson(databaseResource());
        boolean deleteProtectionAlreadyEnabled = "DELETE_PROTECTION_ENABLED".equals(current.get("deleteProtectionState"));
        if (deleteProtectionAlreadyEnabled) {
            Map<String, Object> ready = new LinkedHashMap<>(current);
            ready.put("databaseProtectionUpdatePending", false);
            return ready;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", databaseResource());
        body.put("deleteProtectionState", "DELETE_PROTECTION_ENABLED");
        Map<String, Object> operation = patchJson(databaseResource()
                + "?updateMask=deleteProtectionState", body);
        String operationName = Objects.toString(operation.get("name"), "");
        validateOperationResource(operationName);
        if (operation.containsKey("error")) {
            throw new IllegalStateException("Firestore configuration operation failed: " + safeJson(operation.get("error"), 700));
        }
        if (Boolean.TRUE.equals(operation.get("done"))) {
            Map<String, Object> completed = new LinkedHashMap<>(getJson(databaseResource()));
            completed.put("databaseProtectionUpdatePending", false);
            completed.put("databaseProtectionOperation", operationName);
            return completed;
        }
        Map<String, Object> pending = new LinkedHashMap<>(current);
        pending.put("databaseProtectionUpdatePending", true);
        pending.put("databaseProtectionOperation", operationName);
        return pending;
    }

    private Map<String, Object> enablePitrAndDeleteProtection() throws Exception {
        Map<String, Object> current = getJson(databaseResource());
        boolean pitrAlreadyEnabled = "POINT_IN_TIME_RECOVERY_ENABLED".equals(current.get("pointInTimeRecoveryEnablement"));
        boolean deleteProtectionAlreadyEnabled = "DELETE_PROTECTION_ENABLED".equals(current.get("deleteProtectionState"));
        if (pitrAlreadyEnabled && deleteProtectionAlreadyEnabled) {
            Map<String, Object> ready = new LinkedHashMap<>(current);
            ready.put("databaseProtectionUpdatePending", false);
            return ready;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", databaseResource());
        body.put("pointInTimeRecoveryEnablement", "POINT_IN_TIME_RECOVERY_ENABLED");
        body.put("deleteProtectionState", "DELETE_PROTECTION_ENABLED");
        Map<String, Object> operation = patchJson(databaseResource()
                + "?updateMask=pointInTimeRecoveryEnablement%2CdeleteProtectionState", body);
        String operationName = Objects.toString(operation.get("name"), "");
        validateOperationResource(operationName);
        if (operation.containsKey("error")) {
            throw new IllegalStateException("Firestore configuration operation failed: " + safeJson(operation.get("error"), 700));
        }
        if (Boolean.TRUE.equals(operation.get("done"))) {
            Map<String, Object> completed = new LinkedHashMap<>(getJson(databaseResource()));
            completed.put("databaseProtectionUpdatePending", false);
            completed.put("databaseProtectionOperation", operationName);
            return completed;
        }

        // Database PATCH returns a long-running operation. Do not hold the Android
        // request open while Google Cloud applies it; the overview/health poll will
        // observe authoritative state on the next refresh.
        Map<String, Object> pending = new LinkedHashMap<>(current);
        pending.put("databaseProtectionUpdatePending", true);
        pending.put("databaseProtectionOperation", operationName);
        return pending;
    }

    private Map<String, Object> upsertBackupSchedule(Map<String, Object> existing,
                                                     boolean daily,
                                                     int retentionDays,
                                                     String requestedWeeklyDay) throws Exception {
        String retention = TimeUnit.DAYS.toSeconds(retentionDays) + "s";
        if (existing == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("retention", retention);
            if (daily) body.put("dailyRecurrence", Map.of());
            else body.put("weeklyRecurrence", Map.of("day", normalizeDay(requestedWeeklyDay)));
            return postJson(databaseResource() + "/backupSchedules", body);
        }

        String name = Objects.toString(existing.get("name"), "");
        validateInternalResource(name, "backup schedule");
        long existingDays = durationSecondsToDays(Objects.toString(existing.get("retention"), ""));
        int effectiveRetentionDays = (int) Math.max(retentionDays, existingDays);
        if (existingDays >= retentionDays) {
            Map<String, Object> preserved = new LinkedHashMap<>(existing);
            preserved.put("retentionDays", existingDays);
            return preserved;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("retention", TimeUnit.DAYS.toSeconds(effectiveRetentionDays) + "s");
        Map<String, Object> updated = patchJson(name + "?updateMask=retention", body);
        updated.put("retentionDays", effectiveRetentionDays);
        return updated;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listBackupSchedules() throws Exception {
        Map<String, Object> response = getJson(databaseResource() + "/backupSchedules");
        Object raw = response.get("backupSchedules");
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) out.add((Map<String, Object>) map);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listBackups(String locationId, int max) throws Exception {
        String location = requireSegment(locationId, "locationId");
        Map<String, Object> response = getJson("projects/" + projectId + "/locations/" + location + "/backups?pageSize=100");
        Object raw = response.get("backups");
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            Map<String, Object> backup = (Map<String, Object>) map;
            if (!databaseResource().equals(Objects.toString(backup.get("database"), ""))) continue;
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("name", Objects.toString(backup.get("name"), ""));
            normalized.put("database", Objects.toString(backup.get("database"), ""));
            normalized.put("state", Objects.toString(backup.get("state"), ""));
            normalized.put("snapshotTime", Objects.toString(backup.get("snapshotTime"), ""));
            normalized.put("expireTime", Objects.toString(backup.get("expireTime"), ""));
            normalized.put("stats", backup.getOrDefault("stats", Map.of()));
            out.add(normalized);
        }
        out.sort((a, b) -> Objects.toString(b.get("snapshotTime"), "")
                .compareTo(Objects.toString(a.get("snapshotTime"), "")));
        return out.size() <= max ? out : new ArrayList<>(out.subList(0, max));
    }

    private List<Map<String, Object>> recentRecoveryJobs(int limit) {
        try {
            QuerySnapshot snapshot = firestore.collection("disasterRecoveryJobs")
                    .orderBy("requestedAt", Query.Direction.DESCENDING)
                    .limit(Math.max(1, Math.min(limit, 25)))
                    .get().get();
            List<Map<String, Object>> out = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) out.add(normalizeJob(doc));
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Map<String, Object> normalizeJob(DocumentSnapshot doc) {
        Map<String, Object> item = new LinkedHashMap<>(doc.getData() == null ? Map.of() : doc.getData());
        item.put("jobId", doc.getId());
        for (String key : List.of("requestedAt", "lastCheckedAt", "completedAt")) {
            Object value = item.get(key);
            if (value instanceof Timestamp timestamp) item.put(key, timestamp.toDate().toInstant().toString());
        }
        return item;
    }

    private List<Map<String, Object>> retentionPolicies() {
        List<Map<String, Object>> policies = new ArrayList<>();
        policies.add(retention("securityAuthWindows", "ephemeral_security_telemetry", 7, "expiresAt", true,
                "Authentication-failure aggregation only; no raw token is stored."));
        policies.add(retention("idempotencyKeys", "ephemeral_request_safety", 7, "expiresAt", true,
                "Replay-prevention records can expire after their retry window."));
        policies.add(retention("exitLogs", "student_release_record", -1, "", false,
                "Never auto-delete without an explicit school/legal retention policy."));
        policies.add(retention("systemAuditEvents + school auditEvents", "privileged_audit", -1, "", false,
                "Append-only audit history; no automatic deletion by PickupPass."));
        policies.add(retention("billingInvoices/payment notices/payment events", "financial_record", -1, "", false,
                "Financial records are not auto-deleted by the application."));
        return policies;
    }

    private Map<String, Object> retention(String collection, String classification, int days,
                                          String ttlField, boolean ttlEligible, String note) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("collection", collection);
        item.put("classification", classification);
        item.put("retentionDays", days);
        item.put("ttlField", ttlField);
        item.put("ttlEligible", ttlEligible);
        item.put("automaticDeletionEnabledByCode", false);
        item.put("note", note);
        return item;
    }

    private Map<String, Object> findSchedule(List<Map<String, Object>> schedules, String recurrenceField) {
        for (Map<String, Object> schedule : schedules) {
            if (schedule.containsKey(recurrenceField)) {
                Map<String, Object> normalized = new LinkedHashMap<>(schedule);
                Object retention = schedule.get("retention");
                normalized.put("retentionDays", durationSecondsToDays(Objects.toString(retention, "")));
                return normalized;
            }
        }
        return null;
    }

    private long backupAgeHours(String snapshotTime) {
        if (snapshotTime == null || snapshotTime.isBlank()) return -1;
        try {
            long seconds = java.time.Duration.between(Instant.parse(snapshotTime), Instant.now()).getSeconds();
            return Math.max(0, seconds / 3600);
        } catch (Exception ignored) {
            return -1;
        }
    }

    private long durationSecondsToDays(String duration) {
        try {
            String raw = duration.endsWith("s") ? duration.substring(0, duration.length() - 1) : duration;
            return Math.round(Double.parseDouble(raw) / 86400.0d);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String extractRestoredBackup(Map<String, Object> database) {
        Object sourceInfo = database.get("sourceInfo");
        if (!(sourceInfo instanceof Map<?, ?> source)) return "";
        Object backup = source.get("backup");
        if (!(backup instanceof Map<?, ?> backupSource)) return "";
        return Objects.toString(backupSource.get("backup"), "");
    }

    private Map<String, Object> getJson(String resourceOrUrl) throws Exception {
        return request("GET", resourceOrUrl, null);
    }

    private Map<String, Object> postJson(String resourceOrUrl, Map<String, Object> body) throws Exception {
        return request("POST", resourceOrUrl, body);
    }

    private Map<String, Object> patchJson(String resourceOrUrl, Map<String, Object> body) throws Exception {
        return request("PATCH", resourceOrUrl, body);
    }

    private Map<String, Object> request(String method, String resourceOrUrl, Map<String, Object> body) throws Exception {
        String url = resourceOrUrl.startsWith("https://") ? resourceOrUrl : FIRESTORE_API + resourceOrUrl;
        if (!url.startsWith(FIRESTORE_API)) throw new IllegalArgumentException("Unsupported disaster-recovery API target");

        GoogleCredentials creds = credentials();
        creds.refreshIfExpired();
        if (creds.getAccessToken() == null || creds.getAccessToken().getTokenValue() == null) {
            creds.refresh();
        }
        String token = creds.getAccessToken().getTokenValue();

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        if (body != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String sanitized = sanitizeGoogleError(response.body());
            throw new IllegalStateException("Firestore disaster-recovery API returned HTTP " + response.statusCode()
                    + (sanitized.isBlank() ? "" : ": " + sanitized));
        }
        if (response.body() == null || response.body().isBlank()) return Map.of();
        return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }

    private synchronized GoogleCredentials credentials() throws IOException {
        if (credentials != null) return credentials;
        GoogleCredentials base;
        if (!credentialsPath.isBlank()) {
            Path path = Path.of(credentialsPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Configured disaster-recovery credential file does not exist");
            }
            try (InputStream input = Files.newInputStream(path)) {
                base = GoogleCredentials.fromStream(input);
            }
        } else {
            base = GoogleCredentials.getApplicationDefault();
        }
        credentials = base.createScoped(List.of(CLOUD_PLATFORM_SCOPE));
        return credentials;
    }

    private String databaseResource() {
        return databaseResource(databaseId);
    }

    private String databaseResource(String id) {
        String safeDatabaseId = requireDatabaseId(id);
        return "projects/" + projectId + "/databases/" + safeDatabaseId;
    }

    private String recoveryDatabaseId() {
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
                .withZone(ZoneOffset.UTC).format(Instant.now());
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        return "pickuppass-recovery-" + stamp + "-" + suffix;
    }

    private void validateConfiguration() {
        if (projectId.isBlank()) {
            throw new IllegalStateException("FIRESTORE_DR_PROJECT_ID is required when disaster recovery is enabled");
        }
        requireSegment(projectId, "projectId");
        requireDatabaseId(databaseId);
    }

    private void ensureEnabled() {
        if (!enabled) throw new IllegalStateException("Disaster recovery integration is disabled");
    }

    private void validateBackupResource(String backupName) {
        String value = trim(backupName);
        if (!BACKUP_RESOURCE.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Firestore backup resource name");
        }
        if (!value.startsWith("projects/" + projectId + "/locations/")) {
            throw new IllegalArgumentException("Backup belongs to a different Google Cloud project");
        }
    }

    private void validateOperationResource(String operationName) {
        String value = trim(operationName);
        if (value.isBlank() || !RESOURCE_SAFE.matcher(value).matches()
                || !value.startsWith("projects/" + projectId + "/")) {
            throw new IllegalArgumentException("Invalid Firestore operation resource name");
        }
    }

    private void validateInternalResource(String resource, String label) {
        String value = trim(resource);
        if (value.isBlank() || !RESOURCE_SAFE.matcher(value).matches()
                || !value.startsWith("projects/" + projectId + "/")) {
            throw new IllegalArgumentException("Invalid " + label + " resource name");
        }
    }

    private String requireDatabaseId(String value) {
        String id = trim(value);
        if ("(default)".equals(id)) return id;
        if (!id.matches("[a-z][a-z0-9-]{2,61}[a-z0-9]")) {
            throw new IllegalArgumentException("Invalid Firestore database ID");
        }
        return id;
    }

    private String requireSegment(String value, String name) {
        String v = trim(value);
        if (v.isBlank() || !v.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return v;
    }

    private String requireSimpleId(String value, String name) {
        String v = trim(value);
        if (v.isBlank() || !v.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return v;
    }

    private static String normalizeProfile(String value) {
        String profile = trim(value).toLowerCase(Locale.ROOT);
        return Set.of("free", "startup", "growth").contains(profile) ? profile : "startup";
    }

    private static String normalizeDay(String value) {
        String day = trim(value).toUpperCase(Locale.ROOT);
        Set<String> valid = Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        return valid.contains(day) ? day : "SUNDAY";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) return "Disaster recovery status is currently unavailable";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private String sanitizeGoogleError(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            Map<String, Object> parsed = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Object error = parsed.get("error");
            if (error instanceof Map<?, ?> map) {
                String message = Objects.toString(map.get("message"), "");
                return message.length() <= 500 ? message : message.substring(0, 500);
            }
        } catch (Exception ignored) { }
        return "Google Cloud request failed";
    }

    private String safeJson(Object value, int max) {
        try {
            String text = objectMapper.writeValueAsString(value);
            return text.length() <= max ? text : text.substring(0, max);
        } catch (Exception ignored) {
            return "operation_failed";
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}

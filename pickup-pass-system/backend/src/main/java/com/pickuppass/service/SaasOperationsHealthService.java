package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Materializes a small, actionable operations layer for the SaaS owner.
 *
 * The source of truth stays in schools, tenantUsage, billingInvoices and
 * billingPaymentNotices. This service only creates deduplicated alert records
 * so opening the master console does not repeatedly recompute every business
 * rule or scan operational student data.
 */
@Service
public class SaasOperationsHealthService {
    private static final Logger log = LoggerFactory.getLogger(SaasOperationsHealthService.class);
    private static final int MAX_ACTIVE_RECORD_SCAN = 5000;

    public static final String HEALTHY = "healthy";
    public static final String ATTENTION = "attention_needed";
    public static final String BILLING_RISK = "billing_risk";
    public static final String OVER_QUOTA = "over_quota";
    public static final String SUSPENDED = "suspended";

    private final Firestore firestore;
    private final TenantUsageService tenantUsageService;
    private final int quotaWarningPercent;
    private final long staleHours;
    private final AtomicBoolean refreshRunning = new AtomicBoolean(false);

    public SaasOperationsHealthService(
            Firestore firestore,
            TenantUsageService tenantUsageService,
            @Value("${pickuppass.operations.quota-warning-percent:90}") int quotaWarningPercent,
            @Value("${pickuppass.operations.stale-hours:24}") long staleHours) {
        this.firestore = firestore;
        this.tenantUsageService = tenantUsageService;
        this.quotaWarningPercent = Math.max(50, Math.min(99, quotaWarningPercent));
        this.staleHours = Math.max(1, staleHours);
    }

    @Scheduled(fixedDelayString = "${pickuppass.operations.health-scan-ms:86400000}")
    public void scheduledRefresh() {
        try {
            refreshAll();
        } catch (Exception e) {
            log.warn("SaaS operations health refresh failed; tenant operations are unaffected", e);
        }
    }

    /** Refreshes the materialized alert set. Safe to call manually; overlapping scans are skipped. */
    public RefreshResult refreshAll() throws Exception {
        if (!refreshRunning.compareAndSet(false, true)) {
            return new RefreshResult(false, 0, 0, "already_running");
        }
        try {
            Instant now = Instant.now();
            List<DocumentSnapshot> schools = firestore.collection("schools").get().get().getDocuments();
            Map<String, List<DocumentSnapshot>> invoicesBySchool = new HashMap<>();
            Map<String, List<DocumentSnapshot>> pendingPaymentsBySchool = new HashMap<>();
            Map<String, List<DocumentSnapshot>> emailFailuresBySchool = new HashMap<>();

            for (String status : List.of("open", "overdue")) {
                QuerySnapshot snap = firestore.collection("billingInvoices")
                        .whereEqualTo("status", status).limit(MAX_ACTIVE_RECORD_SCAN).get().get();
                for (DocumentSnapshot d : snap.getDocuments()) {
                    String schoolId = text(d, "schoolId", "");
                    if (!schoolId.isBlank()) invoicesBySchool.computeIfAbsent(schoolId, k -> new ArrayList<>()).add(d);
                }
                if (snap.size() >= MAX_ACTIVE_RECORD_SCAN) log.warn("Operations invoice scan reached safety cap for status={}", status);
            }

            QuerySnapshot pending = firestore.collection("billingPaymentNotices")
                    .whereEqualTo("status", "pending_review").limit(MAX_ACTIVE_RECORD_SCAN).get().get();
            for (DocumentSnapshot d : pending.getDocuments()) {
                String schoolId = text(d, "schoolId", "");
                if (!schoolId.isBlank()) pendingPaymentsBySchool.computeIfAbsent(schoolId, k -> new ArrayList<>()).add(d);
            }
            if (pending.size() >= MAX_ACTIVE_RECORD_SCAN) log.warn("Operations pending-payment scan reached safety cap");

            QuerySnapshot failedEmail = firestore.collection("billingInvoices")
                    .whereEqualTo("emailDeliveryFailed", true).limit(MAX_ACTIVE_RECORD_SCAN).get().get();
            for (DocumentSnapshot d : failedEmail.getDocuments()) {
                String schoolId = text(d, "schoolId", "");
                if (!schoolId.isBlank()) emailFailuresBySchool.computeIfAbsent(schoolId, k -> new ArrayList<>()).add(d);
            }

            QuerySnapshot activeAlertSnap = firestore.collection("saasOperationalAlerts")
                    .whereEqualTo("active", true).limit(MAX_ACTIVE_RECORD_SCAN).get().get();
            Map<String, DocumentSnapshot> existingActive = new HashMap<>();
            for (DocumentSnapshot d : activeAlertSnap.getDocuments()) existingActive.put(d.getId(), d);

            Set<String> desired = new HashSet<>();
            int generated = 0;
            for (DocumentSnapshot school : schools) {
                String schoolId = school.getId();
                String schoolName = text(school, "schoolName", "Unnamed school");
                List<AlertSpec> specs = buildAlertSpecs(
                        school,
                        tenantUsageService.snapshot(schoolId),
                        invoicesBySchool.getOrDefault(schoolId, List.of()),
                        pendingPaymentsBySchool.getOrDefault(schoolId, List.of()),
                        emailFailuresBySchool.getOrDefault(schoolId, List.of()),
                        now
                );
                for (AlertSpec spec : specs) {
                    String id = alertId(spec.type(), spec.resourceId());
                    desired.add(id);
                    upsertAlert(id, schoolId, schoolName, spec, now, existingActive.get(id));
                    generated++;
                }
            }

            int resolved = 0;
            for (Map.Entry<String, DocumentSnapshot> entry : existingActive.entrySet()) {
                if (desired.contains(entry.getKey())) continue;
                entry.getValue().getReference().update(
                        "active", false,
                        "resolvedAt", FieldValue.serverTimestamp(),
                        "lastSeenAt", FieldValue.serverTimestamp()
                ).get();
                resolved++;
            }

            firestore.collection("saasOperationsMetadata").document("global").set(Map.of(
                    "lastScanAt", Date.from(now),
                    "schoolCount", schools.size(),
                    "activeAlertCount", generated,
                    "resolvedAlertCount", resolved,
                    "updatedAt", FieldValue.serverTimestamp()
            ), SetOptions.merge()).get();
            return new RefreshResult(true, generated, resolved, "completed");
        } finally {
            refreshRunning.set(false);
        }
    }

    /**
     * Recomputes one tenant after a master-admin mutation. This keeps the UI
     * current without turning every plan/billing action into a platform-wide scan.
     */
    public void refreshSchool(String schoolId) {
        if (schoolId == null || schoolId.isBlank()) return;
        try {
            DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
            if (!school.exists()) return;

            QuerySnapshot invoiceSnap = firestore.collection("billingInvoices")
                    .whereEqualTo("schoolId", schoolId).limit(500).get().get();
            List<DocumentSnapshot> invoices = new ArrayList<>();
            List<DocumentSnapshot> emailFailures = new ArrayList<>();
            for (DocumentSnapshot d : invoiceSnap.getDocuments()) {
                String status = text(d, "status", "open");
                if ("open".equals(status) || "overdue".equals(status)) invoices.add(d);
                if (Boolean.TRUE.equals(d.getBoolean("emailDeliveryFailed"))) emailFailures.add(d);
            }

            QuerySnapshot noticeSnap = firestore.collection("billingPaymentNotices")
                    .whereEqualTo("schoolId", schoolId).limit(500).get().get();
            List<DocumentSnapshot> pendingPayments = new ArrayList<>();
            for (DocumentSnapshot d : noticeSnap.getDocuments()) {
                if ("pending_review".equals(text(d, "status", ""))) pendingPayments.add(d);
            }

            QuerySnapshot existingSnap = firestore.collection("saasOperationalAlerts")
                    .whereEqualTo("schoolId", schoolId).limit(500).get().get();
            Map<String,DocumentSnapshot> existing = new HashMap<>();
            for (DocumentSnapshot d : existingSnap.getDocuments()) {
                if (Boolean.TRUE.equals(d.getBoolean("active"))) existing.put(d.getId(), d);
            }

            Instant now = Instant.now();
            List<AlertSpec> specs = buildAlertSpecs(
                    school,
                    tenantUsageService.snapshot(schoolId),
                    invoices,
                    pendingPayments,
                    emailFailures,
                    now
            );
            Set<String> desired = new HashSet<>();
            String schoolName = text(school, "schoolName", "Unnamed school");
            for (AlertSpec spec : specs) {
                String id = alertId(spec.type(), spec.resourceId());
                desired.add(id);
                upsertAlert(id, schoolId, schoolName, spec, now, existing.get(id));
            }
            for (Map.Entry<String,DocumentSnapshot> entry : existing.entrySet()) {
                if (desired.contains(entry.getKey())) continue;
                entry.getValue().getReference().update(
                        "active", false,
                        "resolvedAt", FieldValue.serverTimestamp(),
                        "lastSeenAt", FieldValue.serverTimestamp()
                ).get();
            }
        } catch (Exception e) {
            log.warn("Could not refresh operations health for schoolId={}", schoolId, e);
        }
    }

    /** Refreshes on first use or when the materialized operations view is stale. */
    public void refreshIfStale() {
        try {
            DocumentSnapshot meta = firestore.collection("saasOperationsMetadata").document("global").get().get();
            Timestamp last = meta.getTimestamp("lastScanAt");
            if (!meta.exists() || last == null || last.toDate().toInstant().isBefore(Instant.now().minus(Duration.ofHours(staleHours)))) {
                refreshAll();
            }
        } catch (Exception e) {
            log.warn("Could not refresh stale SaaS operations view; returning last known state", e);
        }
    }

    /** Immediate event-driven alert for the highest-value startup workflow: GCash review. */
    public void signalPendingGcash(DocumentSnapshot notice) {
        try {
            if (notice == null || !notice.exists()) return;
            String schoolId = text(notice, "schoolId", "");
            if (schoolId.isBlank()) return;
            DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
            String schoolName = school.exists() ? text(school, "schoolName", "Unnamed school") : "Unnamed school";
            String invoiceNumber = text(notice, "invoiceNumber", text(notice, "invoiceId", "invoice"));
            AlertSpec spec = new AlertSpec(
                    "pending_gcash", "warning", ATTENTION,
                    "GCash payment needs verification",
                    "A school submitted payment for " + invoiceNumber + ". Verify it against the receiving GCash transaction history.",
                    "review_payment", notice.getId()
            );
            upsertAlert(alertId(spec.type(), spec.resourceId()), schoolId, schoolName, spec, Instant.now(), null);
        } catch (Exception e) {
            log.warn("Could not create immediate GCash operations alert noticeId={}", notice == null ? "" : notice.getId(), e);
        }
    }

    public void signalBillingEmailFailure(DocumentReference invoiceRef, String failureType, String message) {
        try {
            DocumentSnapshot invoice = invoiceRef.get().get();
            if (!invoice.exists()) return;
            String schoolId = text(invoice, "schoolId", "");
            if (schoolId.isBlank()) return;
            DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
            String schoolName = school.exists() ? text(school, "schoolName", "Unnamed school") : text(invoice, "schoolNameSnapshot", "Unnamed school");
            String invoiceNumber = text(invoice, "invoiceNumber", invoice.getId());
            AlertSpec spec = new AlertSpec(
                    "billing_email_failed", "warning", ATTENTION,
                    "Billing email delivery failed",
                    "Email delivery failed for " + invoiceNumber + " (" + clean(failureType, 60) + "). " + clean(message, 180),
                    "billing", invoice.getId()
            );
            upsertAlert(alertId(spec.type(), spec.resourceId()), schoolId, schoolName, spec, Instant.now(), null);
        } catch (Exception e) {
            log.warn("Could not create billing-email operations alert invoiceId={}", invoiceRef.getId(), e);
        }
    }

    public void resolve(String type, String resourceId) {
        try {
            DocumentReference ref = firestore.collection("saasOperationalAlerts").document(alertId(type, resourceId));
            DocumentSnapshot d = ref.get().get();
            if (d.exists() && Boolean.TRUE.equals(d.getBoolean("active"))) {
                ref.update("active", false, "resolvedAt", FieldValue.serverTimestamp(), "lastSeenAt", FieldValue.serverTimestamp()).get();
            }
        } catch (Exception e) {
            log.warn("Could not resolve SaaS operations alert type={} resourceId={}", type, resourceId, e);
        }
    }

    public Map<String, Object> overview() throws Exception {
        refreshIfStale();
        QuerySnapshot schoolSnap = firestore.collection("schools").get().get();
        QuerySnapshot alertSnap = firestore.collection("saasOperationalAlerts")
                .whereEqualTo("active", true).limit(500).get().get();

        Map<String, List<DocumentSnapshot>> alertsBySchool = new HashMap<>();
        List<DocumentSnapshot> alerts = new ArrayList<>(alertSnap.getDocuments());
        alerts.sort(Comparator
                .comparingInt((DocumentSnapshot d) -> severityRank(text(d, "severity", "warning"))).reversed()
                .thenComparing(d -> timestampInstant(d, "lastSeenAt"), Comparator.reverseOrder()));
        for (DocumentSnapshot alert : alerts) {
            String schoolId = text(alert, "schoolId", "");
            alertsBySchool.computeIfAbsent(schoolId, k -> new ArrayList<>()).add(alert);
        }

        int healthy = 0, attention = 0, billingRisk = 0, overQuota = 0, suspended = 0;
        int pendingGcash = 0, overdueInvoices = 0, expiring = 0, quotaWarnings = 0, emailFailures = 0;
        List<Map<String,Object>> tenantRows = new ArrayList<>();

        for (DocumentSnapshot school : schoolSnap.getDocuments()) {
            List<DocumentSnapshot> schoolAlerts = alertsBySchool.getOrDefault(school.getId(), List.of());
            String health = classifyHealth(text(school, "status", "active"), schoolAlerts.stream().map(d -> text(d, "healthImpact", ATTENTION)).toList());
            switch (health) {
                case HEALTHY -> healthy++;
                case ATTENTION -> attention++;
                case BILLING_RISK -> billingRisk++;
                case OVER_QUOTA -> overQuota++;
                case SUSPENDED -> suspended++;
            }
            long criticalCount = schoolAlerts.stream().filter(d -> "critical".equals(text(d,"severity",""))).count();
            long warningCount = schoolAlerts.stream().filter(d -> "warning".equals(text(d,"severity",""))).count();
            Map<String,Object> usage = tenantUsageService.snapshot(school.getId());
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("schoolId", school.getId());
            row.put("schoolName", text(school, "schoolName", "Unnamed school"));
            row.put("status", text(school, "status", "active"));
            row.put("plan", text(school, "plan", "trial"));
            row.put("subscriptionStatus", text(school, "subscriptionStatus", "trialing"));
            row.put("healthState", health);
            row.put("activeAlertCount", schoolAlerts.size());
            row.put("criticalAlertCount", criticalCount);
            row.put("warningAlertCount", warningCount);
            row.put("maxQuotaPercent", maxQuotaPercent(usage));
            tenantRows.add(row);
        }
        tenantRows.sort(Comparator
                .comparingInt((Map<String,Object> r) -> healthRank(String.valueOf(r.get("healthState")))).reversed()
                .thenComparing(r -> String.valueOf(r.get("schoolName")), String.CASE_INSENSITIVE_ORDER));

        List<Map<String,Object>> alertRows = new ArrayList<>();
        for (DocumentSnapshot d : alerts) {
            String type = text(d, "type", "");
            if ("pending_gcash".equals(type)) pendingGcash++;
            if ("invoice_overdue".equals(type)) overdueInvoices++;
            if ("trial_expiring".equals(type) || "subscription_expiring".equals(type) || "grace_expiring".equals(type)) expiring++;
            if ("quota_near_limit".equals(type) || "quota_over_limit".equals(type)) quotaWarnings++;
            if ("billing_email_failed".equals(type)) emailFailures++;
            alertRows.add(alertMap(d));
        }

        Timestamp lastScanTs = firestore.collection("saasOperationsMetadata").document("global").get().get().getTimestamp("lastScanAt");
        Map<String,Object> metrics = new LinkedHashMap<>();
        metrics.put("totalSchools", schoolSnap.size());
        metrics.put("healthySchools", healthy);
        metrics.put("attentionNeededSchools", attention);
        metrics.put("billingRiskSchools", billingRisk);
        metrics.put("overQuotaSchools", overQuota);
        metrics.put("suspendedSchools", suspended);
        metrics.put("pendingGcashReviews", pendingGcash);
        metrics.put("overdueInvoices", overdueInvoices);
        metrics.put("expiringSubscriptions", expiring);
        metrics.put("quotaWarnings", quotaWarnings);
        metrics.put("billingEmailFailures", emailFailures);

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("generatedAt", Instant.now().toString());
        result.put("lastScanAt", lastScanTs == null ? null : lastScanTs.toDate().toInstant().toString());
        result.put("metrics", metrics);
        result.put("tenants", tenantRows);
        result.put("alerts", alertRows);
        return result;
    }

    private List<AlertSpec> buildAlertSpecs(
            DocumentSnapshot school,
            Map<String,Object> usage,
            List<DocumentSnapshot> invoices,
            List<DocumentSnapshot> pendingPayments,
            List<DocumentSnapshot> emailFailures,
            Instant now) {
        List<AlertSpec> out = new ArrayList<>();
        String schoolId = school.getId();
        String status = text(school, "status", "active");
        String subscriptionStatus = text(school, "subscriptionStatus", "trialing");

        if ("suspended".equals(status)) {
            out.add(new AlertSpec("tenant_suspended", "critical", SUSPENDED,
                    "Tenant is suspended", "School access is currently suspended by the master administrator.", "school", schoolId));
        }

        if ("past_due".equals(subscriptionStatus)) {
            out.add(new AlertSpec("subscription_past_due", "critical", BILLING_RISK,
                    "Subscription is past due", "The school is in its billing grace period. Review payment and subscription dates.", "subscription", schoolId));
        } else if ("cancelled".equals(subscriptionStatus)) {
            out.add(new AlertSpec("subscription_cancelled", "warning", BILLING_RISK,
                    "Subscription is cancelled", "Optional SaaS features are blocked while core student pickup remains available.", "subscription", schoolId));
        }

        Timestamp graceTs = school.getTimestamp("graceEndsAt");
        if ("past_due".equals(subscriptionStatus) && graceTs != null) {
            long days = daysUntil(now, graceTs.toDate().toInstant());
            if (days >= 0 && days <= 3) {
                out.add(new AlertSpec("grace_expiring", "critical", BILLING_RISK,
                        "Billing grace period ends soon", "Grace period ends in " + days + " day(s). Review payment before optional features are blocked.", "subscription", schoolId));
            }
        }

        Timestamp trialTs = school.getTimestamp("trialEndsAt");
        if ("trialing".equals(subscriptionStatus) && trialTs != null) {
            long days = daysUntil(now, trialTs.toDate().toInstant());
            if (days >= 0 && days <= 7) {
                out.add(new AlertSpec("trial_expiring", "warning", ATTENTION,
                        "Trial ends soon", "Trial ends in " + days + " day(s). Confirm the next plan or extend the trial if appropriate.", "subscription", schoolId));
            }
        }

        Timestamp periodTs = school.getTimestamp("currentPeriodEnd");
        boolean autoRenew = !Boolean.FALSE.equals(school.getBoolean("autoRenew"));
        boolean cancelAtPeriodEnd = Boolean.TRUE.equals(school.getBoolean("cancelAtPeriodEnd"));
        if ("active".equals(subscriptionStatus) && periodTs != null && (!autoRenew || cancelAtPeriodEnd)) {
            long days = daysUntil(now, periodTs.toDate().toInstant());
            if (days >= 0 && days <= 7) {
                out.add(new AlertSpec("subscription_expiring", "warning", ATTENTION,
                        "Subscription period ends soon", "Current period ends in " + days + " day(s) and will not continue automatically.", "subscription", schoolId));
            }
        }

        boolean anyOver = bool(usage, "studentsOverLimit") || bool(usage, "staffOverLimit") || bool(usage, "campusesOverLimit");
        if (anyOver) {
            out.add(new AlertSpec("quota_over_limit", "critical", OVER_QUOTA,
                    "Tenant is over plan quota", quotaMessage(usage, true), "usage", schoolId));
        } else if (maxQuotaPercent(usage) >= quotaWarningPercent) {
            out.add(new AlertSpec("quota_near_limit", "warning", ATTENTION,
                    "Plan quota is nearing its limit", quotaMessage(usage, false), "usage", schoolId));
        }

        for (DocumentSnapshot invoice : invoices) {
            Timestamp due = invoice.getTimestamp("dueAt");
            if (due == null) continue;
            String number = text(invoice, "invoiceNumber", invoice.getId());
            Instant dueAt = due.toDate().toInstant();
            String invoiceStatus = text(invoice, "status", "open");
            if ("overdue".equals(invoiceStatus) || dueAt.isBefore(now)) {
                out.add(new AlertSpec("invoice_overdue", "critical", BILLING_RISK,
                        "Invoice is overdue", number + " is overdue. Follow up or reconcile a submitted GCash payment.", "billing", invoice.getId()));
            } else {
                long days = daysUntil(now, dueAt);
                if (days >= 0 && days <= 3) {
                    out.add(new AlertSpec("invoice_due_soon", "warning", ATTENTION,
                            "Invoice due soon", number + " is due in " + days + " day(s).", "billing", invoice.getId()));
                }
            }
        }

        for (DocumentSnapshot notice : pendingPayments) {
            String number = text(notice, "invoiceNumber", text(notice, "invoiceId", "invoice"));
            out.add(new AlertSpec("pending_gcash", "warning", ATTENTION,
                    "GCash payment needs verification", "Payment for " + number + " is waiting for manual verification.", "review_payment", notice.getId()));
        }

        for (DocumentSnapshot invoice : emailFailures) {
            String number = text(invoice, "invoiceNumber", invoice.getId());
            String type = text(invoice, "lastEmailFailureType", "billing email");
            out.add(new AlertSpec("billing_email_failed", "warning", ATTENTION,
                    "Billing email delivery failed", number + " could not send " + type + ". Retry after checking SMTP configuration.", "billing", invoice.getId()));
        }
        return out;
    }

    private void upsertAlert(String id, String schoolId, String schoolName, AlertSpec spec, Instant now, DocumentSnapshot existing) throws Exception {
        Map<String,Object> data = new HashMap<>();
        data.put("schoolId", schoolId);
        data.put("schoolNameSnapshot", schoolName);
        data.put("type", spec.type());
        data.put("severity", spec.severity());
        data.put("healthImpact", spec.healthImpact());
        data.put("title", spec.title());
        data.put("message", spec.message());
        data.put("action", spec.action());
        data.put("resourceId", spec.resourceId());
        data.put("active", true);
        data.put("lastSeenAt", Date.from(now));
        data.put("resolvedAt", FieldValue.delete());
        if (existing == null || !existing.exists() || existing.getTimestamp("firstSeenAt") == null) {
            data.put("firstSeenAt", Date.from(now));
        }
        firestore.collection("saasOperationalAlerts").document(id).set(data, SetOptions.merge()).get();
    }

    private Map<String,Object> alertMap(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("alertId", d.getId());
        for (String k : List.of("schoolId","schoolNameSnapshot","type","severity","healthImpact","title","message","action","resourceId")) {
            m.put(k, Optional.ofNullable(d.get(k)).orElse(""));
        }
        m.put("firstSeenAt", timestampString(d, "firstSeenAt"));
        m.put("lastSeenAt", timestampString(d, "lastSeenAt"));
        return m;
    }

    static String classifyHealth(String tenantStatus, List<String> impacts) {
        if ("suspended".equalsIgnoreCase(tenantStatus)) return SUSPENDED;
        String best = HEALTHY;
        int bestRank = 0;
        for (String impact : impacts) {
            int rank = healthRank(impact);
            if (rank > bestRank) { best = impact; bestRank = rank; }
        }
        return best;
    }

    static int healthRank(String value) {
        return switch (value) {
            case ATTENTION -> 1;
            case BILLING_RISK -> 2;
            case OVER_QUOTA -> 3;
            case SUSPENDED -> 4;
            default -> 0;
        };
    }

    static long quotaPercent(long current, long limit) {
        if (limit <= 0) return 0;
        return Math.round((current * 100.0) / limit);
    }

    private static long maxQuotaPercent(Map<String,Object> usage) {
        long student = quotaPercent(longValue(usage,"activeStudents"), longValue(usage,"studentLimit"));
        long staff = quotaPercent(longValue(usage,"activeStaff"), longValue(usage,"staffLimit"));
        long campus = quotaPercent(longValue(usage,"activeCampuses"), longValue(usage,"campusLimit"));
        return Math.max(student, Math.max(staff, campus));
    }

    private static String quotaMessage(Map<String,Object> usage, boolean over) {
        List<String> parts = new ArrayList<>();
        appendQuota(parts, "students", usage, "activeStudents", "studentLimit");
        appendQuota(parts, "staff", usage, "activeStaff", "staffLimit");
        appendQuota(parts, "campuses", usage, "activeCampuses", "campusLimit");
        return (over ? "Current usage exceeds a plan limit. " : "One or more plan resources are close to capacity. ") + String.join(" · ", parts);
    }

    private static void appendQuota(List<String> parts, String label, Map<String,Object> usage, String currentKey, String limitKey) {
        long limit = longValue(usage, limitKey);
        if (limit < 0) return;
        parts.add(label + " " + longValue(usage,currentKey) + "/" + limit);
    }

    private static int severityRank(String severity) { return "critical".equals(severity) ? 2 : "warning".equals(severity) ? 1 : 0; }
    private static long daysUntil(Instant now, Instant target) { return Math.max(0, Duration.between(now, target).toDays()); }
    private static boolean bool(Map<String,Object> m, String key) { return Boolean.TRUE.equals(m.get(key)); }
    private static long longValue(Map<String,Object> m, String key) { Object v=m.get(key); return v instanceof Number n ? n.longValue() : -1L; }
    private static String text(DocumentSnapshot d, String key, String fallback) { String v=d.getString(key); return v==null||v.isBlank()?fallback:v; }
    private static String timestampString(DocumentSnapshot d, String key) { Timestamp t=d.getTimestamp(key); return t==null?null:t.toDate().toInstant().toString(); }
    private static Instant timestampInstant(DocumentSnapshot d, String key) { Timestamp t=d.getTimestamp(key); return t==null?Instant.EPOCH:t.toDate().toInstant(); }
    private static String clean(String v, int max) { if(v==null)return ""; String s=v.trim(); return s.length()<=max?s:s.substring(0,max); }

    private static String alertId(String type, String resourceId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((type + "|" + resourceId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record RefreshResult(boolean executed, int activeAlerts, int resolvedAlerts, String status) { }
    private record AlertSpec(String type, String severity, String healthImpact, String title, String message, String action, String resourceId) { }
}

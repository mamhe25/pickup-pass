package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Applies subscription lifecycle transitions without touching school access or
 * the core QR pickup flow. Optional SaaS features are gated separately by
 * SubscriptionFeatureService.
 */
@Service
public class SubscriptionLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleService.class);
    static final long BILLING_PERIOD_DAYS = 30;
    static final long GRACE_PERIOD_DAYS = 7;

    private final Firestore firestore;
    private final AuditService auditService;
    private final SaasOperationsHealthService operationsHealthService;

    public SubscriptionLifecycleService(Firestore firestore, AuditService auditService,
                                        SaasOperationsHealthService operationsHealthService) {
        this.firestore = firestore;
        this.auditService = auditService;
        this.operationsHealthService = operationsHealthService;
    }

    @Scheduled(fixedDelayString = "${pickuppass.subscription-lifecycle-ms:3600000}")
    public void reconcileDueSubscriptions() {
        try {
            for (DocumentSnapshot school : firestore.collection("schools").get().get().getDocuments()) {
                try {
                    reconcileSchool(school.getId(), Instant.now());
                } catch (Exception e) {
                    log.error("SUBSCRIPTION_LIFECYCLE_FAILED schoolId={}", school.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("SUBSCRIPTION_LIFECYCLE_SCAN_FAILED", e);
        }
    }

    public TransitionResult reconcileSchool(String schoolId, Instant now) throws Exception {
        DocumentReference ref = firestore.collection("schools").document(schoolId);
        TransitionResult result = firestore.runTransaction(tx -> {
            DocumentSnapshot school = tx.get(ref).get();
            if (!school.exists()) return TransitionResult.none();

            String plan = stringOr(school.getString("plan"), SubscriptionFeatureService.TRIAL);
            String status = stringOr(school.getString("subscriptionStatus"),
                    SubscriptionFeatureService.TRIAL.equals(plan) ? "trialing" : "active");
            Instant trialEndsAt = instant(school.getTimestamp("trialEndsAt"));
            Instant currentPeriodEnd = instant(school.getTimestamp("currentPeriodEnd"));
            Instant graceEndsAt = instant(school.getTimestamp("graceEndsAt"));
            boolean autoRenew = !Boolean.FALSE.equals(school.getBoolean("autoRenew"));
            boolean cancelAtPeriodEnd = Boolean.TRUE.equals(school.getBoolean("cancelAtPeriodEnd"));

            TransitionDecision decision = decide(status, trialEndsAt, currentPeriodEnd, graceEndsAt,
                    autoRenew, cancelAtPeriodEnd, now);
            if (decision.action() == null) return TransitionResult.none();

            Map<String,Object> update = new HashMap<>(decision.update());
            update.put("subscriptionUpdatedAt", FieldValue.serverTimestamp());
            tx.update(ref, update);
            return new TransitionResult(decision.action(), decision.newStatus(), decision.auditDetails());
        }).get();

        if (result.action() != null) {
            auditService.recordSystem(schoolId, result.action(), "school", schoolId, result.details());
            // Keep the master operations console current after the hourly lifecycle worker
            // changes a subscription. This is tenant-scoped and best-effort.
            operationsHealthService.refreshSchool(schoolId);
        }
        return result;
    }

    static TransitionDecision decide(String status,
                                     Instant trialEndsAt,
                                     Instant currentPeriodEnd,
                                     Instant graceEndsAt,
                                     boolean autoRenew,
                                     boolean cancelAtPeriodEnd,
                                     Instant now) {
        if ("trialing".equals(status) && trialEndsAt != null && !now.isBefore(trialEndsAt)) {
            Instant graceEnd = trialEndsAt.plus(GRACE_PERIOD_DAYS, ChronoUnit.DAYS);
            Map<String,Object> update = new HashMap<>();
            update.put("subscriptionStatus", "past_due");
            update.put("pastDueAt", Date.from(trialEndsAt));
            update.put("graceEndsAt", Date.from(graceEnd));
            return new TransitionDecision(
                    "subscription.trial_expired",
                    "past_due",
                    update,
                    Map.of("previousStatus", "trialing", "newStatus", "past_due", "graceEndsAt", graceEnd.toString())
            );
        }

        if ("past_due".equals(status) && graceEndsAt != null && !now.isBefore(graceEndsAt)) {
            Map<String,Object> update = new HashMap<>();
            update.put("subscriptionStatus", "cancelled");
            update.put("cancelledAt", Date.from(now));
            update.put("subscriptionAccessBlockedAt", Date.from(now));
            return new TransitionDecision(
                    "subscription.grace_expired",
                    "cancelled",
                    update,
                    Map.of("previousStatus", "past_due", "newStatus", "cancelled")
            );
        }

        if ("active".equals(status) && currentPeriodEnd != null && !now.isBefore(currentPeriodEnd)) {
            if (cancelAtPeriodEnd || !autoRenew) {
                Map<String,Object> update = new HashMap<>();
                update.put("subscriptionStatus", "cancelled");
                update.put("cancelledAt", Date.from(now));
                update.put("subscriptionAccessBlockedAt", Date.from(now));
                return new TransitionDecision(
                        "subscription.period_ended",
                        "cancelled",
                        update,
                        Map.of("previousStatus", "active", "newStatus", "cancelled")
                );
            }

            Instant start = currentPeriodEnd;
            Instant end = start.plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS);
            while (!now.isBefore(end)) {
                start = end;
                end = end.plus(BILLING_PERIOD_DAYS, ChronoUnit.DAYS);
            }
            Map<String,Object> update = new HashMap<>();
            update.put("currentPeriodStart", Date.from(start));
            update.put("currentPeriodEnd", Date.from(end));
            update.put("lastAutoRenewedAt", Date.from(now));
            update.put("graceEndsAt", FieldValue.delete());
            return new TransitionDecision(
                    "subscription.auto_renewed",
                    "active",
                    update,
                    Map.of("newPeriodStart", start.toString(), "newPeriodEnd", end.toString())
            );
        }

        return TransitionDecision.none();
    }

    private static String stringOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toLowerCase();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toDate().toInstant();
    }

    record TransitionDecision(String action, String newStatus, Map<String,Object> update, Map<String,Object> auditDetails) {
        static TransitionDecision none() { return new TransitionDecision(null, null, Map.of(), Map.of()); }
    }

    public record TransitionResult(String action, String newStatus, Map<String,Object> details) {
        static TransitionResult none() { return new TransitionResult(null, null, Map.of()); }
    }
}

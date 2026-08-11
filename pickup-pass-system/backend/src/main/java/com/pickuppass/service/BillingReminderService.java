package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Best-effort, deduplicated billing reminders. Never participates in pickup transactions. */
@Service
public class BillingReminderService {
    private static final Logger log = LoggerFactory.getLogger(BillingReminderService.class);
    private final Firestore firestore;
    private final BillingEmailService emailService;
    private final boolean enabled;

    public BillingReminderService(Firestore firestore, BillingEmailService emailService,
                                  @Value("${pickuppass.billing.reminders-enabled:true}") boolean enabled) {
        this.firestore = firestore;
        this.emailService = emailService;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${pickuppass.billing.reminder-scan-ms:21600000}")
    public void scan() {
        if (!enabled) return;
        try {
            Instant now = Instant.now();
            for (String targetStatus : new String[]{"open", "overdue"}) {
                QuerySnapshot snap = firestore.collection("billingInvoices")
                        .whereEqualTo("status", targetStatus).get().get();
                for (DocumentSnapshot invoice : snap.getDocuments()) {
                    Timestamp dueTs = invoice.getTimestamp("dueAt");
                    if (dueTs == null) continue;
                    String recipient = resolveRecipient(invoice);
                    if (recipient.isBlank()) continue;
                    long hours = Duration.between(now, dueTs.toDate().toInstant()).toHours();
                    if (hours <= 24 && hours >= 0 && invoice.getTimestamp("reminder1DaySentAt") == null) {
                        sendAndMark(invoice.getReference(), recipient, "1_day", "reminder1DaySentAt");
                    } else if (hours <= 7L * 24 && hours > 24 && invoice.getTimestamp("reminder7DaySentAt") == null) {
                        sendAndMark(invoice.getReference(), recipient, "7_day", "reminder7DaySentAt");
                    } else if (hours < 0 && invoice.getTimestamp("overdueReminderSentAt") == null) {
                        sendAndMark(invoice.getReference(), recipient, "overdue", "overdueReminderSentAt");
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Billing reminder scan failed; core operations are unaffected", e);
        }
    }

    private void sendAndMark(DocumentReference ref, String recipient, String type, String field) {
        try {
            emailService.sendPaymentReminder(ref, recipient, type);
            ref.update(field, FieldValue.serverTimestamp(), "updatedAt", FieldValue.serverTimestamp()).get();
        } catch (Exception e) {
            log.warn("Billing reminder delivery failed invoiceId={} type={}", ref.getId(), type, e);
        }
    }

    private String resolveRecipient(DocumentSnapshot invoice) throws Exception {
        String schoolId = text(invoice, "schoolId", "");
        if (!schoolId.isBlank()) {
            DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
            if (school.exists()) {
                String current = text(school, "billingEmail", "").trim().toLowerCase(Locale.ROOT);
                if (!current.isBlank()) return current;
            }
        }
        return text(invoice, "billingEmailSnapshot", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String text(DocumentSnapshot d, String key, String fallback) {
        String v = d.getString(key);
        return v == null || v.isBlank() ? fallback : v;
    }
}

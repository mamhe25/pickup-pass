package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class BillingInvoiceLifecycleService {
    private final Firestore firestore;
    public BillingInvoiceLifecycleService(Firestore firestore) { this.firestore = firestore; }

    @Scheduled(fixedDelayString = "${pickuppass.billing.reconcile-ms:3600000}")
    public void markPastDueInvoices() {
        try {
            QuerySnapshot open = firestore.collection("billingInvoices").whereEqualTo("status", "open").get().get();
            Instant now = Instant.now();
            for (DocumentSnapshot d : open.getDocuments()) {
                Timestamp due = d.getTimestamp("dueAt");
                if (due != null && due.toDate().toInstant().isBefore(now)) {
                    d.getReference().update("status", "overdue", "updatedAt", FieldValue.serverTimestamp()).get();
                }
            }
        } catch (Exception ignored) {
            // Billing status refresh is operational bookkeeping; it must not affect QR pickup availability.
        }
    }
}

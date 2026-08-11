package com.pickuppass.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class PaymentWebhookService {
    private final Firestore firestore;
    private final Map<String,PaymentWebhookAdapter> adapters;
    private final AuditService auditService;

    public PaymentWebhookService(Firestore firestore, List<PaymentWebhookAdapter> adapters, AuditService auditService) {
        this.firestore = firestore;
        Map<String,PaymentWebhookAdapter> map = new HashMap<>();
        for (PaymentWebhookAdapter adapter : adapters) map.put(adapter.provider().toLowerCase(Locale.ROOT), adapter);
        this.adapters = Map.copyOf(map);
        this.auditService = auditService;
    }

    public WebhookResult handle(String provider, Map<String,String> headers, byte[] body, Instant now) throws Exception {
        PaymentWebhookAdapter adapter = adapters.get(provider.toLowerCase(Locale.ROOT));
        if (adapter == null) throw new IllegalArgumentException("Unsupported payment provider");
        PaymentWebhookAdapter.PaymentEvent event = adapter.verifyAndParse(headers, body, now);
        if (!Set.of("payment.succeeded", "invoice.paid").contains(event.type())) {
            storeIgnored(provider, event);
            return new WebhookResult("ignored", event.eventId(), event.invoiceId());
        }
        if (event.amountMinor() == null || event.amountMinor() < 0 || event.currency() == null || !event.currency().matches("[A-Z]{3}")) {
            throw new GenericHmacPaymentWebhookAdapter.WebhookValidationException("Payment amount and currency are required");
        }

        String eventDocId = sha256(provider + "\n" + event.eventId());
        DocumentReference eventRef = firestore.collection("paymentWebhookEvents").document(eventDocId);
        DocumentReference invoiceRef = firestore.collection("billingInvoices").document(event.invoiceId());

        ProcessOutcome outcome;
        try {
            outcome = firestore.runTransaction(tx -> {
                DocumentSnapshot existing = tx.get(eventRef).get();
                if (existing.exists()) return new ProcessOutcome("duplicate", existing.getString("schoolId"));
                DocumentSnapshot invoice = tx.get(invoiceRef).get();
                if (!invoice.exists()) throw new WebhookProcessingException("Invoice not found");
                String status = Optional.ofNullable(invoice.getString("status")).orElse("open");
                if ("void".equals(status)) throw new WebhookProcessingException("Void invoice cannot be paid");
                long invoiceAmount = Optional.ofNullable(invoice.getLong("amountMinor")).orElse(0L);
                String invoiceCurrency = Optional.ofNullable(invoice.getString("currency")).orElse("PHP");
                if (invoiceAmount != event.amountMinor() || !invoiceCurrency.equalsIgnoreCase(event.currency())) {
                    throw new WebhookProcessingException("Payment amount or currency does not match invoice");
                }
                String schoolId = Optional.ofNullable(invoice.getString("schoolId")).orElse("");
                Map<String,Object> eventDoc = new HashMap<>();
                eventDoc.put("provider", provider);
                eventDoc.put("providerEventId", event.eventId());
                eventDoc.put("type", event.type());
                eventDoc.put("invoiceId", event.invoiceId());
                eventDoc.put("schoolId", schoolId);
                eventDoc.put("payloadHash", event.payloadHash());
                eventDoc.put("receivedAt", FieldValue.serverTimestamp());
                eventDoc.put("status", "processed");
                tx.set(eventRef, eventDoc);
                if (!"paid".equals(status)) {
                    Map<String,Object> update = new HashMap<>();
                    update.put("status", "paid");
                    update.put("paidAt", Date.from(event.occurredAt()));
                    update.put("paymentReference", clean(event.paymentReference(), 120));
                    update.put("paymentMethod", provider);
                    update.put("paymentProvider", provider);
                    update.put("providerEventId", event.eventId());
                    update.put("updatedAt", FieldValue.serverTimestamp());
                    tx.update(invoiceRef, update);
                }
                return new ProcessOutcome("processed", schoolId);
            }).get();
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof WebhookProcessingException processing) throw processing;
            throw e;
        }

        if ("processed".equals(outcome.status())) {
            auditService.recordSystem(outcome.schoolId(), "billing.payment_webhook_processed", "billingInvoice", event.invoiceId(),
                    Map.of("provider", provider, "providerEventId", event.eventId()));
        }
        return new WebhookResult(outcome.status(), event.eventId(), event.invoiceId());
    }

    private void storeIgnored(String provider, PaymentWebhookAdapter.PaymentEvent event) throws Exception {
        String id = sha256(provider + "\n" + event.eventId());
        DocumentReference ref = firestore.collection("paymentWebhookEvents").document(id);
        if (!ref.get().get().exists()) {
            ref.set(Map.of("provider", provider, "providerEventId", event.eventId(), "type", event.type(),
                    "invoiceId", event.invoiceId(), "payloadHash", event.payloadHash(), "status", "ignored",
                    "receivedAt", FieldValue.serverTimestamp())).get();
        }
    }
    private static String clean(String v, int max) { if (v==null) return ""; String s=v.trim(); return s.length()<=max?s:s.substring(0,max); }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private record ProcessOutcome(String status, String schoolId) {}
    public record WebhookResult(String status, String eventId, String invoiceId) {}
    public static class WebhookProcessingException extends RuntimeException { public WebhookProcessingException(String m){super(m);} }
}

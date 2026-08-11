package com.pickuppass.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.AuditService;
import com.pickuppass.service.BillingEmailService;
import com.pickuppass.service.InvoicePdfService;
import com.pickuppass.service.ReceiptPdfService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/master-admin/billing")
@PreAuthorize("hasRole('master_admin')")
public class MasterBillingController {
    private final Firestore firestore;
    private final AuditService auditService;
    private final InvoicePdfService invoicePdfService;
    private final BillingEmailService billingEmailService;
    private final ReceiptPdfService receiptPdfService;
    private final boolean gcashEnabled;
    private final String gcashAccountName;
    private final String gcashMobile;
    private final String gcashNote;

    public MasterBillingController(Firestore firestore, AuditService auditService,
                                   InvoicePdfService invoicePdfService, BillingEmailService billingEmailService,
                                   ReceiptPdfService receiptPdfService,
                                   @org.springframework.beans.factory.annotation.Value("${BILLING_GCASH_ENABLED:true}") boolean gcashEnabled,
                                   @org.springframework.beans.factory.annotation.Value("${BILLING_GCASH_ACCOUNT_NAME:}") String gcashAccountName,
                                   @org.springframework.beans.factory.annotation.Value("${BILLING_GCASH_MOBILE:}") String gcashMobile,
                                   @org.springframework.beans.factory.annotation.Value("${BILLING_GCASH_NOTE:Send the exact invoice amount and keep your GCash reference number.}") String gcashNote) {
        this.firestore = firestore;
        this.auditService = auditService;
        this.invoicePdfService = invoicePdfService;
        this.billingEmailService = billingEmailService;
        this.receiptPdfService = receiptPdfService;
        this.gcashEnabled = gcashEnabled;
        this.gcashAccountName = clean(gcashAccountName, 120);
        this.gcashMobile = clean(gcashMobile, 40);
        this.gcashNote = clean(gcashNote, 500);
    }

    @GetMapping("/schools/{schoolId}/profile")
    public ResponseEntity<?> billingProfile(@PathVariable String schoolId) throws Exception {
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        if (!school.exists()) return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        return ResponseEntity.ok(profileMap(school));
    }

    @PutMapping("/schools/{schoolId}/profile")
    public ResponseEntity<?> updateBillingProfile(@PathVariable String schoolId,
                                                   @RequestBody BillingProfileRequest req,
                                                   @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        DocumentReference ref = firestore.collection("schools").document(schoolId);
        DocumentSnapshot school = ref.get().get();
        if (!school.exists()) return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        String email = clean(req.billingEmail, 254).toLowerCase(Locale.ROOT);
        if (!email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid billing email"));
        Map<String,Object> update = new HashMap<>();
        update.put("billingName", clean(req.billingName, 160));
        update.put("billingEmail", email);
        update.put("billingAddress", clean(req.billingAddress, 500));
        update.put("billingTaxId", clean(req.billingTaxId, 80));
        update.put("billingProfileUpdatedAt", FieldValue.serverTimestamp());
        ref.update(update).get();
        auditService.record(actor, "billing.profile_updated", "school", schoolId,
                Map.of("schoolId", schoolId, "billingEmail", email));
        return ResponseEntity.ok(profileMap(ref.get().get()));
    }

    @GetMapping("/schools/{schoolId}/invoices")
    public ResponseEntity<?> list(@PathVariable String schoolId) throws Exception {
        if (!schoolExists(schoolId)) return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        QuerySnapshot snapshot = firestore.collection("billingInvoices")
                .whereEqualTo("schoolId", schoolId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100).get().get();
        List<Map<String,Object>> rows = new ArrayList<>();
        for (DocumentSnapshot d : snapshot.getDocuments()) rows.add(toInvoice(d));
        return ResponseEntity.ok(Map.of("invoices", rows));
    }

    @PostMapping("/schools/{schoolId}/invoices")
    public ResponseEntity<?> create(@PathVariable String schoolId,
                                    @RequestBody CreateInvoiceRequest req,
                                    @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        if (!schoolExists(schoolId)) return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        if (req.amountMinor == null || req.amountMinor < 0) return ResponseEntity.badRequest().body(Map.of("error", "amountMinor must be zero or greater"));
        String currency;
        try { currency = normalizeCurrency(req.currency); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
        Instant dueAt;
        try { dueAt = req.dueAt == null || req.dueAt.isBlank() ? Instant.now().plusSeconds(14L*86400) : Instant.parse(req.dueAt); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", "dueAt must be ISO-8601")); }
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        String number = "PP-" + Instant.now().toString().substring(0,10).replace("-","") + "-" + UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        DocumentReference ref = firestore.collection("billingInvoices").document();
        Map<String,Object> data = new HashMap<>();
        data.put("schoolId", schoolId);
        data.put("schoolNameSnapshot", Optional.ofNullable(school.getString("schoolName")).orElse("Unnamed school"));
        data.put("billingNameSnapshot", firstNonBlank(school.getString("billingName"), school.getString("schoolName"), "Unnamed school"));
        data.put("billingEmailSnapshot", clean(school.getString("billingEmail"), 254));
        data.put("billingAddressSnapshot", clean(school.getString("billingAddress"), 500));
        data.put("billingTaxIdSnapshot", clean(school.getString("billingTaxId"), 80));
        data.put("invoiceNumber", number);
        data.put("planSnapshot", Optional.ofNullable(school.getString("plan")).orElse("trial"));
        data.put("amountMinor", req.amountMinor);
        data.put("currency", currency);
        data.put("status", "open");
        data.put("dueAt", Date.from(dueAt));
        data.put("note", clean(req.note, 500));
        data.put("gcashEnabledSnapshot", gcashEnabled && !gcashMobile.isBlank());
        data.put("gcashAccountNameSnapshot", gcashAccountName);
        data.put("gcashMobileSnapshot", gcashMobile);
        data.put("gcashNoteSnapshot", gcashNote);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        ref.set(data).get();
        auditService.record(actor, "billing.invoice_created", "billingInvoice", ref.getId(), Map.of("schoolId", schoolId, "invoiceNumber", number));
        return ResponseEntity.ok(toInvoice(ref.get().get()));
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<?> pdf(@PathVariable String invoiceId,
                                 @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        DocumentSnapshot invoice = firestore.collection("billingInvoices").document(invoiceId).get().get();
        if (!invoice.exists()) return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        byte[] pdf = invoicePdfService.render(invoice);
        String fileName = clean(invoice.getString("invoiceNumber"), 80).replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
        auditService.record(actor, "billing.invoice_pdf_generated", "billingInvoice", invoiceId,
                Map.of("schoolId", Optional.ofNullable(invoice.getString("schoolId")).orElse("")));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(fileName).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(pdf);
    }

    @GetMapping("/invoices/{invoiceId}/receipt")
    public ResponseEntity<?> receipt(@PathVariable String invoiceId,
                                     @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        DocumentSnapshot invoice = firestore.collection("billingInvoices").document(invoiceId).get().get();
        if (!invoice.exists()) return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        try {
            byte[] pdf = receiptPdfService.render(invoice);
            String number = receiptPdfService.receiptNumber(invoice);
            auditService.record(actor, "billing.receipt_pdf_generated", "billingInvoice", invoiceId,
                    Map.of("schoolId", Optional.ofNullable(invoice.getString("schoolId")).orElse("")));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(number + ".pdf").build().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/invoices/{invoiceId}/reminder")
    public ResponseEntity<?> reminder(@PathVariable String invoiceId,
                                      @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        DocumentReference ref = firestore.collection("billingInvoices").document(invoiceId);
        DocumentSnapshot invoice = ref.get().get();
        if (!invoice.exists()) return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        if ("paid".equalsIgnoreCase(invoice.getString("status")) || "void".equalsIgnoreCase(invoice.getString("status")))
            return ResponseEntity.badRequest().body(Map.of("error", "Reminder is not available for this invoice"));
        String recipient = resolveBillingRecipient(invoice);
        if (recipient.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "No billing email is configured for this school"));
        billingEmailService.sendPaymentReminder(ref, recipient,
                "overdue".equalsIgnoreCase(invoice.getString("status")) ? "overdue" : "manual");
        auditService.record(actor, "billing.payment_reminder_requested", "billingInvoice", invoiceId, Map.of("recipient", recipient));
        return ResponseEntity.ok(Map.of("status", "sent", "recipient", recipient));
    }

    @PostMapping("/invoices/{invoiceId}/email")
    public ResponseEntity<?> email(@PathVariable String invoiceId,
                                   @RequestBody(required = false) EmailInvoiceRequest req,
                                   @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        DocumentReference ref = firestore.collection("billingInvoices").document(invoiceId);
        DocumentSnapshot invoice = ref.get().get();
        if (!invoice.exists()) return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        String recipient = req == null ? "" : clean(req.recipientEmail, 254).toLowerCase(Locale.ROOT);
        if (recipient.isBlank()) recipient = resolveBillingRecipient(invoice);
        if (recipient.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "No billing email is configured for this school"));
        try {
            BillingEmailService.DeliveryResult delivery = billingEmailService.sendInvoice(ref, recipient);
            auditService.record(actor, "billing.invoice_email_requested", "billingInvoice", invoiceId,
                    Map.of("recipient", delivery.recipient()));
            return ResponseEntity.ok(Map.of("status", "sent", "recipient", delivery.recipient(), "invoiceNumber", delivery.invoiceNumber()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        } catch (org.springframework.mail.MailException e) {
            return ResponseEntity.status(502).body(Map.of("error", "Billing email delivery failed"));
        }
    }

    @PostMapping("/invoices/{invoiceId}/paid")
    public ResponseEntity<?> markPaid(@PathVariable String invoiceId,
                                      @RequestBody PaymentRequest req,
                                      @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        DocumentReference ref = firestore.collection("billingInvoices").document(invoiceId);
        DocumentSnapshot invoice = ref.get().get();
        if (!invoice.exists()) return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        String status = Optional.ofNullable(invoice.getString("status")).orElse("open");
        if ("void".equals(status)) return ResponseEntity.badRequest().body(Map.of("error", "Void invoice cannot be paid"));
        Map<String,Object> update = new HashMap<>();
        update.put("status", "paid");
        update.put("paidAt", FieldValue.serverTimestamp());
        update.put("paymentReference", clean(req.paymentReference, 120));
        update.put("paymentMethod", clean(req.paymentMethod, 80));
        update.put("paymentNote", clean(req.note, 500));
        update.put("updatedAt", FieldValue.serverTimestamp());
        ref.update(update).get();
        auditService.record(actor, "billing.invoice_paid", "billingInvoice", invoiceId, Map.of("schoolId", Optional.ofNullable(invoice.getString("schoolId")).orElse("")));
        try {
            DocumentSnapshot paidInvoice = ref.get().get();
            String recipient = resolveBillingRecipient(paidInvoice);
            if (!recipient.isBlank()) billingEmailService.sendPaymentReceipt(ref, recipient);
        } catch (Exception ignored) { }
        return ResponseEntity.ok(toInvoice(ref.get().get()));
    }

    @PostMapping("/invoices/{invoiceId}/void")
    public ResponseEntity<?> voidInvoice(@PathVariable String invoiceId,
                                         @RequestBody VoidRequest req,
                                         @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        DocumentReference ref = firestore.collection("billingInvoices").document(invoiceId);
        DocumentSnapshot invoice = ref.get().get();
        if (!invoice.exists()) return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        if ("paid".equals(invoice.getString("status"))) return ResponseEntity.badRequest().body(Map.of("error", "Paid invoice cannot be voided"));
        ref.update("status", "void", "voidReason", clean(req.reason, 300), "updatedAt", FieldValue.serverTimestamp()).get();
        auditService.record(actor, "billing.invoice_voided", "billingInvoice", invoiceId, Map.of("schoolId", Optional.ofNullable(invoice.getString("schoolId")).orElse("")));
        return ResponseEntity.ok(toInvoice(ref.get().get()));
    }

    @PostMapping("/schools/{schoolId}/invoices/reconcile-overdue")
    public ResponseEntity<?> reconcileOverdue(@PathVariable String schoolId,
                                               @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        if (!schoolExists(schoolId)) return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        QuerySnapshot snapshot = firestore.collection("billingInvoices")
                .whereEqualTo("schoolId", schoolId).whereEqualTo("status", "open").get().get();
        int changed = 0;
        Instant now = Instant.now();
        for (DocumentSnapshot d : snapshot.getDocuments()) {
            Timestamp due = d.getTimestamp("dueAt");
            if (due != null && due.toDate().toInstant().isBefore(now)) {
                d.getReference().update("status", "overdue", "updatedAt", FieldValue.serverTimestamp()).get();
                changed++;
            }
        }
        auditService.record(actor, "billing.overdue_reconciled", "school", schoolId, Map.of("changed", changed));
        return ResponseEntity.ok(Map.of("changed", changed));
    }


    @GetMapping("/schools/{schoolId}/payment-notices")
    public ResponseEntity<?> paymentNotices(@PathVariable String schoolId) throws Exception {
        if (!schoolExists(schoolId)) return ResponseEntity.status(404).body(Map.of("error", "School not found"));
        QuerySnapshot snapshot = firestore.collection("billingPaymentNotices")
                .whereEqualTo("schoolId", schoolId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100).get().get();
        List<Map<String,Object>> rows = new ArrayList<>();
        for (DocumentSnapshot d : snapshot.getDocuments()) rows.add(toPaymentNotice(d));
        return ResponseEntity.ok(Map.of("paymentNotices", rows));
    }

    @PostMapping("/payment-notices/{noticeId}/confirm")
    public ResponseEntity<?> confirmGcashPayment(@PathVariable String noticeId,
                                                  @RequestBody(required = false) PaymentNoticeReviewRequest req,
                                                  @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        DocumentReference noticeRef = firestore.collection("billingPaymentNotices").document(noticeId);
        String reviewNote = req == null ? "" : clean(req.note, 500);

        String invoiceId;
        try {
            invoiceId = firestore.runTransaction(tx -> {
            DocumentSnapshot notice = tx.get(noticeRef).get();
            if (!notice.exists()) throw new IllegalArgumentException("Payment notice not found");
            String noticeStatus = Optional.ofNullable(notice.getString("status")).orElse("pending_review");
            if ("confirmed".equals(noticeStatus)) return Optional.ofNullable(notice.getString("invoiceId")).orElse("");
            if ("rejected".equals(noticeStatus)) throw new IllegalArgumentException("Rejected payment notice cannot be confirmed");

            String invId = Optional.ofNullable(notice.getString("invoiceId")).orElse("");
            if (invId.isBlank()) throw new IllegalArgumentException("Payment notice has no invoice");
            DocumentReference invoiceRef = firestore.collection("billingInvoices").document(invId);
            DocumentSnapshot invoice = tx.get(invoiceRef).get();
            if (!invoice.exists()) throw new IllegalArgumentException("Invoice not found");
            if (!Objects.equals(invoice.getString("schoolId"), notice.getString("schoolId")))
                throw new IllegalArgumentException("Payment notice does not belong to invoice school");
            String invoiceStatus = Optional.ofNullable(invoice.getString("status")).orElse("open");
            if ("void".equals(invoiceStatus)) throw new IllegalArgumentException("Void invoice cannot be paid");
            if ("paid".equals(invoiceStatus)) {
                String existingNoticeId = Optional.ofNullable(invoice.getString("paymentNoticeId")).orElse("");
                if (!noticeId.equals(existingNoticeId)) {
                    throw new IllegalArgumentException("Invoice is already paid using a different payment record");
                }
            }

            long invoiceAmount = Optional.ofNullable(invoice.getLong("amountMinor")).orElse(0L);
            long noticeAmount = Optional.ofNullable(notice.getLong("amountMinor")).orElse(-1L);
            if (invoiceAmount != noticeAmount) throw new IllegalArgumentException("Payment amount does not match invoice total");

            if (!"paid".equals(invoiceStatus)) {
                Map<String,Object> invoiceUpdate = new HashMap<>();
                invoiceUpdate.put("status", "paid");
                invoiceUpdate.put("paidAt", FieldValue.serverTimestamp());
                invoiceUpdate.put("paymentReference", Optional.ofNullable(notice.getString("referenceNumber")).orElse(""));
                invoiceUpdate.put("paymentMethod", "GCash (manual verification)");
                invoiceUpdate.put("paymentNote", reviewNote);
                invoiceUpdate.put("paymentProvider", "gcash_manual");
                invoiceUpdate.put("paymentNoticeId", noticeId);
                invoiceUpdate.put("updatedAt", FieldValue.serverTimestamp());
                tx.update(invoiceRef, invoiceUpdate);
            }

            Map<String,Object> noticeUpdate = new HashMap<>();
            noticeUpdate.put("status", "confirmed");
            noticeUpdate.put("reviewNote", reviewNote);
            noticeUpdate.put("reviewedByUid", actor.getUid());
            noticeUpdate.put("reviewedAt", FieldValue.serverTimestamp());
            noticeUpdate.put("updatedAt", FieldValue.serverTimestamp());
            tx.update(noticeRef, noticeUpdate);
            return invId;
            }).get();
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
            }
            throw e;
        }

        auditService.record(actor, "billing.gcash_payment_confirmed", "billingPaymentNotice", noticeId,
                Map.of("invoiceId", invoiceId));
        try {
            DocumentReference invoiceRef = firestore.collection("billingInvoices").document(invoiceId);
            DocumentSnapshot paidInvoice = invoiceRef.get().get();
            String recipient = resolveBillingRecipient(paidInvoice);
            if (!recipient.isBlank()) billingEmailService.sendPaymentReceipt(invoiceRef, recipient);
        } catch (Exception ignored) { }
        return ResponseEntity.ok(toPaymentNotice(noticeRef.get().get()));
    }

    @PostMapping("/payment-notices/{noticeId}/reject")
    public ResponseEntity<?> rejectGcashPayment(@PathVariable String noticeId,
                                                 @RequestBody PaymentNoticeReviewRequest req,
                                                 @AuthenticationPrincipal FirebaseUserDetails actor) throws Exception {
        String reason = clean(req == null ? null : req.note, 500);
        if (reason.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Rejection reason is required"));
        DocumentReference ref = firestore.collection("billingPaymentNotices").document(noticeId);
        DocumentSnapshot notice = ref.get().get();
        if (!notice.exists()) return ResponseEntity.status(404).body(Map.of("error", "Payment notice not found"));
        String status = Optional.ofNullable(notice.getString("status")).orElse("pending_review");
        if ("confirmed".equals(status)) return ResponseEntity.badRequest().body(Map.of("error", "Confirmed payment notice cannot be rejected"));
        ref.update(
                "status", "rejected",
                "reviewNote", reason,
                "reviewedByUid", actor.getUid(),
                "reviewedAt", FieldValue.serverTimestamp(),
                "updatedAt", FieldValue.serverTimestamp()
        ).get();
        auditService.record(actor, "billing.gcash_payment_rejected", "billingPaymentNotice", noticeId,
                Map.of("invoiceId", Optional.ofNullable(notice.getString("invoiceId")).orElse("")));
        return ResponseEntity.ok(toPaymentNotice(ref.get().get()));
    }

    private Map<String,Object> toPaymentNotice(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("noticeId", d.getId());
        for (String k : List.of("schoolId","invoiceId","invoiceNumber","currency","payerName","referenceNumber","note","status","reviewNote")) {
            m.put(k, Optional.ofNullable(d.get(k)).orElse(""));
        }
        m.put("amountMinor", Optional.ofNullable(d.getLong("amountMinor")).orElse(0L));
        for (String k : List.of("paidAtClaimed","createdAt","updatedAt","reviewedAt")) {
            Timestamp ts = d.getTimestamp(k); m.put(k, ts == null ? null : ts.toDate().toInstant().toString());
        }
        return m;
    }

    private String resolveBillingRecipient(DocumentSnapshot invoice) throws Exception {
        String schoolId = invoice.getString("schoolId");
        if (schoolId == null || schoolId.isBlank()) return clean(invoice.getString("billingEmailSnapshot"), 254);
        DocumentSnapshot school = firestore.collection("schools").document(schoolId).get().get();
        String current = school.exists() ? clean(school.getString("billingEmail"), 254) : "";
        if (!current.isBlank()) return current;
        String snapshot = clean(invoice.getString("billingEmailSnapshot"), 254);
        if (!snapshot.isBlank()) return snapshot;
        QuerySnapshot admins = firestore.collection("users")
                .whereEqualTo("schoolId", schoolId).limit(100).get().get();
        for (DocumentSnapshot admin : admins.getDocuments()) {
            if (!"school_admin".equals(admin.getString("role"))) continue;
            if (Boolean.FALSE.equals(admin.getBoolean("isActive"))) continue;
            String email = clean(admin.getString("email"), 254);
            if (!email.isBlank()) return email;
        }
        return "";
    }

    private boolean schoolExists(String schoolId) throws Exception { return firestore.collection("schools").document(schoolId).get().get().exists(); }
    private String normalizeCurrency(String v) {
        String c = v == null || v.isBlank() ? "PHP" : v.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a 3-letter ISO code");
        return c;
    }
    private static String clean(String v, int max) { if (v == null) return ""; String s=v.trim(); return s.length()<=max?s:s.substring(0,max); }
    private static String firstNonBlank(String... values) { for (String v:values) if (v!=null&&!v.isBlank()) return v.trim(); return ""; }
    private Map<String,Object> profileMap(DocumentSnapshot school) {
        return Map.of(
                "schoolId", school.getId(),
                "billingName", firstNonBlank(school.getString("billingName"), school.getString("schoolName")),
                "billingEmail", clean(school.getString("billingEmail"), 254),
                "billingAddress", clean(school.getString("billingAddress"), 500),
                "billingTaxId", clean(school.getString("billingTaxId"), 80)
        );
    }
    private Map<String,Object> toInvoice(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("invoiceId", d.getId());
        for (String k : List.of("schoolId","schoolNameSnapshot","billingNameSnapshot","billingEmailSnapshot","billingAddressSnapshot","billingTaxIdSnapshot","invoiceNumber","planSnapshot","currency","status","note","paymentReference","paymentMethod","paymentNote","paymentProvider","providerEventId","voidReason","lastEmailedTo")) m.put(k, Optional.ofNullable(d.get(k)).orElse(""));
        m.put("amountMinor", Optional.ofNullable(d.getLong("amountMinor")).orElse(0L));
        m.put("emailDeliveryCount", Optional.ofNullable(d.getLong("emailDeliveryCount")).orElse(0L));
        for (String k : List.of("dueAt","createdAt","updatedAt","paidAt","lastEmailedAt")) { Timestamp t=d.getTimestamp(k); m.put(k, t==null?null:t.toDate().toInstant().toString()); }
        return m;
    }

    public static class CreateInvoiceRequest { public Long amountMinor; public String currency; public String dueAt; public String note; }
    public static class PaymentRequest { public String paymentReference; public String paymentMethod; public String note; }
    public static class VoidRequest { public String reason; }
    public static class EmailInvoiceRequest { public String recipientEmail; }
    public static class BillingProfileRequest { public String billingName; public String billingEmail; public String billingAddress; public String billingTaxId; }
    public static class PaymentNoticeReviewRequest { public String note; }
}

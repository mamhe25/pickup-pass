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
import com.pickuppass.service.InvoicePdfService;
import com.pickuppass.service.ReceiptPdfService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

/**
 * School-facing billing center for the startup/manual-payment stage.
 *
 * The school can view invoices and submit a GCash payment notice. The notice
 * does NOT mark the invoice paid automatically. A master admin must reconcile
 * the payment against the receiving GCash account and confirm it.
 */
@RestController
@RequestMapping("/api/school-admin/billing")
@PreAuthorize("hasRole('school_admin')")
public class SchoolBillingController {
    private final Firestore firestore;
    private final AuditService auditService;
    private final InvoicePdfService invoicePdfService;
    private final ReceiptPdfService receiptPdfService;
    private final boolean gcashEnabled;
    private final String gcashAccountName;
    private final String gcashMobile;
    private final String gcashNote;

    public SchoolBillingController(
            Firestore firestore,
            AuditService auditService,
            InvoicePdfService invoicePdfService,
            ReceiptPdfService receiptPdfService,
            @Value("${BILLING_GCASH_ENABLED:true}") boolean gcashEnabled,
            @Value("${BILLING_GCASH_ACCOUNT_NAME:}") String gcashAccountName,
            @Value("${BILLING_GCASH_MOBILE:}") String gcashMobile,
            @Value("${BILLING_GCASH_NOTE:Send the exact invoice amount and keep your GCash reference number.}") String gcashNote) {
        this.firestore = firestore;
        this.auditService = auditService;
        this.invoicePdfService = invoicePdfService;
        this.receiptPdfService = receiptPdfService;
        this.gcashEnabled = gcashEnabled;
        this.gcashAccountName = clean(gcashAccountName, 120);
        this.gcashMobile = clean(gcashMobile, 40);
        this.gcashNote = clean(gcashNote, 500);
    }

    @GetMapping
    public ResponseEntity<?> billingCenter(@AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        String schoolId = admin.getSchoolId();
        QuerySnapshot invoiceSnap = firestore.collection("billingInvoices")
                .whereEqualTo("schoolId", schoolId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100).get().get();

        QuerySnapshot noticeSnap = firestore.collection("billingPaymentNotices")
                .whereEqualTo("schoolId", schoolId)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100).get().get();

        List<Map<String,Object>> invoices = new ArrayList<>();
        for (DocumentSnapshot d : invoiceSnap.getDocuments()) invoices.add(invoiceMap(d));
        List<Map<String,Object>> notices = new ArrayList<>();
        for (DocumentSnapshot d : noticeSnap.getDocuments()) notices.add(noticeMap(d));

        Map<String,Object> instructions = new LinkedHashMap<>();
        instructions.put("enabled", gcashEnabled && !gcashMobile.isBlank());
        instructions.put("accountName", gcashAccountName);
        instructions.put("mobile", gcashMobile);
        instructions.put("note", gcashNote);
        instructions.put("verificationMode", "manual_review");

        return ResponseEntity.ok(Map.of(
                "paymentInstructions", instructions,
                "invoices", invoices,
                "paymentNotices", notices
        ));
    }

    @GetMapping("/invoices/{invoiceId}/pdf")
    public ResponseEntity<?> invoicePdf(@PathVariable String invoiceId,
                                        @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        DocumentSnapshot invoice = firestore.collection("billingInvoices").document(invoiceId).get().get();
        if (!invoice.exists() || !admin.getSchoolId().equals(invoice.getString("schoolId")))
            return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        byte[] pdf = invoicePdfService.render(invoice);
        String name = Optional.ofNullable(invoice.getString("invoiceNumber")).orElse(invoiceId).replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(pdf);
    }

    @GetMapping("/invoices/{invoiceId}/receipt")
    public ResponseEntity<?> receiptPdf(@PathVariable String invoiceId,
                                        @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {
        DocumentSnapshot invoice = firestore.collection("billingInvoices").document(invoiceId).get().get();
        if (!invoice.exists() || !admin.getSchoolId().equals(invoice.getString("schoolId")))
            return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        try {
            byte[] pdf = receiptPdfService.render(invoice);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(receiptPdfService.receiptNumber(invoice)+".pdf").build().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").body(pdf);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/invoices/{invoiceId}/gcash-payment-notice")
    public ResponseEntity<?> submitPaymentNotice(
            @PathVariable String invoiceId,
            @RequestBody GcashPaymentNoticeRequest req,
            @AuthenticationPrincipal FirebaseUserDetails admin) throws Exception {

        if (!gcashEnabled || gcashMobile.isBlank()) {
            return ResponseEntity.status(503).body(Map.of("error", "GCash payment instructions are not configured"));
        }

        DocumentReference invoiceRef = firestore.collection("billingInvoices").document(invoiceId);
        DocumentSnapshot invoice = invoiceRef.get().get();
        if (!invoice.exists() || !admin.getSchoolId().equals(invoice.getString("schoolId"))) {
            return ResponseEntity.status(404).body(Map.of("error", "Invoice not found"));
        }
        String invoiceStatus = Optional.ofNullable(invoice.getString("status")).orElse("open");
        if ("paid".equals(invoiceStatus)) return ResponseEntity.badRequest().body(Map.of("error", "Invoice is already paid"));
        if ("void".equals(invoiceStatus)) return ResponseEntity.badRequest().body(Map.of("error", "Void invoice cannot receive a payment notice"));

        long invoiceAmount = Optional.ofNullable(invoice.getLong("amountMinor")).orElse(0L);
        long amountMinor = req.amountMinor == null ? invoiceAmount : req.amountMinor;
        if (amountMinor != invoiceAmount) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payment amount must match the invoice total exactly"));
        }

        String reference = clean(req.referenceNumber, 120);
        if (reference.length() < 4) return ResponseEntity.badRequest().body(Map.of("error", "GCash reference number is required"));
        String payerName = clean(req.payerName, 120);
        if (payerName.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "Payer name is required"));
        String paidAtText = clean(req.paidAt, 80);
        Instant paidAt;
        try { paidAt = paidAtText.isBlank() ? Instant.now() : Instant.parse(paidAtText); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", "paidAt must be ISO-8601")); }

        DocumentReference noticeRef = firestore.collection("billingPaymentNotices").document();
        DocumentReference claimRef = firestore.collection("billingPaymentReferenceClaims").document(hashReference(reference));
        Map<String,Object> data = new HashMap<>();
        data.put("schoolId", admin.getSchoolId());
        data.put("invoiceId", invoiceId);
        data.put("invoiceNumber", Optional.ofNullable(invoice.getString("invoiceNumber")).orElse(invoiceId));
        data.put("amountMinor", invoiceAmount);
        data.put("currency", Optional.ofNullable(invoice.getString("currency")).orElse("PHP"));
        data.put("payerName", payerName);
        data.put("referenceNumber", reference);
        data.put("paidAtClaimed", Date.from(paidAt));
        data.put("note", clean(req.note, 500));
        data.put("status", "pending_review");
        data.put("submittedByUid", admin.getUid());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        try {
            firestore.runTransaction(tx -> {
                DocumentSnapshot claim = tx.get(claimRef).get();
                if (claim.exists()) {
                    String existingNoticeId = Optional.ofNullable(claim.getString("noticeId")).orElse("");
                    if (!existingNoticeId.isBlank()) {
                        DocumentSnapshot existing = tx.get(firestore.collection("billingPaymentNotices").document(existingNoticeId)).get();
                        String existingStatus = existing.exists()
                                ? Optional.ofNullable(existing.getString("status")).orElse("pending_review")
                                : "pending_review";
                        if (!"rejected".equals(existingStatus)) {
                            throw new IllegalArgumentException("This GCash reference number has already been submitted");
                        }
                    }
                }
                tx.set(noticeRef, data);
                tx.set(claimRef, Map.of(
                        "noticeId", noticeRef.getId(),
                        "invoiceId", invoiceId,
                        "schoolId", admin.getSchoolId(),
                        "updatedAt", FieldValue.serverTimestamp()
                ));
                return null;
            }).get();
        } catch (Exception e) {
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            if (cause instanceof IllegalArgumentException) {
                return ResponseEntity.status(409).body(Map.of("error", cause.getMessage()));
            }
            throw e;
        }

        auditService.record(admin, "billing.gcash_payment_notice_submitted", "billingPaymentNotice", noticeRef.getId(),
                Map.of("invoiceId", invoiceId, "referenceNumber", reference));
        return ResponseEntity.ok(noticeMap(noticeRef.get().get()));
    }

    private Map<String,Object> invoiceMap(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("invoiceId", d.getId());
        for (String k : List.of("invoiceNumber","planSnapshot","currency","status","note","paymentReference","paymentMethod")) {
            m.put(k, Optional.ofNullable(d.get(k)).orElse(""));
        }
        m.put("amountMinor", Optional.ofNullable(d.getLong("amountMinor")).orElse(0L));
        m.put("receiptAvailable", "paid".equalsIgnoreCase(Optional.ofNullable(d.getString("status")).orElse("")));
        m.put("receiptNumber", Optional.ofNullable(d.getString("receiptNumber")).orElse(""));
        for (String k : List.of("dueAt","createdAt","paidAt")) {
            Timestamp t = d.getTimestamp(k); m.put(k, t == null ? null : t.toDate().toInstant().toString());
        }
        return m;
    }

    private Map<String,Object> noticeMap(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("noticeId", d.getId());
        for (String k : List.of("schoolId","invoiceId","invoiceNumber","currency","payerName","referenceNumber","note","status","reviewNote")) {
            m.put(k, Optional.ofNullable(d.get(k)).orElse(""));
        }
        m.put("amountMinor", Optional.ofNullable(d.getLong("amountMinor")).orElse(0L));
        for (String k : List.of("paidAtClaimed","createdAt","updatedAt","reviewedAt")) {
            Timestamp t=d.getTimestamp(k); m.put(k, t==null?null:t.toDate().toInstant().toString());
        }
        return m;
    }

    private static String hashReference(String reference) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(reference.trim().toUpperCase(Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String clean(String v, int max) {
        if (v == null) return "";
        String s = v.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }

    public static class GcashPaymentNoticeRequest {
        public String payerName;
        public String referenceNumber;
        public Long amountMinor;
        public String paidAt;
        public String note;
    }
}

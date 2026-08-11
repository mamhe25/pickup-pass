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
    private static final Set<String> STATUSES = Set.of("open", "paid", "void", "overdue");
    private final Firestore firestore;
    private final AuditService auditService;

    public MasterBillingController(Firestore firestore, AuditService auditService) {
        this.firestore = firestore;
        this.auditService = auditService;
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
        String number = "PP-" + Instant.now().toString().substring(0,10).replace("-","") + "-" + UUID.randomUUID().toString().substring(0,8).toUpperCase();
        DocumentReference ref = firestore.collection("billingInvoices").document();
        Map<String,Object> data = new HashMap<>();
        data.put("schoolId", schoolId);
        data.put("schoolNameSnapshot", Optional.ofNullable(school.getString("schoolName")).orElse("Unnamed school"));
        data.put("invoiceNumber", number);
        data.put("planSnapshot", Optional.ofNullable(school.getString("plan")).orElse("trial"));
        data.put("amountMinor", req.amountMinor);
        data.put("currency", currency);
        data.put("status", "open");
        data.put("dueAt", Date.from(dueAt));
        data.put("note", clean(req.note, 500));
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());
        ref.set(data).get();
        auditService.record(actor, "billing.invoice_created", "billingInvoice", ref.getId(), Map.of("schoolId", schoolId, "invoiceNumber", number));
        return ResponseEntity.ok(toInvoice(ref.get().get()));
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
        auditService.record(actor, "billing.invoice_paid", "billingInvoice", invoiceId, Map.of("schoolId", invoice.getString("schoolId")));
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
        auditService.record(actor, "billing.invoice_voided", "billingInvoice", invoiceId, Map.of("schoolId", invoice.getString("schoolId")));
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

    private boolean schoolExists(String schoolId) throws Exception { return firestore.collection("schools").document(schoolId).get().get().exists(); }
    private String normalizeCurrency(String v) {
        String c = v == null || v.isBlank() ? "PHP" : v.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z]{3}")) throw new IllegalArgumentException("currency must be a 3-letter ISO code");
        return c;
    }
    private String clean(String v, int max) { if (v == null) return ""; String s=v.trim(); return s.length()<=max?s:s.substring(0,max); }
    private Map<String,Object> toInvoice(DocumentSnapshot d) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("invoiceId", d.getId());
        for (String k : List.of("schoolId","schoolNameSnapshot","invoiceNumber","planSnapshot","currency","status","note","paymentReference","paymentMethod","paymentNote","voidReason")) m.put(k, Optional.ofNullable(d.get(k)).orElse(""));
        m.put("amountMinor", Optional.ofNullable(d.getLong("amountMinor")).orElse(0L));
        for (String k : List.of("dueAt","createdAt","updatedAt","paidAt")) { Timestamp t=d.getTimestamp(k); m.put(k, t==null?null:t.toDate().toInstant().toString()); }
        return m;
    }

    public static class CreateInvoiceRequest { public Long amountMinor; public String currency; public String dueAt; public String note; }
    public static class PaymentRequest { public String paymentReference; public String paymentMethod; public String note; }
    public static class VoidRequest { public String reason; }
}

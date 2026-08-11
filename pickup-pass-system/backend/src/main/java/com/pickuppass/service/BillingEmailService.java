package com.pickuppass.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class BillingEmailService {
    private static final Logger log = LoggerFactory.getLogger(BillingEmailService.class);
    private final JavaMailSender mailSender;
    private final InvoicePdfService invoicePdfService;
    private final ReceiptPdfService receiptPdfService;
    private final AuditService auditService;
    private final SaasOperationsHealthService operationsHealthService;
    private final String fromEmail;
    private final String fromName;
    private final String supportEmail;

    public BillingEmailService(JavaMailSender mailSender,
                               InvoicePdfService invoicePdfService,
                               ReceiptPdfService receiptPdfService,
                               AuditService auditService,
                               SaasOperationsHealthService operationsHealthService,
                               @Value("${pickuppass.billing.from-email:}") String fromEmail,
                               @Value("${pickuppass.billing.from-name:PickupPass Billing}") String fromName,
                               @Value("${pickuppass.billing.support-email:}") String supportEmail) {
        this.mailSender = mailSender;
        this.invoicePdfService = invoicePdfService;
        this.receiptPdfService = receiptPdfService;
        this.auditService = auditService;
        this.operationsHealthService = operationsHealthService;
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName == null || fromName.isBlank() ? "PickupPass Billing" : fromName.trim();
        this.supportEmail = supportEmail == null ? "" : supportEmail.trim();
    }

    public DeliveryResult sendInvoice(DocumentReference invoiceRef, String recipient) throws Exception {
        DocumentSnapshot invoice = requireInvoice(invoiceRef);
        byte[] pdf = invoicePdfService.render(invoice);
        String number = text(invoice, "invoiceNumber", invoice.getId());
        String schoolName = text(invoice, "schoolNameSnapshot", "School");
        String body = "Hello,\n\nAttached is PickupPass invoice " + number + " for " + schoolName + ".\n"
                + "Amount: " + money(invoice) + "\n"
                + "Due: " + date(invoice, "dueAt") + "\n\n"
                + "Please keep this message and the attached PDF for your billing records."
                + supportLine();
        try {
            send(recipient, "PickupPass invoice " + number + " - " + schoolName, body,
                    safeFilename(number) + ".pdf", pdf);
            markDeliverySuccess(invoiceRef);
        } catch (Exception e) {
            markDeliveryFailure(invoiceRef, "invoice", e);
            throw e;
        }
        bestEffortUpdate(invoiceRef, Map.of(
                "lastEmailedAt", FieldValue.serverTimestamp(),
                "lastEmailedTo", recipient.trim().toLowerCase(),
                "emailDeliveryCount", FieldValue.increment(1),
                "updatedAt", FieldValue.serverTimestamp()
        ), "BILLING_EMAIL_METADATA_UPDATE_FAILED");
        auditService.recordSystem(text(invoice, "schoolId", ""), "billing.invoice_emailed", "billingInvoice", invoice.getId(),
                Map.of("recipient", recipient.trim().toLowerCase(), "invoiceNumber", number));
        return new DeliveryResult(recipient.trim().toLowerCase(), number);
    }

    public DeliveryResult sendPaymentReceipt(DocumentReference invoiceRef, String recipient) throws Exception {
        DocumentSnapshot invoice = requireInvoice(invoiceRef);
        byte[] pdf = receiptPdfService.render(invoice);
        String number = receiptPdfService.receiptNumber(invoice);
        String schoolName = text(invoice, "schoolNameSnapshot", "School");
        String body = "Hello,\n\nPickupPass has confirmed payment for " + schoolName + ".\n"
                + "Invoice: " + text(invoice, "invoiceNumber", invoice.getId()) + "\n"
                + "Amount received: " + money(invoice) + "\n"
                + "Payment reference: " + text(invoice, "paymentReference", "-") + "\n\n"
                + "Your payment receipt is attached for your records." + supportLine();
        try {
            send(recipient, "PickupPass payment receipt " + number, body,
                    safeFilename(number) + ".pdf", pdf);
            markDeliverySuccess(invoiceRef);
        } catch (Exception e) {
            markDeliveryFailure(invoiceRef, "payment receipt", e);
            throw e;
        }
        bestEffortUpdate(invoiceRef, Map.of(
                "receiptNumber", number,
                "receiptLastEmailedAt", FieldValue.serverTimestamp(),
                "receiptLastEmailedTo", recipient.trim().toLowerCase(),
                "receiptEmailDeliveryCount", FieldValue.increment(1),
                "updatedAt", FieldValue.serverTimestamp()
        ), "BILLING_RECEIPT_METADATA_UPDATE_FAILED");
        auditService.recordSystem(text(invoice, "schoolId", ""), "billing.receipt_emailed", "billingInvoice", invoice.getId(),
                Map.of("recipient", recipient.trim().toLowerCase(), "receiptNumber", number));
        return new DeliveryResult(recipient.trim().toLowerCase(), number);
    }

    public DeliveryResult sendPaymentReminder(DocumentReference invoiceRef, String recipient, String reminderType) throws Exception {
        DocumentSnapshot invoice = requireInvoice(invoiceRef);
        String number = text(invoice, "invoiceNumber", invoice.getId());
        String schoolName = text(invoice, "schoolNameSnapshot", "School");
        String subject = "overdue".equals(reminderType)
                ? "PickupPass invoice overdue: " + number
                : "PickupPass invoice reminder: " + number;
        String firstLine = "overdue".equals(reminderType)
                ? "This is a payment reminder that the following PickupPass invoice is overdue."
                : "This is a reminder that the following PickupPass invoice is approaching its due date.";
        String body = "Hello,\n\n" + firstLine + "\n\n"
                + "School: " + schoolName + "\n"
                + "Invoice: " + number + "\n"
                + "Amount: " + money(invoice) + "\n"
                + "Due: " + date(invoice, "dueAt") + "\n\n"
                + "If payment has already been sent via GCash, submit the payment reference in PickupPass for verification."
                + supportLine();
        byte[] pdf = invoicePdfService.render(invoice);
        try {
            send(recipient, subject, body, safeFilename(number) + ".pdf", pdf);
            markDeliverySuccess(invoiceRef);
        } catch (Exception e) {
            markDeliveryFailure(invoiceRef, "payment reminder", e);
            throw e;
        }
        auditService.recordSystem(text(invoice, "schoolId", ""), "billing.payment_reminder_emailed", "billingInvoice", invoice.getId(),
                Map.of("recipient", recipient.trim().toLowerCase(), "type", reminderType));
        return new DeliveryResult(recipient.trim().toLowerCase(), number);
    }

    private DocumentSnapshot requireInvoice(DocumentReference invoiceRef) throws Exception {
        DocumentSnapshot invoice = invoiceRef.get().get();
        if (!invoice.exists()) throw new IllegalArgumentException("Invoice not found");
        return invoice;
    }

    private void send(String recipient, String subject, String body, String attachmentName, byte[] attachment) throws Exception {
        if (fromEmail.isBlank()) throw new IllegalStateException("Billing email is not configured (BILLING_FROM_EMAIL)");
        InternetAddress to = new InternetAddress(recipient, true); to.validate();
        InternetAddress from = new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name());
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from); helper.setTo(to);
        if (!supportEmail.isBlank()) helper.setReplyTo(supportEmail);
        helper.setSubject(subject); helper.setText(body, false);
        helper.addAttachment(attachmentName, new ByteArrayResource(attachment), "application/pdf");
        try { mailSender.send(message); }
        catch (MailException e) { throw e; }
    }



    private void markDeliverySuccess(DocumentReference ref) {
        Map<String,Object> update = new java.util.HashMap<>();
        update.put("emailDeliveryFailed", false);
        update.put("lastEmailFailureAt", FieldValue.delete());
        update.put("lastEmailFailureType", FieldValue.delete());
        update.put("lastEmailFailureMessage", FieldValue.delete());
        update.put("updatedAt", FieldValue.serverTimestamp());
        bestEffortUpdate(ref, update, "BILLING_EMAIL_FAILURE_CLEAR_FAILED");
        operationsHealthService.resolve("billing_email_failed", ref.getId());
    }

    private void markDeliveryFailure(DocumentReference ref, String type, Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (message.length() > 300) message = message.substring(0, 300);
        Map<String,Object> update = new java.util.HashMap<>();
        update.put("emailDeliveryFailed", true);
        update.put("lastEmailFailureAt", FieldValue.serverTimestamp());
        update.put("lastEmailFailureType", type);
        update.put("lastEmailFailureMessage", message);
        update.put("updatedAt", FieldValue.serverTimestamp());
        bestEffortUpdate(ref, update, "BILLING_EMAIL_FAILURE_METADATA_UPDATE_FAILED");
        operationsHealthService.signalBillingEmailFailure(ref, type, message);
    }

    private void bestEffortUpdate(DocumentReference ref, Map<String,Object> update, String label) {
        try { ref.update(update).get(); }
        catch (Exception e) { log.error("{} invoiceId={}", label, ref.getId(), e); }
    }
    private String supportLine() { return supportEmail.isBlank() ? "" : "\n\nBilling questions: " + supportEmail; }
    private static long longValue(DocumentSnapshot d, String key) { Long v = d.getLong(key); return v == null ? 0L : v; }
    private static String text(DocumentSnapshot d, String key, String fallback) { String v=d.getString(key); return v==null||v.isBlank()?fallback:v; }
    private static String safeFilename(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private static String date(DocumentSnapshot d, String key) { return d.getTimestamp(key) == null ? "-" : d.getTimestamp(key).toDate().toInstant().toString().substring(0,10); }
    private static String money(DocumentSnapshot d) { return text(d,"currency","PHP") + " " + String.format("%.2f", longValue(d,"amountMinor") / 100.0); }
    public record DeliveryResult(String recipient, String invoiceNumber) {}
}

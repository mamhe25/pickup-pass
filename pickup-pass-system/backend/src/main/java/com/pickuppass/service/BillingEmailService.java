package com.pickuppass.service;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class BillingEmailService {
    private static final Logger log = LoggerFactory.getLogger(BillingEmailService.class);
    private final JavaMailSender mailSender;
    private final InvoicePdfService pdfService;
    private final AuditService auditService;
    private final String fromEmail;
    private final String fromName;
    private final String supportEmail;

    public BillingEmailService(JavaMailSender mailSender,
                               InvoicePdfService pdfService,
                               AuditService auditService,
                               @Value("${pickuppass.billing.from-email:}") String fromEmail,
                               @Value("${pickuppass.billing.from-name:PickupPass Billing}") String fromName,
                               @Value("${pickuppass.billing.support-email:}") String supportEmail) {
        this.mailSender = mailSender;
        this.pdfService = pdfService;
        this.auditService = auditService;
        this.fromEmail = fromEmail == null ? "" : fromEmail.trim();
        this.fromName = fromName == null || fromName.isBlank() ? "PickupPass Billing" : fromName.trim();
        this.supportEmail = supportEmail == null ? "" : supportEmail.trim();
    }

    public DeliveryResult sendInvoice(DocumentReference invoiceRef, String recipient) throws Exception {
        if (fromEmail.isBlank()) throw new IllegalStateException("Billing email is not configured (BILLING_FROM_EMAIL)");
        InternetAddress to = new InternetAddress(recipient, true); to.validate();
        InternetAddress from = new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name());

        DocumentSnapshot invoice = invoiceRef.get().get();
        if (!invoice.exists()) throw new IllegalArgumentException("Invoice not found");
        byte[] pdf = pdfService.render(invoice);
        String number = text(invoice, "invoiceNumber", invoice.getId());
        String schoolName = text(invoice, "schoolNameSnapshot", "School");

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        if (!supportEmail.isBlank()) helper.setReplyTo(supportEmail);
        helper.setSubject("PickupPass invoice " + number + " - " + schoolName);
        String body = "Hello,\n\nAttached is PickupPass invoice " + number + " for " + schoolName + ".\n"
                + "Amount: " + text(invoice, "currency", "PHP") + " " + String.format("%.2f", longValue(invoice, "amountMinor") / 100.0) + "\n"
                + "Due: " + (invoice.getTimestamp("dueAt") == null ? "-" : invoice.getTimestamp("dueAt").toDate().toInstant().toString().substring(0, 10)) + "\n\n"
                + "Please keep this message and the attached PDF for your billing records."
                + (supportEmail.isBlank() ? "" : "\n\nBilling questions: " + supportEmail);
        helper.setText(body, false);
        helper.addAttachment(safeFilename(number) + ".pdf", new ByteArrayResource(pdf), "application/pdf");
        mailSender.send(message);

        String normalizedRecipient = recipient.trim().toLowerCase();
        // The email has already left the mail server at this point. Metadata/audit failures
        // must not make the API report a false send failure and encourage duplicate sends.
        try {
            invoiceRef.update(Map.of(
                    "lastEmailedAt", FieldValue.serverTimestamp(),
                    "lastEmailedTo", normalizedRecipient,
                    "emailDeliveryCount", FieldValue.increment(1),
                    "updatedAt", FieldValue.serverTimestamp()
            )).get();
        } catch (Exception metadataError) {
            log.error("BILLING_EMAIL_METADATA_UPDATE_FAILED invoiceId={} recipient={}", invoice.getId(), normalizedRecipient, metadataError);
        }
        String schoolId = text(invoice, "schoolId", "");
        auditService.recordSystem(schoolId, "billing.invoice_emailed", "billingInvoice", invoice.getId(),
                Map.of("recipient", normalizedRecipient, "invoiceNumber", number));
        return new DeliveryResult(normalizedRecipient, number);
    }

    private static long longValue(DocumentSnapshot d, String key) { Long v = d.getLong(key); return v == null ? 0L : v; }
    private static String text(DocumentSnapshot d, String key, String fallback) { String v=d.getString(key); return v==null||v.isBlank()?fallback:v; }
    private static String safeFilename(String value) { return value.replaceAll("[^A-Za-z0-9._-]", "_"); }
    public record DeliveryResult(String recipient, String invoiceNumber) {}
}

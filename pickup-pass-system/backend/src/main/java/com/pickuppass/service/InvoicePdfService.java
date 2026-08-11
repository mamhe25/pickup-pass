package com.pickuppass.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

/** Generates immutable, on-demand invoice PDFs from the invoice snapshots stored in Firestore. */
@Service
public class InvoicePdfService {
    private static final float MARGIN = 46f;
    private final String companyName;
    private final String companyAddress;
    private final String supportEmail;
    private final boolean gcashEnabled;
    private final String gcashAccountName;
    private final String gcashMobile;
    private final String gcashNote;
    private final ZoneId displayZone;

    public InvoicePdfService(
            @Value("${pickuppass.billing.company-name:PickupPass}") String companyName,
            @Value("${pickuppass.billing.company-address:}") String companyAddress,
            @Value("${pickuppass.billing.support-email:}") String supportEmail,
            @Value("${BILLING_GCASH_ENABLED:true}") boolean gcashEnabled,
            @Value("${BILLING_GCASH_ACCOUNT_NAME:}") String gcashAccountName,
            @Value("${BILLING_GCASH_MOBILE:}") String gcashMobile,
            @Value("${BILLING_GCASH_NOTE:Send the exact invoice amount and keep your GCash reference number.}") String gcashNote,
            @Value("${app.school-time-zone:Asia/Manila}") String displayZone) {
        this.companyName = blankTo(companyName, "PickupPass");
        this.companyAddress = safe(companyAddress);
        this.supportEmail = safe(supportEmail);
        this.gcashEnabled = gcashEnabled;
        this.gcashAccountName = safe(gcashAccountName);
        this.gcashMobile = safe(gcashMobile);
        this.gcashNote = safe(gcashNote);
        this.displayZone = ZoneId.of(displayZone);
    }

    public byte[] render(DocumentSnapshot invoice) throws Exception {
        if (invoice == null || !invoice.exists()) throw new IllegalArgumentException("Invoice not found");
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;
                text(cs, bold, 22, MARGIN, y, companyName);
                y -= 20;
                if (!companyAddress.isBlank()) { text(cs, regular, 9, MARGIN, y, companyAddress); y -= 14; }
                if (!supportEmail.isBlank()) { text(cs, regular, 9, MARGIN, y, supportEmail); y -= 14; }

                float rightX = 355f;
                text(cs, bold, 24, rightX, page.getMediaBox().getHeight() - MARGIN, "INVOICE");
                text(cs, regular, 10, rightX, page.getMediaBox().getHeight() - MARGIN - 20,
                        "Invoice #: " + value(invoice, "invoiceNumber", invoice.getId()));
                text(cs, regular, 10, rightX, page.getMediaBox().getHeight() - MARGIN - 34,
                        "Issued: " + date(invoice.getTimestamp("createdAt")));
                text(cs, regular, 10, rightX, page.getMediaBox().getHeight() - MARGIN - 48,
                        "Due: " + date(invoice.getTimestamp("dueAt")));
                text(cs, bold, 10, rightX, page.getMediaBox().getHeight() - MARGIN - 66,
                        "Status: " + value(invoice, "status", "open").toUpperCase(Locale.ROOT));

                y -= 24;
                line(cs, MARGIN, y, page.getMediaBox().getWidth() - MARGIN, y);
                y -= 28;
                text(cs, bold, 11, MARGIN, y, "Bill to");
                y -= 17;
                text(cs, bold, 12, MARGIN, y, value(invoice, "billingNameSnapshot", value(invoice, "schoolNameSnapshot", "School")));
                y -= 15;
                String billingEmail = value(invoice, "billingEmailSnapshot", "");
                if (!billingEmail.isBlank()) { text(cs, regular, 10, MARGIN, y, billingEmail); y -= 14; }
                String billingAddress = value(invoice, "billingAddressSnapshot", "");
                if (!billingAddress.isBlank()) { y = wrapped(cs, regular, 10, MARGIN, y, 470, billingAddress); }
                String taxId = value(invoice, "billingTaxIdSnapshot", "");
                if (!taxId.isBlank()) { text(cs, regular, 10, MARGIN, y, "Tax / Registration ID: " + taxId); y -= 14; }

                y -= 20;
                line(cs, MARGIN, y, page.getMediaBox().getWidth() - MARGIN, y);
                y -= 25;
                text(cs, bold, 10, MARGIN, y, "DESCRIPTION");
                text(cs, bold, 10, 430, y, "AMOUNT");
                y -= 18;
                String plan = value(invoice, "planSnapshot", "subscription");
                text(cs, regular, 11, MARGIN, y, "PickupPass " + capitalize(plan) + " subscription");
                text(cs, regular, 11, 430, y, money(invoice));
                y -= 22;
                String note = value(invoice, "note", "");
                if (!note.isBlank()) y = wrapped(cs, regular, 9, MARGIN, y, 470, note);
                y -= 10;
                line(cs, MARGIN, y, page.getMediaBox().getWidth() - MARGIN, y);
                y -= 28;
                text(cs, bold, 13, 365, y, "TOTAL");
                text(cs, bold, 13, 430, y, money(invoice));

                boolean invoiceGcashEnabled = invoice.contains("gcashEnabledSnapshot")
                        ? Boolean.TRUE.equals(invoice.getBoolean("gcashEnabledSnapshot"))
                        : gcashEnabled;
                String invoiceGcashName = value(invoice, "gcashAccountNameSnapshot", gcashAccountName);
                String invoiceGcashMobile = value(invoice, "gcashMobileSnapshot", gcashMobile);
                String invoiceGcashNote = value(invoice, "gcashNoteSnapshot", gcashNote);

                if ("paid".equalsIgnoreCase(value(invoice, "status", ""))) {
                    y -= 30;
                    text(cs, bold, 11, MARGIN, y, "PAID " + date(invoice.getTimestamp("paidAt")));
                    String ref = value(invoice, "paymentReference", "");
                    String method = value(invoice, "paymentMethod", "");
                    if (!ref.isBlank() || !method.isBlank()) {
                        y -= 15;
                        text(cs, regular, 9, MARGIN, y, "Payment: " + (method + " " + ref).trim());
                    }
                }
                else if (invoiceGcashEnabled && !invoiceGcashMobile.isBlank()) {
                    y -= 34;
                    text(cs, bold, 11, MARGIN, y, "PAYMENT INSTRUCTIONS - GCASH");
                    y -= 16;
                    text(cs, regular, 10, MARGIN, y, "Account: " + (invoiceGcashName.isBlank() ? "Configured GCash account" : invoiceGcashName));
                    y -= 14;
                    text(cs, regular, 10, MARGIN, y, "GCash mobile: " + invoiceGcashMobile);
                    y -= 14;
                    text(cs, regular, 9, MARGIN, y, "Send the exact invoice total. Submit the GCash reference in PickupPass for manual verification.");
                    if (!invoiceGcashNote.isBlank()) {
                        y -= 14;
                        y = wrapped(cs, regular, 9, MARGIN, y, 470, invoiceGcashNote);
                    }
                }

                text(cs, regular, 8, MARGIN, 34,
                        "Generated by PickupPass. Keep this invoice with your official billing records.");
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private String money(DocumentSnapshot d) {
        long minor = Optional.ofNullable(d.getLong("amountMinor")).orElse(0L);
        String currency = value(d, "currency", "PHP");
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
        nf.setMinimumFractionDigits(2); nf.setMaximumFractionDigits(2);
        return currency + " " + nf.format(minor / 100.0);
    }

    private String date(Timestamp ts) {
        if (ts == null) return "-";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(displayZone).format(ts.toDate().toInstant());
    }

    private static String value(DocumentSnapshot d, String key, String fallback) {
        Object v = d.get(key);
        return v == null || String.valueOf(v).isBlank() ? fallback : safe(String.valueOf(v));
    }
    private static String capitalize(String v) { return v == null || v.isBlank() ? "" : Character.toUpperCase(v.charAt(0)) + v.substring(1); }
    private static String blankTo(String v, String fallback) { return v == null || v.isBlank() ? fallback : safe(v); }
    private static String safe(String v) {
        if (v == null) return "";
        return v.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').replaceAll("[^\\x20-\\x7E]", "?").trim();
    }
    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws Exception {
        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(safe(value)); cs.endText();
    }
    private static void line(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws Exception {
        cs.setLineWidth(0.6f); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }
    private static float wrapped(PDPageContentStream cs, PDType1Font font, float size, float x, float y, float width, String raw) throws Exception {
        String[] words = safe(raw).split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.getStringWidth(candidate) / 1000f * size > width && line.length() > 0) {
                text(cs, font, size, x, y, line.toString()); y -= size + 4; line = new StringBuilder(word);
            } else line = new StringBuilder(candidate);
        }
        if (line.length() > 0) { text(cs, font, size, x, y, line.toString()); y -= size + 4; }
        return y;
    }
}

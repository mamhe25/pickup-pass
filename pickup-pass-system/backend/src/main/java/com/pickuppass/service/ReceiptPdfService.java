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

/** Generates a payment receipt only after an invoice has been confirmed paid. */
@Service
public class ReceiptPdfService {
    private static final float MARGIN = 46f;
    private final String companyName;
    private final String companyAddress;
    private final String supportEmail;
    private final ZoneId displayZone;

    public ReceiptPdfService(
            @Value("${pickuppass.billing.company-name:PickupPass}") String companyName,
            @Value("${pickuppass.billing.company-address:}") String companyAddress,
            @Value("${pickuppass.billing.support-email:}") String supportEmail,
            @Value("${app.school-time-zone:Asia/Manila}") String displayZone) {
        this.companyName = blankTo(companyName, "PickupPass");
        this.companyAddress = safe(companyAddress);
        this.supportEmail = safe(supportEmail);
        this.displayZone = ZoneId.of(displayZone);
    }

    public byte[] render(DocumentSnapshot invoice) throws Exception {
        if (invoice == null || !invoice.exists()) throw new IllegalArgumentException("Invoice not found");
        if (!"paid".equalsIgnoreCase(value(invoice, "status", ""))) {
            throw new IllegalArgumentException("Receipt is available only for a paid invoice");
        }
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
                text(cs, bold, 24, rightX, page.getMediaBox().getHeight() - MARGIN, "PAYMENT RECEIPT");
                text(cs, regular, 10, rightX, page.getMediaBox().getHeight() - MARGIN - 20,
                        "Receipt #: " + receiptNumber(invoice));
                text(cs, regular, 10, rightX, page.getMediaBox().getHeight() - MARGIN - 34,
                        "Invoice #: " + value(invoice, "invoiceNumber", invoice.getId()));
                text(cs, regular, 10, rightX, page.getMediaBox().getHeight() - MARGIN - 48,
                        "Paid: " + date(invoice.getTimestamp("paidAt")));

                y -= 28;
                line(cs, MARGIN, y, page.getMediaBox().getWidth() - MARGIN, y);
                y -= 28;
                text(cs, bold, 11, MARGIN, y, "Received from");
                y -= 18;
                text(cs, bold, 12, MARGIN, y,
                        value(invoice, "billingNameSnapshot", value(invoice, "schoolNameSnapshot", "School")));
                y -= 16;
                String email = value(invoice, "billingEmailSnapshot", "");
                if (!email.isBlank()) { text(cs, regular, 10, MARGIN, y, email); y -= 14; }

                y -= 18;
                line(cs, MARGIN, y, page.getMediaBox().getWidth() - MARGIN, y);
                y -= 28;
                text(cs, bold, 10, MARGIN, y, "DESCRIPTION");
                text(cs, bold, 10, 420, y, "AMOUNT RECEIVED");
                y -= 20;
                text(cs, regular, 11, MARGIN, y,
                        "PickupPass " + capitalize(value(invoice, "planSnapshot", "subscription")) + " subscription");
                text(cs, bold, 11, 420, y, money(invoice));
                y -= 30;
                text(cs, regular, 10, MARGIN, y, "Payment method: " + value(invoice, "paymentMethod", "Manual payment"));
                y -= 16;
                String reference = value(invoice, "paymentReference", "");
                if (!reference.isBlank()) { text(cs, regular, 10, MARGIN, y, "Payment reference: " + reference); y -= 16; }
                String paymentNote = value(invoice, "paymentNote", "");
                if (!paymentNote.isBlank()) { y = wrapped(cs, regular, 9, MARGIN, y, 470, "Payment note: " + paymentNote); }

                y -= 20;
                line(cs, MARGIN, y, page.getMediaBox().getWidth() - MARGIN, y);
                y -= 28;
                text(cs, bold, 13, 350, y, "TOTAL RECEIVED");
                text(cs, bold, 13, 455, y, money(invoice));

                text(cs, regular, 8, MARGIN, 34,
                        "Generated by PickupPass after payment confirmation. Keep this receipt with your billing records.");
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    public String receiptNumber(DocumentSnapshot invoice) {
        String explicit = value(invoice, "receiptNumber", "");
        if (!explicit.isBlank()) return explicit;
        String number = value(invoice, "invoiceNumber", invoice.getId());
        return "RCPT-" + number.replaceFirst("^PP-", "");
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
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(displayZone).format(ts.toDate().toInstant());
    }
    private static String value(DocumentSnapshot d, String key, String fallback) {
        Object v = d.get(key); return v == null || String.valueOf(v).isBlank() ? fallback : safe(String.valueOf(v));
    }
    private static String capitalize(String v) { return v == null || v.isBlank() ? "" : Character.toUpperCase(v.charAt(0)) + v.substring(1); }
    private static String blankTo(String v, String fallback) { return v == null || v.isBlank() ? fallback : safe(v); }
    private static String safe(String v) { return v == null ? "" : v.replace('\n',' ').replace('\r',' ').replace('\t',' ').replaceAll("[^\\x20-\\x7E]", "?").trim(); }
    private static void text(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String value) throws Exception {
        cs.beginText(); cs.setFont(font, size); cs.newLineAtOffset(x, y); cs.showText(safe(value)); cs.endText();
    }
    private static void line(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws Exception {
        cs.setLineWidth(0.6f); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
    }
    private static float wrapped(PDPageContentStream cs, PDType1Font font, float size, float x, float y, float width, String raw) throws Exception {
        String[] words = safe(raw).split("\\s+"); StringBuilder line = new StringBuilder();
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

package com.pickuppass.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** Provider-neutral HMAC adapter. Replace/add adapters for Stripe, Xendit, PayMongo, etc. without changing invoice logic. */
@Component
public class GenericHmacPaymentWebhookAdapter implements PaymentWebhookAdapter {
    private final ObjectMapper mapper;
    private final HmacWebhookVerifier verifier;
    private final String secret;

    public GenericHmacPaymentWebhookAdapter(ObjectMapper mapper, HmacWebhookVerifier verifier,
                                             @Value("${pickuppass.payment-webhooks.generic-hmac-secret:}") String secret) {
        this.mapper = mapper; this.verifier = verifier; this.secret = secret == null ? "" : secret;
    }

    @Override public String provider() { return "generic-hmac"; }

    @Override
    public PaymentEvent verifyAndParse(Map<String,String> headers, byte[] rawBody, Instant now) throws Exception {
        if (secret.isBlank()) throw new WebhookUnavailableException("Payment webhook secret is not configured");
        String eventId = header(headers, "x-webhook-event-id");
        String timestamp = header(headers, "x-webhook-timestamp");
        String signature = header(headers, "x-webhook-signature");
        if (eventId == null || eventId.isBlank() || eventId.length() > 160) throw new WebhookValidationException("Missing or invalid event id");
        if (!verifier.verify(secret, timestamp, rawBody, signature, now)) throw new WebhookSignatureException("Invalid webhook signature");

        JsonNode root = mapper.readTree(rawBody);
        String type = requiredText(root, "type");
        JsonNode data = root.path("data");
        String invoiceId = requiredText(data, "invoiceId");
        Long amountMinor = data.hasNonNull("amountMinor") ? data.get("amountMinor").longValue() : null;
        String currency = data.hasNonNull("currency") ? data.get("currency").asText().toUpperCase(Locale.ROOT) : null;
        String reference = data.hasNonNull("paymentReference") ? data.get("paymentReference").asText() : "";
        Instant occurredAt = now;
        if (root.hasNonNull("occurredAt")) {
            try { occurredAt = Instant.parse(root.get("occurredAt").asText()); } catch (Exception ignored) { }
        }
        return new PaymentEvent(eventId, type, invoiceId, amountMinor, currency, reference, occurredAt, sha256(rawBody));
    }

    private static String requiredText(JsonNode node, String name) {
        String v = node.path(name).asText("").trim();
        if (v.isBlank()) throw new WebhookValidationException("Missing " + name);
        return v;
    }
    private static String header(Map<String,String> h, String key) { return h.get(key); }
    private static String sha256(byte[] body) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body)); }

    public static class WebhookSignatureException extends RuntimeException { public WebhookSignatureException(String m){super(m);} }
    public static class WebhookValidationException extends RuntimeException { public WebhookValidationException(String m){super(m);} }
    public static class WebhookUnavailableException extends RuntimeException { public WebhookUnavailableException(String m){super(m);} }
}

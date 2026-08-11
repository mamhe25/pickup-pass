package com.pickuppass.service;

import java.time.Instant;
import java.util.Map;

public interface PaymentWebhookAdapter {
    String provider();
    PaymentEvent verifyAndParse(Map<String,String> headers, byte[] rawBody, Instant now) throws Exception;

    record PaymentEvent(String eventId, String type, String invoiceId, Long amountMinor,
                        String currency, String paymentReference, Instant occurredAt, String payloadHash) {}
}

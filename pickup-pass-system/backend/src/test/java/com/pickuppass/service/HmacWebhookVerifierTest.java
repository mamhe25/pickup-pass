package com.pickuppass.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class HmacWebhookVerifierTest {
    @Test void acceptsValidRecentSignatureAndRejectsTamperingAndReplay() throws Exception {
        HmacWebhookVerifier verifier = new HmacWebhookVerifier();
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String ts = String.valueOf(now.getEpochSecond());
        byte[] body = "{\"type\":\"payment.succeeded\"}".getBytes();
        String signature = HexFormat.of().formatHex(verifier.sign("test-secret", ts, body));
        assertTrue(verifier.verify("test-secret", ts, body, signature, now));
        assertFalse(verifier.verify("test-secret", ts, "changed".getBytes(), signature, now));
        assertFalse(verifier.verify("test-secret", ts, body, signature, now.plusSeconds(301)));
    }
}

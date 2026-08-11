package com.pickuppass.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class HmacWebhookVerifier {
    private static final long MAX_CLOCK_SKEW_SECONDS = 300;

    public boolean verify(String secret, String timestamp, byte[] body, String suppliedHexSignature, Instant now) {
        if (secret == null || secret.isBlank() || timestamp == null || suppliedHexSignature == null) return false;
        long epoch;
        try { epoch = Long.parseLong(timestamp); } catch (NumberFormatException e) { return false; }
        if (Math.abs(now.getEpochSecond() - epoch) > MAX_CLOCK_SKEW_SECONDS) return false;
        try {
            byte[] expected = sign(secret, timestamp, body);
            byte[] supplied = HexFormat.of().parseHex(suppliedHexSignature.trim().toLowerCase());
            return MessageDigest.isEqual(expected, supplied);
        } catch (Exception e) { return false; }
    }

    byte[] sign(String secret, String timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) '.');
        mac.update(body);
        return mac.doFinal();
    }
}

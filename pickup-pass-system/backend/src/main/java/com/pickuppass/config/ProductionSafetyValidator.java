package com.pickuppass.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;

/** Refuses to start a production deployment with known development/default security values. */
@Component
public class ProductionSafetyValidator {
    private final Environment environment;
    private final String qrSecret;
    private final String bootstrapSecret;
    private final String frontendUrl;
    private final String corsAllowedOrigins;
    private final String securityFingerprintSecret;

    public ProductionSafetyValidator(Environment environment,
            @Value("${qr.signing.secret:}") String qrSecret,
            @Value("${bootstrap.secret:}") String bootstrapSecret,
            @Value("${app.frontend-base-url:}") String frontendUrl,
            @Value("${app.cors.allowed-origins:}") String corsAllowedOrigins,
            @Value("${pickuppass.security.fingerprint-secret:}") String securityFingerprintSecret) {
        this.environment = environment;
        this.qrSecret = qrSecret;
        this.bootstrapSecret = bootstrapSecret;
        this.frontendUrl = frontendUrl;
        this.corsAllowedOrigins = corsAllowedOrigins;
        this.securityFingerprintSecret = securityFingerprintSecret;
    }

    @PostConstruct
    public void validate() {
        boolean production = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));
        if (!production) return;

        if (qrSecret == null || qrSecret.length() < 32 || qrSecret.contains("change-this")) {
            throw new IllegalStateException("Production requires QR_SIGNING_SECRET with at least 32 random characters");
        }
        if (bootstrapSecret == null || bootstrapSecret.length() < 32) {
            throw new IllegalStateException("Production requires BOOTSTRAP_SECRET with at least 32 random characters");
        }
        if (securityFingerprintSecret == null || securityFingerprintSecret.length() < 32
                || securityFingerprintSecret.contains("development-security")) {
            throw new IllegalStateException("Production requires SECURITY_FINGERPRINT_SECRET with at least 32 random characters");
        }
        requireHttpsPublicUrl(frontendUrl, "FRONTEND_BASE_URL");
        validateCorsOrigins(corsAllowedOrigins);
    }

    private void validateCorsOrigins(String origins) {
        if (origins == null || origins.isBlank()) {
            throw new IllegalStateException("Production requires CORS_ALLOWED_ORIGINS");
        }
        for (String raw : origins.split(",")) {
            String origin = raw.trim();
            if (origin.isBlank()) continue;
            if (origin.contains("*")) {
                throw new IllegalStateException("Production CORS_ALLOWED_ORIGINS must not contain wildcards");
            }
            requireHttpsPublicUrl(origin, "CORS_ALLOWED_ORIGINS");
        }
    }

    private void requireHttpsPublicUrl(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Production " + name + " is required");
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || host.isBlank()) {
                throw new IllegalStateException("Production " + name + " must use a valid https:// origin");
            }
            String normalizedHost = host.toLowerCase();
            if (normalizedHost.equals("localhost") || normalizedHost.equals("127.0.0.1") || normalizedHost.equals("::1")) {
                throw new IllegalStateException("Production " + name + " must not use a local development host");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Production " + name + " must contain a valid URL", e);
        }
    }
}

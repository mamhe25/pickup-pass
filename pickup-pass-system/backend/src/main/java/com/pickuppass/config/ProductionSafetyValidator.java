package com.pickuppass.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** Refuses to start a production deployment with known development/default security values. */
@Component
public class ProductionSafetyValidator {
    private final Environment environment;
    private final String qrSecret;
    private final String bootstrapSecret;
    private final String frontendUrl;

    public ProductionSafetyValidator(Environment environment,
            @Value("${qr.signing.secret:}") String qrSecret,
            @Value("${bootstrap.secret:}") String bootstrapSecret,
            @Value("${app.frontend-base-url:}") String frontendUrl) {
        this.environment = environment;
        this.qrSecret = qrSecret;
        this.bootstrapSecret = bootstrapSecret;
        this.frontendUrl = frontendUrl;
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
        if (frontendUrl == null || !frontendUrl.startsWith("https://")) {
            throw new IllegalStateException("Production FRONTEND_BASE_URL must use https://");
        }
    }
}

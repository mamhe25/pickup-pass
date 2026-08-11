package com.pickuppass.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSafetyValidatorTest {

    private static final String QR_SECRET = "A9fK3mR8vQ2xT7zP4nL6sD1cH5jW0bYu";
    private static final String BOOTSTRAP_SECRET = "B8gL2nS7wR4xQ9mT1pV6cD3hK5jZ0aXe";
    private static final String SECURITY_SECRET = "S7vQ4mR9xK2pT6nL1cD8hJ5wB3zF0aYe";

    @Test
    void developmentAllowsLocalDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                "change-this-to-a-long-random-value-in-production", "", false,
                "http://localhost:5500", "http://localhost:5500,http://localhost:5173",
                "", false, "");
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void productionRejectsDefaultQrSecret() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                "change-this-to-a-long-random-value-in-production", "", false,
                "https://app.pickuppass.ph", "https://app.pickuppass.ph",
                SECURITY_SECRET, false, "");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionRejectsWildcardCors() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, "", false, "https://app.pickuppass.ph", "*",
                SECURITY_SECRET, false, "");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionRejectsLocalhostCors() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, "", false, "https://app.pickuppass.ph", "http://localhost:5173",
                SECURITY_SECRET, false, "");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionRejectsWeakSecurityFingerprintSecret() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, "", false, "https://app.pickuppass.ph", "https://app.pickuppass.ph",
                "too-short", false, "");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionAllowsBootstrapDisabledWithoutSecret() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, "", false, "https://app.pickuppass.ph", "https://app.pickuppass.ph",
                SECURITY_SECRET, false, "");
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void productionRejectsEnabledBootstrapWithoutStrongSecret() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, "too-short", true, "https://app.pickuppass.ph", "https://app.pickuppass.ph",
                SECURITY_SECRET, false, "");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionAcceptsEnabledBootstrapWithStrongSecret() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, BOOTSTRAP_SECRET, true, "https://app.pickuppass.ph", "https://app.pickuppass.ph",
                SECURITY_SECRET, false, "");
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void productionAcceptsStrongConfiguration() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, "", false, "https://app.pickuppass.ph",
                "https://app.pickuppass.ph,https://admin.pickuppass.ph",
                SECURITY_SECRET, false, "");
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void productionRejectsEnabledDisasterRecoveryWithoutProjectId() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, "", false, "https://app.pickuppass.ph", "https://app.pickuppass.ph",
                SECURITY_SECRET, true, "");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionAcceptsEnabledDisasterRecoveryWithProjectId() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, "", false, "https://app.pickuppass.ph", "https://app.pickuppass.ph",
                SECURITY_SECRET, true, "pickuppass-prod");
        assertDoesNotThrow(validator::validate);
    }

    private MockEnvironment productionEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }
}

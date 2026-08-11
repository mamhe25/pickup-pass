package com.pickuppass.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSafetyValidatorTest {

    private static final String QR_SECRET = "A9fK3mR8vQ2xT7zP4nL6sD1cH5jW0bYu";
    private static final String BOOTSTRAP_SECRET = "B8gL2nS7wR4xQ9mT1pV6cD3hK5jZ0aXe";

    @Test
    void developmentAllowsLocalDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                "change-this-to-a-long-random-value-in-production", "", "http://localhost:5500",
                "http://localhost:5500,http://localhost:5173");
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void productionRejectsDefaultQrSecret() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                "change-this-to-a-long-random-value-in-production",
                BOOTSTRAP_SECRET, "https://app.pickuppass.ph", "https://app.pickuppass.ph");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionRejectsWildcardCors() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, BOOTSTRAP_SECRET, "https://app.pickuppass.ph", "*");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionRejectsLocalhostCors() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, BOOTSTRAP_SECRET, "https://app.pickuppass.ph", "http://localhost:5173");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionAcceptsStrongConfiguration() {
        MockEnvironment env = productionEnvironment();
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                QR_SECRET, BOOTSTRAP_SECRET,
                "https://app.pickuppass.ph",
                "https://app.pickuppass.ph,https://admin.pickuppass.ph");
        assertDoesNotThrow(validator::validate);
    }

    private MockEnvironment productionEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        return env;
    }
}

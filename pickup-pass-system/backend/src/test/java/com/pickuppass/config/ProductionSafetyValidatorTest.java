package com.pickuppass.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSafetyValidatorTest {

    @Test
    void developmentAllowsLocalDefaults() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                "change-this-to-a-long-random-value-in-production", "", "http://localhost:5500");
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void productionRejectsDefaultQrSecret() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                "change-this-to-a-long-random-value-in-production",
                "01234567890123456789012345678901", "https://app.pickuppass.ph");
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    void productionAcceptsStrongConfiguration() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        ProductionSafetyValidator validator = new ProductionSafetyValidator(env,
                "A9fK3mR8vQ2xT7zP4nL6sD1cH5jW0bYu",
                "B8gL2nS7wR4xQ9mT1pV6cD3hK5jZ0aXe",
                "https://app.pickuppass.ph");
        assertDoesNotThrow(validator::validate);
    }
}

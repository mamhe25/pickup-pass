package com.pickuppass.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionFeatureServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void catalogProvidesSafePlanDefaults() {
        SubscriptionFeatureService service = new SubscriptionFeatureService(null);
        Map<String, Object> catalog = service.getCatalog();

        assertTrue(catalog.containsKey("trial"));
        assertTrue(catalog.containsKey("starter"));
        assertTrue(catalog.containsKey("school"));
        assertTrue(catalog.containsKey("enterprise"));

        Map<String, Object> starter = (Map<String, Object>) catalog.get("starter");
        Map<String, Boolean> starterFeatures = (Map<String, Boolean>) starter.get("features");
        assertFalse(starterFeatures.get("advanced_reporting"));
        assertFalse(starterFeatures.get("scheduled_announcements"));
        assertTrue(starterFeatures.get("manual_override"));

        Map<String, Object> enterprise = (Map<String, Object>) catalog.get("enterprise");
        Map<String, Boolean> enterpriseFeatures = (Map<String, Boolean>) enterprise.get("features");
        assertTrue(SubscriptionFeatureService.FEATURES.stream().allMatch(k -> Boolean.TRUE.equals(enterpriseFeatures.get(k))));
    }

    @Test
    void unknownOrBlankPlanFallsBackToTrial() {
        SubscriptionFeatureService service = new SubscriptionFeatureService(null);
        assertEquals("trial", service.normalizePlan(null));
        assertEquals("trial", service.normalizePlan(""));
        assertEquals("trial", service.normalizePlan("not-a-plan"));
        assertEquals("school", service.normalizePlan(" SCHOOL "));
    }

    @Test
    void subscriptionStateBlocksOnlyOptionalFeatureAccessAfterGrace() {
        Instant now = Instant.parse("2026-08-11T00:00:00Z");
        assertTrue(SubscriptionFeatureService.subscriptionAccessActive("active", null, null, now));
        assertTrue(SubscriptionFeatureService.subscriptionAccessActive(
                "past_due", null, now.plusSeconds(60), now));
        assertFalse(SubscriptionFeatureService.subscriptionAccessActive(
                "past_due", null, now.minusSeconds(1), now));
        assertFalse(SubscriptionFeatureService.subscriptionAccessActive("cancelled", null, null, now));
    }
}

package com.pickuppass.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SubscriptionLifecycleServiceTest {
    private final Instant now = Instant.parse("2026-08-11T00:00:00Z");

    @Test
    void expiredTrialMovesToPastDueWithGrace() {
        var d = SubscriptionLifecycleService.decide(
                "trialing", now.minusSeconds(60), null, null,
                true, false, now);
        assertEquals("subscription.trial_expired", d.action());
        assertEquals("past_due", d.newStatus());
        assertTrue(d.update().containsKey("graceEndsAt"));
    }

    @Test
    void expiredGraceCancelsOptionalSubscriptionAccess() {
        var d = SubscriptionLifecycleService.decide(
                "past_due", null, null, now.minusSeconds(1),
                true, false, now);
        assertEquals("subscription.grace_expired", d.action());
        assertEquals("cancelled", d.newStatus());
    }

    @Test
    void activeSubscriptionAutoRenewsWhenEnabled() {
        var d = SubscriptionLifecycleService.decide(
                "active", null, now.minusSeconds(1), null,
                true, false, now);
        assertEquals("subscription.auto_renewed", d.action());
        assertEquals("active", d.newStatus());
        assertTrue(d.update().containsKey("currentPeriodEnd"));
    }

    @Test
    void cancelAtPeriodEndWinsOverAutoRenew() {
        var d = SubscriptionLifecycleService.decide(
                "active", null, now.minusSeconds(1), null,
                true, true, now);
        assertEquals("subscription.period_ended", d.action());
        assertEquals("cancelled", d.newStatus());
    }
}

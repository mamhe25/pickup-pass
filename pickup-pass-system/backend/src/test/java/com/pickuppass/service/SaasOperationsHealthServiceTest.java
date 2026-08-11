package com.pickuppass.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaasOperationsHealthServiceTest {
    @Test
    void suspendedTenantAlwaysWins() {
        assertEquals(SaasOperationsHealthService.SUSPENDED,
                SaasOperationsHealthService.classifyHealth("suspended", List.of(SaasOperationsHealthService.OVER_QUOTA)));
    }

    @Test
    void highestActiveImpactWins() {
        assertEquals(SaasOperationsHealthService.OVER_QUOTA,
                SaasOperationsHealthService.classifyHealth("active", List.of(
                        SaasOperationsHealthService.ATTENTION,
                        SaasOperationsHealthService.BILLING_RISK,
                        SaasOperationsHealthService.OVER_QUOTA)));
    }

    @Test
    void quotaPercentageIsStable() {
        assertEquals(90, SaasOperationsHealthService.quotaPercent(90, 100));
        assertEquals(0, SaasOperationsHealthService.quotaPercent(50, -1));
    }
}

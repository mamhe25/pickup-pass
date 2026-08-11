package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import com.pickuppass.service.SubscriptionFeatureService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant")
public class TenantEntitlementController {

    private final SubscriptionFeatureService subscriptionFeatureService;

    public TenantEntitlementController(SubscriptionFeatureService subscriptionFeatureService) {
        this.subscriptionFeatureService = subscriptionFeatureService;
    }

    @GetMapping("/entitlements")
    @PreAuthorize("isAuthenticated() and !hasRole('master_admin')")
    public ResponseEntity<?> entitlements(@AuthenticationPrincipal FirebaseUserDetails user) throws Exception {
        return ResponseEntity.ok(subscriptionFeatureService.effectiveEntitlements(user.getSchoolId()));
    }
}

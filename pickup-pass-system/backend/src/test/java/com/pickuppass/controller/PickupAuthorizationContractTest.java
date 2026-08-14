package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PickupAuthorizationContractTest {

    @Test
    void scannerEndpointsRemainRestrictedToSchoolStaff() throws Exception {
        assertRule("activePickupGates", "hasAnyRole('teacher','school_admin')", FirebaseUserDetails.class);
        assertRule("verify", "hasAnyRole('teacher','school_admin')",
                PickupController.VerifyRequest.class, FirebaseUserDetails.class);
        assertRule("approve", "hasAnyRole('teacher','school_admin')",
                PickupController.VerifyRequest.class, String.class, String.class, FirebaseUserDetails.class);
    }

    @Test
    void manualOverrideRemainsRestrictedToSchoolAdministrators() throws Exception {
        assertRule("manualOverride", "hasRole('school_admin')",
                PickupController.ManualOverrideRequest.class, String.class, String.class, FirebaseUserDetails.class);
    }

    private void assertRule(String methodName, String expectedRule, Class<?>... parameterTypes) throws Exception {
        PreAuthorize annotation = PickupController.class
                .getDeclaredMethod(methodName, parameterTypes)
                .getAnnotation(PreAuthorize.class);
        assertNotNull(annotation, methodName + " must keep an explicit authorization rule");
        assertEquals(expectedRule, annotation.value());
    }
}

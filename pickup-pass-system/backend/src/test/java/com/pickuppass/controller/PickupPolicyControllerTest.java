package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PickupPolicyControllerTest {

    private final FirebaseUserDetails admin =
            new FirebaseUserDetails("admin1", "admin@test.com", "school1", "school_admin");

    @Test
    void rejectsUnknownPickupModeBeforeWritingAnything() throws Exception {
        PickupPolicyController controller = new PickupPolicyController(null, null, "Asia/Manila");
        PickupPolicyController.UpdatePickupPolicyRequest request = new PickupPolicyController.UpdatePickupPolicyRequest();
        request.setMode("queue_required");

        var response = controller.updatePolicy(request, admin);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void rejectsInvalidTimeWindowFormatBeforeWritingAnything() throws Exception {
        PickupPolicyController controller = new PickupPolicyController(null, null, "Asia/Manila");
        PickupPolicyController.UpdatePickupPolicyRequest request = new PickupPolicyController.UpdatePickupPolicyRequest();
        request.setMode("time_window");
        request.setEarliestPickupTime("2 PM");
        request.setLatestPickupTime("18:00");

        var response = controller.updatePolicy(request, admin);

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    void rejectsWindowWhoseEndIsNotAfterStart() throws Exception {
        PickupPolicyController controller = new PickupPolicyController(null, null, "Asia/Manila");
        PickupPolicyController.UpdatePickupPolicyRequest request = new PickupPolicyController.UpdatePickupPolicyRequest();
        request.setMode("time_window");
        request.setEarliestPickupTime("18:00");
        request.setLatestPickupTime("14:00");

        var response = controller.updatePolicy(request, admin);

        assertEquals(400, response.getStatusCode().value());
    }
}

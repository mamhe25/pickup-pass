package com.pickuppass.controller;

import com.pickuppass.security.FirebaseUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionControllerTest {

    @Test
    void meReturnsAuthenticatedIdentityAndTenant() {
        SessionController controller = new SessionController();
        FirebaseUserDetails user = new FirebaseUserDetails(
                "teacher-1", "teacher@example.com", "school-1", "teacher"
        );

        ResponseEntity<?> response = controller.me(user);

        assertEquals(200, response.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertEquals("teacher-1", body.get("uid"));
        assertEquals("teacher", body.get("role"));
        assertEquals("school-1", body.get("schoolId"));
        assertEquals("active", body.get("status"));
    }
}

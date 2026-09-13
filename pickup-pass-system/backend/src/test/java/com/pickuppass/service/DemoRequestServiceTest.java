package com.pickuppass.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DemoRequestServiceTest {

    @Test
    void normalizesLeadEmail() {
        assertEquals(
                "school.admin@example.edu",
                DemoRequestService.normalizeEmail(" School.Admin@Example.EDU "));
    }

    @Test
    void normalizesHumanReadableStatus() {
        assertEquals(
                "demo_scheduled",
                DemoRequestService.normalizeStatus("Demo Scheduled"));
    }

    @Test
    void rejectsUnsupportedStatus() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> DemoRequestService.normalizeStatus("ignored"));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }
}

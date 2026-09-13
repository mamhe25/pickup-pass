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

    @Test
    void classifiesEducationGovernmentFreeAndCustomDomains() {
        assertEquals(
                "education",
                DemoRequestService.assessEmailDomain("principal@school.edu.ph").type());
        assertEquals(
                "government",
                DemoRequestService.assessEmailDomain("staff@deped.gov.ph").type());
        assertEquals(
                "government",
                DemoRequestService.assessEmailDomain("office@city.gov.ph").type());
        assertEquals(
                "free",
                DemoRequestService.assessEmailDomain("schooloffice@gmail.com").type());
        assertEquals(
                "custom",
                DemoRequestService.assessEmailDomain("admin@sanroqueacademy.org").type());
    }

    @Test
    void doesNotRequireEduEmail() {
        assertEquals(
                "Verified custom-domain email",
                DemoRequestService.trustLabel(
                        DemoRequestService.assessEmailDomain("admin@myacademy.ph").type()));
        assertEquals(
                "Verified free email · review organization",
                DemoRequestService.trustLabel(
                        DemoRequestService.assessEmailDomain("registrar@gmail.com").type()));
    }

    @Test
    void rejectsKnownDisposableEmailDomains() {
        assertThrows(
                ResponseStatusException.class,
                () -> DemoRequestService.assessEmailDomain("fake@mailinator.com"));
        assertThrows(
                ResponseStatusException.class,
                () -> DemoRequestService.assessEmailDomain("fake@yopmail.com"));
    }
}

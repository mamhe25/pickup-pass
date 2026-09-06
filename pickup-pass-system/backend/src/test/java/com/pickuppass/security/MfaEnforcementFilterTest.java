package com.pickuppass.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MfaEnforcementFilterTest {

    private final MfaEnforcementFilter filter =
            new MfaEnforcementFilter(new ObjectMapper());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void schoolAdminWithoutMfaIsBlocked() throws Exception {
        authenticate("school_admin", false);

        MockHttpServletResponse response =
                execute("/api/school-admin/staff");

        assertEquals(
                MfaEnforcementFilter.MFA_REQUIRED_STATUS,
                response.getStatus());
        assertTrue(
                response.getContentAsString()
                        .contains("\"code\":\"mfa_required\""));
    }

    @Test
    void masterAdminWithoutMfaIsBlocked() throws Exception {
        authenticate("master_admin", false);

        MockHttpServletResponse response =
                execute("/api/master-admin/overview");

        assertEquals(
                MfaEnforcementFilter.MFA_REQUIRED_STATUS,
                response.getStatus());
    }

    @Test
    void protectedRoleWithMfaIsAllowed() throws Exception {
        authenticate("school_admin", true);

        MockHttpServletResponse response =
                execute("/api/school-admin/staff");

        assertEquals(204, response.getStatus());
    }

    @Test
    void teacherDoesNotRequireMfa() throws Exception {
        authenticate("teacher", false);

        MockHttpServletResponse response =
                execute("/api/teacher/students");

        assertEquals(204, response.getStatus());
    }

    @Test
    void sessionMeRemainsAvailableForMfaBootstrap() throws Exception {
        authenticate("master_admin", false);

        MockHttpServletResponse response =
                execute("/api/session/me");

        assertEquals(204, response.getStatus());
    }

    private void authenticate(
            String role,
            boolean mfaSatisfied) {

        FirebaseUserDetails principal =
                new FirebaseUserDetails(
                        "uid-1",
                        "admin@example.com",
                        "school-a",
                        role,
                        mfaSatisfied);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_" + role)));

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    private MockHttpServletResponse execute(
            String uri) throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "GET",
                        uri);
        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain =
                (servletRequest, servletResponse) ->
                        ((MockHttpServletResponse) servletResponse)
                                .setStatus(204);

        filter.doFilter(
                request,
                response,
                chain);

        return response;
    }
}

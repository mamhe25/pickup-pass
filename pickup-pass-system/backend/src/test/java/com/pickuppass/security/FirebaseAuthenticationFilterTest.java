package com.pickuppass.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.pickuppass.service.SecurityEventService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseAuthenticationFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatedStaffReceivesClaimBoundTenantAndRole() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        SecurityEventService securityEvents = mock(SecurityEventService.class);
        FirebaseToken token = mock(FirebaseToken.class);
        when(firebaseAuth.verifyIdToken("valid-token", true)).thenReturn(token);
        when(token.getUid()).thenReturn("teacher-1");
        when(token.getEmail()).thenReturn("teacher@example.com");
        when(token.getClaims()).thenReturn(Map.of("schoolId", "school-1", "role", "teacher"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new FirebaseAuthenticationFilter(firebaseAuth, securityEvents).doFilterInternal(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        FirebaseUserDetails principal = (FirebaseUserDetails) authentication.getPrincipal();
        assertEquals("teacher-1", principal.getUid());
        assertEquals("school-1", principal.getSchoolId());
        assertEquals("teacher", principal.getRole());
        assertEquals("ROLE_teacher", authentication.getAuthorities().iterator().next().getAuthority());
        verify(firebaseAuth).verifyIdToken("valid-token", true);
        verify(chain).doFilter(request, response);
    }

    @Test
    void revokedOrDisabledTokenNeverCreatesAuthenticatedContext() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        SecurityEventService securityEvents = mock(SecurityEventService.class);
        when(firebaseAuth.verifyIdToken("revoked-token", true))
                .thenThrow(new IllegalStateException("Token is revoked or user is disabled"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new FirebaseAuthenticationFilter(firebaseAuth, securityEvents).doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertSame(Boolean.TRUE, request.getAttribute("firebaseTokenRejected"));
        verify(firebaseAuth).verifyIdToken("revoked-token", true);
        verify(securityEvents).recordInvalidToken(same(request), eq("IllegalStateException"));
        verify(chain).doFilter(request, response);
    }
}

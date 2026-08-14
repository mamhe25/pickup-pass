package com.pickuppass.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickuppass.service.DeviceSessionService;
import com.pickuppass.service.SecurityEventService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceSessionFilterTest {

    private static final String DEVICE_ID = "android-device-1234567890";
    private DeviceSessionService sessions;
    private SecurityEventService securityEvents;
    private DeviceSessionFilter filter;

    @BeforeEach
    void setUp() {
        sessions = mock(DeviceSessionService.class);
        securityEvents = mock(SecurityEventService.class);
        filter = new DeviceSessionFilter(sessions, new ObjectMapper(), securityEvents);

        FirebaseUserDetails user = new FirebaseUserDetails(
                "teacher-1", "teacher@example.com", "school-1", "teacher");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_teacher"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedDeviceIsRejectedBeforeControllerExecution() throws Exception {
        when(sessions.validateAndTouch("teacher-1", "school-1", "teacher", DEVICE_ID, "Pixel", "1.0"))
                .thenReturn(DeviceSessionService.ValidationResult.revokedResult());
        MockHttpServletRequest request = requestWithDevice(DEVICE_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("DEVICE_SESSION_REVOKED"));
        verify(securityEvents).recordRevokedDeviceAttempt(
                any(FirebaseUserDetails.class), same(request), eq(DEVICE_ID));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void malformedDeviceIdentifierIsRejected() throws Exception {
        when(sessions.validateAndTouch("teacher-1", "school-1", "teacher", "short", "Pixel", "1.0"))
                .thenThrow(new IllegalArgumentException("Invalid device id"));
        MockHttpServletRequest request = requestWithDevice("short");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(response.getContentAsString().contains("INVALID_DEVICE_ID"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void validationOutageFailsClosedWhenDeviceIdIsPresent() throws Exception {
        when(sessions.validateAndTouch("teacher-1", "school-1", "teacher", DEVICE_ID, "Pixel", "1.0"))
                .thenThrow(new IllegalStateException("Firestore unavailable"));
        MockHttpServletRequest request = requestWithDevice(DEVICE_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("DEVICE_SESSION_UNAVAILABLE"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void clientsWithoutDeviceIdentityRetainLegacyAccessDuringValidationOutage() throws Exception {
        when(sessions.validateAndTouch("teacher-1", "school-1", "teacher", null, null, null))
                .thenThrow(new IllegalStateException("Firestore unavailable"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest requestWithDevice(String deviceId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DeviceSessionFilter.DEVICE_ID, deviceId);
        request.addHeader(DeviceSessionFilter.DEVICE_NAME, "Pixel");
        request.addHeader("X-Client-Version", "1.0");
        return request;
    }
}

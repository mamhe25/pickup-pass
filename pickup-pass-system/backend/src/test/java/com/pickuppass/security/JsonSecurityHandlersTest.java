package com.pickuppass.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickuppass.service.SecurityEventService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JsonSecurityHandlersTest {

    @Test
    void authenticationEntryPointReturnsStable401Payload() throws Exception {
        JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("requestId", "req-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(request, response, new BadCredentialsException("bad token"));

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("SESSION_INVALID"));
        assertTrue(response.getContentAsString().contains("req-123"));
    }

    @Test
    void accessDeniedHandlerReturnsStable403Payload() throws Exception {
        JsonAccessDeniedHandler handler = new JsonAccessDeniedHandler(new ObjectMapper(), mock(SecurityEventService.class));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("requestId", "req-456");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("forbidden"));

        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("ACCESS_DENIED"));
        assertTrue(response.getContentAsString().contains("req-456"));
    }
}



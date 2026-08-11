package com.pickuppass.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickuppass.service.DeviceSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Rejects individually revoked app installations after Firebase authentication. */
public class DeviceSessionFilter extends OncePerRequestFilter {
    public static final String DEVICE_ID = "X-Device-Id";
    public static final String DEVICE_NAME = "X-Device-Name";
    private final DeviceSessionService sessions;
    private final ObjectMapper objectMapper;

    public DeviceSessionFilter(DeviceSessionService sessions, ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof FirebaseUserDetails user) {
            String deviceId = request.getHeader(DEVICE_ID);
            try {
                var result = sessions.validateAndTouch(user.getUid(), user.getSchoolId(), user.getRole(),
                        deviceId, request.getHeader(DEVICE_NAME), request.getHeader("X-Client-Version"));
                if (result.revoked()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    Map<String,Object> body = new LinkedHashMap<>();
                    body.put("error", "This device session has been signed out");
                    body.put("code", "DEVICE_SESSION_REVOKED");
                    Object requestId = request.getAttribute("requestId");
                    if (requestId != null) body.put("requestId", requestId.toString());
                    objectMapper.writeValue(response.getOutputStream(), body);
                    return;
                }
            } catch (IllegalArgumentException badId) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getOutputStream(), Map.of(
                        "error", "Invalid device identifier", "code", "INVALID_DEVICE_ID"));
                return;
            } catch (Exception e) {
                // Fail closed for an authenticated app request carrying a device id.
                if (deviceId != null && !deviceId.isBlank()) {
                    response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(), Map.of(
                            "error", "Could not validate device session", "code", "DEVICE_SESSION_UNAVAILABLE"));
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}

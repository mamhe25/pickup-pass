package com.pickuppass.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickuppass.service.SecurityEventService;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;
    private final SecurityEventService securityEvents;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper, SecurityEventService securityEvents) {
        this.objectMapper = objectMapper;
        this.securityEvents = securityEvents;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof FirebaseUserDetails user) {
            securityEvents.recordAccessDenied(user, request);
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "You do not have permission to perform this action");
        body.put("code", "ACCESS_DENIED");
        Object requestId = request.getAttribute("requestId");
        if (requestId != null) body.put("requestId", requestId.toString());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

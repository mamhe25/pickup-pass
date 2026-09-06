package com.pickuppass.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Server-side mandatory MFA policy for privileged PickupPass roles.
 *
 * Android/web UI enforcement is only user experience. This filter is the
 * authoritative API boundary: a valid password-only Firebase token belonging
 * to school_admin or master_admin cannot use protected PickupPass APIs.
 *
 * /api/session/me remains available as a minimal identity/bootstrap endpoint
 * so a just-authenticated admin can discover that MFA setup is required.
 */
public class MfaEnforcementFilter extends OncePerRequestFilter {

    public static final int MFA_REQUIRED_STATUS = 428;

    private final ObjectMapper objectMapper;

    public MfaEnforcementFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {
        return "/api/session/me".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication != null
                && authentication.getPrincipal()
                    instanceof FirebaseUserDetails principal
                && principal.requiresMfa()
                && !principal.isMfaSatisfied()) {

            response.setStatus(MFA_REQUIRED_STATUS);
            response.setContentType(
                    MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Map<String, Object> body = new LinkedHashMap<>();
            body.put(
                    "error",
                    "Two-factor authentication is required for this role");
            body.put("code", "mfa_required");
            body.put("mfaRequired", true);

            objectMapper.writeValue(
                    response.getWriter(),
                    body);
            return;
        }

        filterChain.doFilter(request, response);
    }
}

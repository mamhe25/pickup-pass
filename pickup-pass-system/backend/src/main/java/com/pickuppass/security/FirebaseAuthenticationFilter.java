package com.pickuppass.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.pickuppass.service.SecurityEventService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Verifies the "Authorization: Bearer <Firebase ID token>" header on every
 * request via the Admin SDK, then builds a FirebaseUserDetails principal
 * carrying schoolId + role from the token's custom claims. This is what lets
 * @PreAuthorize("hasRole('teacher')") and manual schoolId checks work
 * downstream in controllers.
 */
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private final FirebaseAuth firebaseAuth;
    private final SecurityEventService securityEvents;

    public FirebaseAuthenticationFilter(FirebaseAuth firebaseAuth, SecurityEventService securityEvents) {
        this.firebaseAuth = firebaseAuth;
        this.securityEvents = securityEvents;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String idToken = header.substring(7);
            try {
                FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken, true);

                String uid = decoded.getUid();
                String email = decoded.getEmail();
                Object schoolIdClaim = decoded.getClaims().get("schoolId");
                Object roleClaim = decoded.getClaims().get("role");

                String schoolId = schoolIdClaim != null ? schoolIdClaim.toString() : null;
                String role = roleClaim != null ? roleClaim.toString() : "unknown";

                FirebaseUserDetails principal = new FirebaseUserDetails(uid, email, schoolId, role);

                List<GrantedAuthority> authorities =
                        List.of(new SimpleGrantedAuthority("ROLE_" + role));

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authToken);

            } catch (Exception e) {
                // Invalid/expired token: leave context unauthenticated. Do not store
                // the token or raw IP; security telemetry keeps only a keyed fingerprint.
                request.setAttribute("firebaseTokenRejected", Boolean.TRUE);
                securityEvents.recordInvalidToken(request, e.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}

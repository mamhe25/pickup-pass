package com.pickuppass.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight per-instance fixed-window API limiter. It protects the highest-risk
 * endpoints immediately without requiring another service. For multi-instance scale,
 * replace the backing map with Redis/Bucket4j while keeping the same policy keys.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Policy policy = policyFor(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = Instant.now().getEpochSecond();
        long windowStart = now - (now % policy.windowSeconds);
        String key = policy.name + ":" + clientKey(request) + ":" + windowStart;

        Window window = windows.computeIfAbsent(key, k -> new Window(windowStart));
        int count = window.count.incrementAndGet();

        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, policy.limit - count)));

        if (count > policy.limit) {
            long retryAfter = Math.max(1, policy.windowSeconds - (now - windowStart));
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            objectMapper.writeValue(response.getWriter(), Map.of(
                    "error", "Too many requests",
                    "retryAfterSeconds", retryAfter));
            cleanupOldWindows(now);
            return;
        }

        cleanupOldWindows(now);
        filterChain.doFilter(request, response);
    }

    private Policy policyFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && path.equals("/api/parent/generate-token")) return new Policy("qr-generate", 10, 60);
        if ("POST".equals(method) && path.equals("/api/pickup/verify")) return new Policy("qr-verify", 60, 60);
        if ("POST".equals(method) && path.equals("/api/pickup/approve")) return new Policy("pickup-approve", 40, 60);
        if ("POST".equals(method) && path.equals("/api/pickup/manual-override")) return new Policy("manual-override", 10, 60);
        if ("POST".equals(method) && path.contains("/broadcasts")) return new Policy("broadcast", 5, 60);
        if ("POST".equals(method) && path.equals("/api/school-admin/students/import")) return new Policy("student-import", 10, 3600);
        if ("POST".equals(method) && path.startsWith("/api/bootstrap/")) return new Policy("bootstrap", 5, 3600);
        if ("POST".equals(method) && (path.contains("add-guardian") || path.contains("/staff"))) return new Policy("provision", 20, 3600);
        return null;
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private void cleanupOldWindows(long now) {
        if (windows.size() < 1000) return;
        windows.entrySet().removeIf(e -> now - e.getValue().windowStart > 7200);
    }

    private record Policy(String name, int limit, long windowSeconds) {}
    private static final class Window {
        final long windowStart;
        final AtomicInteger count = new AtomicInteger();
        Window(long windowStart) { this.windowStart = windowStart; }
    }
}

package com.pickuppass.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Structured request summary without request bodies, query strings, tokens, or personal data. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - started) / 1_000_000;
            int status = response.getStatus();
            String method = request.getMethod();
            String path = request.getRequestURI();
            if (status >= 500) {
                log.error("http_request method={} path={} status={} durationMs={}", method, path, status, durationMs);
            } else if (status >= 400 || durationMs >= 2000) {
                log.warn("http_request method={} path={} status={} durationMs={}", method, path, status, durationMs);
            } else {
                log.info("http_request method={} path={} status={} durationMs={}", method, path, status, durationMs);
            }
        }
    }
}

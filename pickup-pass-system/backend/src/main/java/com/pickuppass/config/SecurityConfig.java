package com.pickuppass.config;

import com.google.firebase.auth.FirebaseAuth;
import com.pickuppass.security.FirebaseAuthenticationFilter;
import com.pickuppass.security.DeviceSessionFilter;
import com.pickuppass.service.DeviceSessionService;
import com.pickuppass.service.SecurityEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickuppass.security.JsonAccessDeniedHandler;
import com.pickuppass.security.JsonAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public FirebaseAuthenticationFilter firebaseAuthenticationFilter(FirebaseAuth firebaseAuth, SecurityEventService securityEvents) {
        return new FirebaseAuthenticationFilter(firebaseAuth, securityEvents);
    }

    @Bean
    public DeviceSessionFilter deviceSessionFilter(DeviceSessionService sessions, ObjectMapper objectMapper, SecurityEventService securityEvents) {
        return new DeviceSessionFilter(sessions, objectMapper, securityEvents);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                     FirebaseAuthenticationFilter firebaseFilter,
                                                     DeviceSessionFilter deviceSessionFilter,
                                                     JsonAuthenticationEntryPoint authenticationEntryPoint,
                                                     JsonAccessDeniedHandler accessDeniedHandler,
                                                     CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/info").hasRole("master_admin")
                .requestMatchers("/api/bootstrap/**").permitAll()
                .requestMatchers("/api/webhooks/payments/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(firebaseFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(deviceSessionFilter, FirebaseAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5500,http://localhost:5173}") String origins) {
        CorsConfiguration config = new CorsConfiguration();
        List<String> allowedOrigins = Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-ID", "X-Client-Version", "X-Device-Id", "X-Device-Name", "X-Pickup-Gate-Id", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Request-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

package com.evplus.report.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration for EV+ Async Reporting Service.
 *
 * Configures OAuth2 Resource Server for JWT authentication in all environments.
 * Local profile uses symmetric key JWT validation for development convenience.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * JWT Decoder for local development.
     * Uses symmetric key (HMAC-SHA256) to decode and validate JWT tokens.
     * The same key is used by JwtTokenGeneratorController to generate tokens.
     */
    @Bean
    @Profile("local")
    public JwtDecoder localJwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(LocalJwtSecretKey.getSecretKey()).build();
    }

    /**
     * Security configuration for local development.
     * Requires JWT authentication via Bearer token.
     * Public endpoints:
     * - /dev/jwt/** - JWT token generator (so you can get tokens without auth)
     * - Actuator health endpoints
     * - Swagger/OpenAPI endpoints
     */
    @Bean
    @Profile("local")
    public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no authentication required
                .requestMatchers(
                    "/dev/jwt/**",           // JWT token generator
                    "/actuator/health/**",
                    "/actuator/info",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(localJwtDecoder())
                    .jwtAuthenticationConverter(new CustomJwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Security configuration for dev, stage, and prod environments.
     * Configures OAuth2 Resource Server with JWT authentication.
     *
     * Public endpoints (no authentication required):
     * - Actuator health endpoints
     * - Swagger/OpenAPI endpoints
     *
     * Protected endpoints (authentication required):
     * - All /reports/** endpoints
     * - All /admin/** endpoints
     */
    @Bean
    @Profile({"dev", "stage", "prod"})
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints - no authentication required
                .requestMatchers(
                    "/actuator/health/**",
                    "/actuator/info",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/v3/api-docs/**"
                ).permitAll()

                // Admin endpoints - require ADMIN role
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(new CustomJwtAuthenticationConverter())
                )
            );

        return http.build();
    }
}

package com.evplus.report.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
//import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
//import org.springframework.security.oauth2.jwt.JwtDecoder;
//import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Value("${cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS,PATCH}")
    private String allowedMethods;

    @Value("${cors.allowed-headers:*}")
    private String allowedHeaders;

    @Value("${cors.exposed-headers:Content-Disposition,X-Correlation-Id}")
    private String exposedHeaders;

    @Value("${cors.allow-credentials:true}")
    private boolean allowCredentials;

    @Value("${cors.max-age:3600}")
    private long maxAge;

    /**
     * CORS Configuration Source.
     * Configures Cross-Origin Resource Sharing to allow browser-based clients
     * from different domains to access the API.
     *
     * Configuration is driven by application.yml properties:
     * - cors.allowed-origins: Comma-separated list of allowed origins (default: *)
     * - cors.allowed-methods: Allowed HTTP methods (default: GET,POST,PUT,DELETE,OPTIONS,PATCH)
     * - cors.allowed-headers: Allowed request headers (default: *)
     * - cors.exposed-headers: Headers exposed to the client (default: Content-Disposition,X-Correlation-Id)
     * - cors.allow-credentials: Allow credentials (default: true)
     * - cors.max-age: Cache duration for preflight requests in seconds (default: 3600)
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Parse and set allowed origins
        configuration.setAllowedOrigins(parseCommaSeparatedValues(allowedOrigins));

        // Parse and set allowed methods
        configuration.setAllowedMethods(parseCommaSeparatedValues(allowedMethods));

        // Parse and set allowed headers
        configuration.setAllowedHeaders(parseCommaSeparatedValues(allowedHeaders));

        // Parse and set exposed headers
        configuration.setExposedHeaders(parseCommaSeparatedValues(exposedHeaders));

        // Set credentials and max age
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    /**
     * Parse comma-separated values and trim whitespace.
     */
    private List<String> parseCommaSeparatedValues(String value) {
        if (value == null || value.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    /**
     * JWT Decoder for local development.
     * Uses symmetric key (HMAC-SHA256) to decode and validate JWT tokens.
     * The same key is used by JwtTokenGeneratorController to generate tokens.
     * 
     * THIS METHOD IS ONLY FOR LOCAL DEVELOPMENT AND SHOULD NOT BE USED IN PRODUCTION. 
     * THAT IS WHY IT IS COMMENTED OUT.
     * 
     * 
     */
    /**@Bean
    @Profile("local")
    public JwtDecoder localJwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(LocalJwtSecretKey.getSecretKey()).build();
    }**/

    /**
     * Security configuration for local development.
     * Requires JWT authentication via Bearer token.
     * Public endpoints:
     * - /dev/jwt/** - JWT token generator (so you can get tokens without auth)
     * - Actuator health endpoints
     * - Swagger/OpenAPI endpoints
     */
    /**@Bean
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
    }**/

    /**
     * Security configuration for stage and prod environments.
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
    //@Profile({"dev","stage", "prod", "local"})
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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

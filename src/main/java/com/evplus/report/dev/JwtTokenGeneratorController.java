package com.evplus.report.dev;

import com.evplus.report.security.LocalJwtSecretKey;
import io.jsonwebtoken.Jwts;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * JWT Token Generator for Local Development ONLY.
 *
 * WARNING: This endpoint is ONLY available in local profile.
 * DO NOT use in production environments.
 *
 * Generates JWT tokens with custom claims for testing authentication.
 */
@RestController
@RequestMapping("/dev/jwt")
@Profile("local")
@Slf4j
@Tag(name = "Development Tools", description = "Local development utilities (NOT available in production)")
public class JwtTokenGeneratorController {

    // Shared secret key for signing JWTs (same key used by SecurityConfig for validation)
    private static final SecretKey SECRET_KEY = LocalJwtSecretKey.getSecretKey();
    private static final String ISSUER = "ev-plus-local-dev";

    /**
     * Generate a JWT token with custom claims.
     *
     * @param request token generation request with user details
     * @return JWT token response
     */
    @PostMapping("/generate")
    @Operation(
        summary = "Generate JWT token for testing",
        description = "Generates a JWT token with custom userId, districtId, and roles for local testing. " +
                     "Token is valid for 24 hours. **LOCAL DEVELOPMENT ONLY**"
    )
    public ResponseEntity<TokenResponse> generateToken(@RequestBody TokenRequest request) {
        log.info("Generating JWT token for userId={}, districtId={}",
            request.getUserId(), request.getDistrictId());

        Instant now = Instant.now();
        Instant expiration = now.plus(24, ChronoUnit.HOURS);

        String token = Jwts.builder()
                .subject(request.getUsername())
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim("userId", request.getUserId())
                .claim("districtId", request.getDistrictId())
                .claim("email", request.getEmail())
                .claim("given_name", request.getFirstName())
                .claim("family_name", request.getLastName())
                .claim("roles", request.getRoles())
                .signWith(SECRET_KEY)
                .compact();

        TokenResponse response = new TokenResponse();
        response.setToken(token);
        response.setTokenType("Bearer");
        response.setExpiresIn(24 * 3600); // 24 hours in seconds
        response.setIssuedAt(now.toString());
        response.setExpiresAt(expiration.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Generate a quick test token with default values.
     * Useful for quick testing without providing full request body.
     *
     * @param userId optional user ID (default: 123)
     * @param districtId optional district ID (default: 456)
     * @return JWT token response
     */
    @GetMapping("/quick")
    @Operation(
        summary = "Generate quick test token with defaults",
        description = "Generates a JWT token with default test values. " +
                     "Useful for quick testing without crafting full request body."
    )
    public ResponseEntity<TokenResponse> generateQuickToken(
            @Parameter(description = "User ID", example = "123")
            @RequestParam(defaultValue = "123") Integer userId,
            @Parameter(description = "District ID", example = "456")
            @RequestParam(defaultValue = "456") Integer districtId
    ) {
        TokenRequest request = new TokenRequest();
        request.setUserId(userId);
        request.setDistrictId(districtId);
        request.setUsername("testuser");
        request.setEmail("test@evplus.com");
        request.setFirstName("Test");
        request.setLastName("User");
        request.setRoles(Arrays.asList("USER"));

        return generateToken(request);
    }

    /**
     * Decode and display JWT token claims (for debugging).
     *
     * @param token JWT token to decode
     * @return decoded claims
     */
    @GetMapping("/decode")
    @Operation(
        summary = "Decode JWT token",
        description = "Decodes a JWT token and displays all claims. Useful for debugging."
    )
    public ResponseEntity<Map<String, Object>> decodeToken(
            @Parameter(description = "JWT token to decode")
            @RequestParam String token
    ) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(SECRET_KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Map<String, Object> response = new HashMap<>();
            response.put("subject", claims.getSubject());
            response.put("issuer", claims.getIssuer());
            response.put("issuedAt", claims.getIssuedAt());
            response.put("expiration", claims.getExpiration());
            response.put("userId", claims.get("userId"));
            response.put("districtId", claims.get("districtId"));
            response.put("email", claims.get("email"));
            response.put("given_name", claims.get("given_name"));
            response.put("family_name", claims.get("family_name"));
            response.put("roles", claims.get("roles"));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to decode token", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid token: " + e.getMessage()));
        }
    }

    @Data
    public static class TokenRequest {
        private Integer userId;
        private Integer districtId;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private List<String> roles = Arrays.asList("USER");
    }

    @Data
    public static class TokenResponse {
        private String token;
        private String tokenType;
        private Integer expiresIn;
        private String issuedAt;
        private String expiresAt;
    }
}

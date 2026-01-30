package com.evplus.report.service;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Token Service for Download Tokens using RSA Asymmetric Keys.
 *
 * This service generates and validates JWT tokens used in download redirect URLs.
 * Using JWT provides several security benefits:
 * - Self-contained expiration validation (no DB lookup needed for expiration check)
 * - Cryptographically signed with RSA (more secure than HMAC for distributed systems)
 * - Can include claims for additional validation
 * - Standard security approach
 *
 * Security Architecture:
 * - Private key (stored in AWS Secrets Manager) is used to SIGN tokens
 * - Public key (stored in AWS Secrets Manager or config) is used to VERIFY tokens
 * - This allows verification without exposing the signing key
 *
 * JWT Claims:
 * - jti (JWT ID): Unique token identifier (used as database token ID)
 * - sub (Subject): Report job ID
 * - userId: User who requested the report
 * - districtId: District ID for the report
 * - iat (Issued At): Token creation time
 * - exp (Expiration): Token expiration time
 *
 * Keys Storage:
 * - Dev/Stage/Prod: Keys loaded from AWS Secrets Manager via Spring Cloud AWS
 * - Local: Keys loaded from application-local.yml (hardcoded for convenience)
 */
@Service
@Slf4j
public class DownloadTokenJwtService {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long defaultExpirationSeconds;

    /**
     * Constructor - initializes RSA keys from Secrets Manager and default expiration.
     *
     * Keys are automatically loaded from AWS Secrets Manager by Spring Cloud AWS.
     * Secret structure in Secrets Manager:
     * {
     *   "app.download-token.jwt.private-key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
     *   "app.download-token.jwt.public-key": "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
     * }
     *
     * @param privateKeyPem PEM-encoded RSA private key (from Secrets Manager)
     * @param publicKeyPem PEM-encoded RSA public key (from Secrets Manager)
     * @param defaultExpirationDays Default expiration in days (converts to seconds)
     * @throws Exception if keys cannot be loaded
     */
    public DownloadTokenJwtService(
            @Value("${app.download-token.jwt.private-key}") String privateKeyPem,
            @Value("${app.download-token.jwt.public-key}") String publicKeyPem,
            @Value("${app.download-token.jwt.expiration-days:7}") long defaultExpirationDays
    ) throws Exception {
        this.privateKey = loadPrivateKey(privateKeyPem);
        this.publicKey = loadPublicKey(publicKeyPem);
        this.defaultExpirationSeconds = Duration.ofDays(defaultExpirationDays).getSeconds();

        log.info("Download Token JWT Service initialized with RSA-256 keys, default expiration: {} days",
            defaultExpirationDays);
    }

    /**
     * Load RSA private key from PEM format.
     *
     * @param privateKeyPem PEM-encoded private key
     * @return PrivateKey instance
     * @throws Exception if key cannot be loaded
     */
    private PrivateKey loadPrivateKey(String privateKeyPem) throws Exception {
        // Remove PEM headers/footers and whitespace
        String privateKeyContent = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");

        // Decode base64
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);

        // Create private key
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Load RSA public key from PEM format.
     *
     * @param publicKeyPem PEM-encoded public key
     * @return PublicKey instance
     * @throws Exception if key cannot be loaded
     */
    private PublicKey loadPublicKey(String publicKeyPem) throws Exception {
        // Remove PEM headers/footers and whitespace
        String publicKeyContent = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----BEGIN RSA PUBLIC KEY-----", "")
            .replace("-----END RSA PUBLIC KEY-----", "")
            .replaceAll("\\s+", "");

        // Decode base64
        byte[] keyBytes = Base64.getDecoder().decode(publicKeyContent);

        // Create public key
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Generate a JWT token for download URL.
     *
     * @param reportId Report job ID
     * @param userId User who requested the report
     * @param districtId District ID
     * @param expiration Token expiration duration
     * @return Signed JWT token string
     */
    public String generateToken(
            String reportId,
            Integer userId,
            Integer districtId,
            Duration expiration
    ) {
        Instant now = Instant.now();
        Instant expirationTime = now.plus(expiration);
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
            .id(tokenId)                                    // jti: Unique token ID
            .subject(reportId)                              // sub: Report job ID
            .claim("userId", userId)                        // Custom claim: User ID
            .claim("districtId", districtId)                // Custom claim: District ID
            .issuedAt(Date.from(now))                       // iat: Issued at time
            .expiration(Date.from(expirationTime))          // exp: Expiration time
            .signWith(privateKey, Jwts.SIG.RS256)           // Sign with RSA-SHA256
            .compact();

        log.debug("Generated JWT download token: jti={}, sub={}, userId={}, districtId={}, exp={}",
            tokenId, reportId, userId, districtId, expirationTime);

        return token;
    }

    /**
     * Validate JWT token and extract claims.
     * This validates:
     * - Signature (using public key)
     * - Expiration time
     * - Token structure
     *
     * @param token JWT token string
     * @return Claims if token is valid and not expired
     * @throws JwtException if token is invalid, expired, or tampered
     */
    public Claims validateToken(String token) throws JwtException {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            log.debug("JWT token validated successfully: jti={}, sub={}, exp={}",
                claims.getId(), claims.getSubject(), claims.getExpiration());

            return claims;

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: jti={}, exp={}",
                e.getClaims().getId(), e.getClaims().getExpiration());
            throw e;
        } catch (SignatureException e) {
            log.warn("JWT token signature validation failed - possible tampering attempt");
            throw e;
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token");
            throw e;
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token");
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null");
            throw e;
        }
    }

    /**
     * Check if token is expired without throwing exception.
     *
     * @param token JWT token string
     * @return true if token is expired or invalid, false if valid
     */
    public boolean isTokenExpired(String token) {
        try {
            validateToken(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            // Other validation errors also mean token is not valid
            return true;
        }
    }

    /**
     * Extract report ID from token without full validation.
     * Useful for logging/debugging even when token is expired.
     *
     * @param token JWT token string
     * @return Report ID (subject claim) or null if cannot parse
     */
    public String getReportIdFromToken(String token) {
        try {
            // Parse without signature verification for debugging purposes
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
            // Simple JSON parsing for subject field
            if (payload.contains("\"sub\"")) {
                int subStart = payload.indexOf("\"sub\":\"") + 7;
                int subEnd = payload.indexOf("\"", subStart);
                if (subStart > 7 && subEnd > subStart) {
                    return payload.substring(subStart, subEnd);
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract report ID from token", e);
            return null;
        }
    }

    /**
     * Get token ID (jti) from validated claims.
     * The jti is used as the database token ID.
     *
     * @param claims JWT claims
     * @return Token ID (UUID string)
     */
    public String getTokenId(Claims claims) {
        return claims.getId();
    }

    /**
     * Get report ID from validated claims.
     *
     * @param claims JWT claims
     * @return Report ID
     */
    public String getReportId(Claims claims) {
        return claims.getSubject();
    }

    /**
     * Get user ID from validated claims.
     *
     * @param claims JWT claims
     * @return User ID
     */
    public Integer getUserId(Claims claims) {
        return claims.get("userId", Integer.class);
    }

    /**
     * Get district ID from validated claims.
     *
     * @param claims JWT claims
     * @return District ID
     */
    public Integer getDistrictId(Claims claims) {
        return claims.get("districtId", Integer.class);
    }
}

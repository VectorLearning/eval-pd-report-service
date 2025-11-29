package com.evplus.report.security;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * Shared JWT Secret Key for Local Development.
 *
 * WARNING: This is for LOCAL DEVELOPMENT ONLY.
 * DO NOT use this key in production environments.
 *
 * Both JwtTokenGeneratorController and SecurityConfig use this shared key
 * to ensure tokens generated can be validated.
 */
public final class LocalJwtSecretKey {

    // Fixed base64-encoded secret for local development (256+ bits for HS256)
    // Base64 of: "ev-plus-local-secret-key-32bytes!" (36 bytes)
    private static final String SECRET_BASE64 = "ZXYtcGx1cy1sb2NhbC1zZWNyZXQta2V5LTMyYnl0ZXMh";

    // Singleton secret key instance
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
        Base64.getDecoder().decode(SECRET_BASE64)
    );

    private LocalJwtSecretKey() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the shared secret key for local JWT signing and validation.
     *
     * @return the secret key
     */
    public static SecretKey getSecretKey() {
        return SECRET_KEY;
    }
}

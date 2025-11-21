package com.evplus.report.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom JWT Authentication Converter.
 *
 * Extracts user information and roles/authorities from JWT claims.
 * Maps JWT claims to Spring Security authorities.
 */
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);

        // Create user principal from JWT claims
        UserPrincipal principal = UserPrincipal.builder()
            .userId(extractUserId(jwt))
            .email(jwt.getClaimAsString("email"))
            .firstName(jwt.getClaimAsString("given_name"))
            .lastName(jwt.getClaimAsString("family_name"))
            .username(jwt.getSubject())
            .authorities(authorities)
            .build();

        return new JwtAuthenticationToken(jwt, authorities, principal.getUsername());
    }

    /**
     * Extract authorities/roles from JWT claims.
     *
     * Looks for roles in the "roles" claim (array of strings).
     * Prefixes each role with "ROLE_" as per Spring Security convention.
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // Try to get roles from "roles" claim
        List<String> roles = jwt.getClaimAsStringList("roles");

        if (roles == null || roles.isEmpty()) {
            // Try alternative claim names
            roles = jwt.getClaimAsStringList("authorities");
        }

        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        return roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()))
            .collect(Collectors.toList());
    }

    /**
     * Extract user ID from JWT claims.
     *
     * Tries multiple possible claim names for user ID.
     */
    private Integer extractUserId(Jwt jwt) {
        // Try different possible claim names for user ID
        Object userIdClaim = jwt.getClaim("user_id");
        if (userIdClaim == null) {
            userIdClaim = jwt.getClaim("userId");
        }
        if (userIdClaim == null) {
            userIdClaim = jwt.getClaim("id");
        }

        if (userIdClaim instanceof Number) {
            return ((Number) userIdClaim).intValue();
        } else if (userIdClaim instanceof String) {
            try {
                return Integer.parseInt((String) userIdClaim);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }
}

package com.evplus.report.security;

import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * User Principal - represents the authenticated user.
 *
 * This object is extracted from JWT claims and made available
 * in controllers via @AuthenticationPrincipal annotation.
 */
@Data
@Builder
public class UserPrincipal {

    private Integer userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private Collection<GrantedAuthority> authorities;

    /**
     * Check if user has admin role.
     */
    public boolean isAdmin() {
        return authorities != null && authorities.stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Get full name (firstName + lastName).
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return username;
        }
        return String.format("%s %s",
            firstName != null ? firstName : "",
            lastName != null ? lastName : "").trim();
    }
}

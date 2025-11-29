package com.evplus.report.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Custom Authentication Token that holds UserPrincipal as the principal.
 *
 * Used to properly integrate JWT authentication with UserPrincipal-based
 * authorization in controllers.
 */
public class UserPrincipalAuthenticationToken extends AbstractAuthenticationToken {

    private final UserPrincipal principal;
    private final Object credentials;

    /**
     * Create an authenticated token with UserPrincipal.
     *
     * @param principal the UserPrincipal containing user information
     * @param credentials the credentials (typically the JWT token)
     * @param authorities the granted authorities
     */
    public UserPrincipalAuthenticationToken(
            UserPrincipal principal,
            Object credentials,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}

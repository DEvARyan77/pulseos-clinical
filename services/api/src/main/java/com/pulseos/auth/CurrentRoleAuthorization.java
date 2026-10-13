package com.pulseos.auth;

import java.util.Locale;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Checks persistent role and account status on every protected operation.
 * Role changes and deactivations take effect without waiting for JWT expiry.
 */
public class CurrentRoleAuthorization implements AuthorizationManager<RequestAuthorizationContext> {
    private final StaffAccountRepository accounts;
    private final StaffRole required;
    private final String legacyUsername;

    public CurrentRoleAuthorization(StaffAccountRepository accounts, StaffRole required,
                                    String legacyUsername) {
        this.accounts = accounts;
        this.required = required;
        this.legacyUsername = legacyUsername == null ? "" :
            legacyUsername.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public AuthorizationDecision authorize(java.util.function.Supplier<? extends Authentication> authentication,
                                           RequestAuthorizationContext context) {
        Authentication current = authentication.get();
        if (!(current instanceof JwtAuthenticationToken jwt) || !current.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        String username = jwt.getToken().getSubject();
        if (username == null || username.isBlank()) return new AuthorizationDecision(false);
        var staff = accounts.findByUsername(username);
        if (staff.isPresent()) {
            return new AuthorizationDecision(staff.get().isEnabled() &&
                required.name().equals(staff.get().getRole()));
        }
        // The old env-based login has no persistent account and is never ADMIN.
        return new AuthorizationDecision(required == StaffRole.CLINICIAN &&
            !legacyUsername.isBlank() &&
            legacyUsername.equals(username.strip().toLowerCase(Locale.ROOT)));
    }
}

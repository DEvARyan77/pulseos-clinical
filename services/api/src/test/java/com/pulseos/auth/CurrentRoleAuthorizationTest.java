package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentRoleAuthorizationTest {
    private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);

    private JwtAuthenticationToken token(String username) {
        return new JwtAuthenticationToken(Jwt.withTokenValue("example-token")
            .header("alg", "HS256").subject(username).claim("roles", java.util.List.of("CLINICIAN"))
            .build(), java.util.List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CLINICIAN")));
    }

    @Test
    void activeAdminCanManageStaffButNotClinicalRecords() {
        var account = mock(StaffAccount.class);
        when(account.getRole()).thenReturn("ADMIN");
        when(account.isEnabled()).thenReturn(true);
        when(accounts.findByUsername("owner")).thenReturn(Optional.of(account));
        assertThat(new CurrentRoleAuthorization(accounts, StaffRole.ADMIN, "")
            .authorize(() -> token("owner"), null).isGranted()).isTrue();
        assertThat(new CurrentRoleAuthorization(accounts, StaffRole.CLINICIAN, "")
            .authorize(() -> token("owner"), null).isGranted()).isFalse();
    }

    @Test
    void revokingRoleOrDisablingAccountTakesEffectImmediately() {
        var account = mock(StaffAccount.class);
        when(account.getRole()).thenReturn("CLINICIAN");
        when(account.isEnabled()).thenReturn(true, false);
        when(accounts.findByUsername("user")).thenReturn(Optional.of(account));
        var guard = new CurrentRoleAuthorization(accounts, StaffRole.CLINICIAN, "");
        assertThat(guard.authorize(() -> token("user"), null).isGranted()).isTrue();
        assertThat(guard.authorize(() -> token("user"), null).isGranted()).isFalse();
    }

    @Test
    void legacyAccountCannotBecomeAdmin() {
        when(accounts.findByUsername("legacy")).thenReturn(Optional.empty());
        assertThat(new CurrentRoleAuthorization(accounts, StaffRole.CLINICIAN, "LEGACY")
            .authorize(() -> token("legacy"), null).isGranted()).isTrue();
        assertThat(new CurrentRoleAuthorization(accounts, StaffRole.ADMIN, "LEGACY")
            .authorize(() -> token("legacy"), null).isGranted()).isFalse();
    }
}

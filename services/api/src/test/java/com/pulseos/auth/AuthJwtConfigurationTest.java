package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

class AuthJwtConfigurationTest {
    private final AuthJwtConfiguration configuration = new AuthJwtConfiguration();

    @Test
    void rejectsShortSigningKey() {
        assertThatThrownBy(() -> configuration.jwtSigningKey(Base64.getEncoder()
            .encodeToString("short".getBytes(StandardCharsets.UTF_8))))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("at least 32 bytes");
    }

    @Test
    void issuedJwtCanBeVerified() {
        String secret = Base64.getEncoder().encodeToString(
            "this-test-only-key-is-longer-than-thirty-two-bytes".getBytes(StandardCharsets.UTF_8));
        var key = configuration.jwtSigningKey(secret);
        var encoder = configuration.jwtEncoder(key);
        var decoder = configuration.jwtDecoder(key);
        var claims = JwtClaimsSet.builder().issuer("pulseos-api").subject("example-clinician")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build();
        String token = encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThat(decoder.decode(token).getSubject()).isEqualTo("example-clinician");
    }

    @Test
    void acceptsMixedCaseUsernameAndReturnsCanonicalIdentity() {
        var repository = org.mockito.Mockito.mock(StaffAccountRepository.class);
        var account = org.mockito.Mockito.mock(StaffAccount.class);
        org.mockito.Mockito.when(account.getUsername()).thenReturn("doctor");
        org.mockito.Mockito.when(account.getPasswordHash()).thenReturn("hashed-password");
        org.mockito.Mockito.when(account.getRole()).thenReturn("CLINICIAN");
        org.mockito.Mockito.when(account.isEnabled()).thenReturn(true);
        org.mockito.Mockito.when(repository.findByUsername("doctor")).thenReturn(java.util.Optional.of(account));

        var principal = configuration.staffUsers(repository, "", "").loadUserByUsername("  DOCTOR  ");
        assertThat(principal.getUsername()).isEqualTo("doctor");
        org.mockito.Mockito.verify(repository).findByUsername("doctor");
    }

    @Test
    void supportsLoginByRegisteredEmail() {
        var repository = org.mockito.Mockito.mock(StaffAccountRepository.class);
        var account = org.mockito.Mockito.mock(StaffAccount.class);
        org.mockito.Mockito.when(account.getUsername()).thenReturn("doctor");
        org.mockito.Mockito.when(account.getPasswordHash()).thenReturn("hashed-password");
        org.mockito.Mockito.when(account.getRole()).thenReturn("CLINICIAN");
        org.mockito.Mockito.when(account.isEnabled()).thenReturn(true);
        org.mockito.Mockito.when(repository.findByEmail("doctor@example.test"))
            .thenReturn(java.util.Optional.of(account));

        var principal = configuration.staffUsers(repository, "", "")
            .loadUserByUsername("DOCTOR@EXAMPLE.TEST");
        assertThat(principal.getUsername()).isEqualTo("doctor");
        org.mockito.Mockito.verify(repository).findByEmail("doctor@example.test");
    }

}

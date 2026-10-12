package com.pulseos.auth;

import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@ConditionalOnProperty(prefix = "pulseos.auth", name = "enabled", havingValue = "true")
public class AuthJwtConfiguration {
    @Bean
    SecretKey jwtSigningKey(@Value("${PULSEOS_AUTH_JWT_SECRET_BASE64:}") String encoded) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("PULSEOS_AUTH_JWT_SECRET_BASE64 must be valid Base64", exception);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException("PULSEOS_AUTH_JWT_SECRET_BASE64 must decode to at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
            .macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("pulseos-api"));
        return decoder;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    UserDetailsService staffUsers(StaffAccountRepository accounts,
            @Value("${PULSEOS_AUTH_USERNAME:}") String legacyUsername,
            @Value("${PULSEOS_AUTH_PASSWORD_HASH:}") String legacyHash) {
        // The optional legacy staff identity makes previous deployments compatible.
        // Newly registered users are persisted in PostgreSQL.
        return submittedIdentity -> {
            String normalized = submittedIdentity.strip().toLowerCase(Locale.ROOT);
            Optional<StaffAccount> matching = accounts.findByUsername(normalized);
            if (matching.isEmpty() && normalized.contains("@")) {
                matching = accounts.findByEmail(normalized);
            }
            return matching.map(account -> User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .roles(account.getRole())
                .disabled(!account.isEnabled())
                .build()).orElseGet(() -> {
                if (!legacyUsername.isBlank() &&
                    (submittedIdentity.strip().equals(legacyUsername)
                      || normalized.equals(legacyUsername.toLowerCase(Locale.ROOT))) &&
                    legacyHash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
                    return User.withUsername(legacyUsername).password(legacyHash)
                        .roles("CLINICIAN").build();
                }
                throw new UsernameNotFoundException("Unknown staff account");
            });
        };
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService staffUsers, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(staffUsers);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}

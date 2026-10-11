package com.pulseos.auth;

import java.util.Base64;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

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
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService staffUsers(
            @Value("${PULSEOS_AUTH_USERNAME:}") String username,
            @Value("${PULSEOS_AUTH_PASSWORD_HASH:}") String passwordHash) {
        if (username.isBlank() || username.length() > 80 || passwordHash.isBlank()
                || !passwordHash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalStateException("Configure PULSEOS_AUTH_USERNAME and a BCrypt PULSEOS_AUTH_PASSWORD_HASH");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
            .password(passwordHash).roles("CLINICIAN").build());
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService staffUsers, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(staffUsers);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}

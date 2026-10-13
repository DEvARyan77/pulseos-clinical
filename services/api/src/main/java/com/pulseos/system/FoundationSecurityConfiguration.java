package com.pulseos.system;

import org.springframework.beans.factory.annotation.Value;
import com.pulseos.auth.CurrentRoleAuthorization;
import com.pulseos.auth.StaffRole;
import com.pulseos.auth.StaffAccountRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class FoundationSecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http,
    @Value("${pulseos.auth.enabled:false}") boolean authEnabled,
    @Value("${PULSEOS_AUTH_USERNAME:}") String legacyUsername,
    StaffAccountRepository staffAccounts) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth->auth
        .requestMatchers("/actuator/health","/api/v1/system/status").permitAll()
        .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/register","/api/v1/auth/patient-register","/api/v1/auth/verify",
            "/api/v1/auth/resend", "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password").permitAll()
        .requestMatchers(HttpMethod.GET,"/api/v1/auth/registration-status").permitAll()
        .requestMatchers("/api/v1/staff-chat/**")
          .access((authentication, context) -> {
            var token = authentication.get();
            if (!(token instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt)
                || !token.isAuthenticated())
              return new org.springframework.security.authorization.AuthorizationDecision(false);
            return new org.springframework.security.authorization.AuthorizationDecision(
              staffAccounts.findByUsername(jwt.getToken().getSubject())
                .filter(com.pulseos.auth.StaffAccount::isEnabled)
                .filter(a->com.pulseos.auth.StaffRole.ADMIN.name().equals(a.getRole())||
                    com.pulseos.auth.StaffRole.CLINICIAN.name().equals(a.getRole())).isPresent());
          })
        .requestMatchers("/api/v1/admin/**")
          .access(new CurrentRoleAuthorization(staffAccounts, StaffRole.ADMIN, legacyUsername))
        .requestMatchers(HttpMethod.GET,"/api/v1/auth/me").authenticated()
        .requestMatchers("/api/v1/auth/profile").authenticated()
        .requestMatchers("/api/v1/patient-portal/**").authenticated()
        .requestMatchers("/api/v1/demo-appointments","/api/v1/demo-appointments/**")
          .access(new CurrentRoleAuthorization(staffAccounts, StaffRole.CLINICIAN, legacyUsername))
        .requestMatchers("/api/v1/patients","/api/v1/patients/**")
          .access(new CurrentRoleAuthorization(staffAccounts, StaffRole.CLINICIAN, legacyUsername))
        .anyRequest().denyAll());
    if(authEnabled) {
      JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
      roles.setAuthoritiesClaimName("roles");
      roles.setAuthorityPrefix("ROLE_");
      JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
      converter.setJwtGrantedAuthoritiesConverter(roles);
      http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));
    }
    return http.build();
  }
}

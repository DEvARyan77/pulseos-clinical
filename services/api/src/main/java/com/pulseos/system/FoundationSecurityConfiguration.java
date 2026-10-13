package com.pulseos.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class FoundationSecurityConfiguration {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http,
    @Value("${pulseos.auth.enabled:false}") boolean authEnabled) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
      .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(auth->auth
        .requestMatchers("/actuator/health","/api/v1/system/status").permitAll()
        .requestMatchers(HttpMethod.POST,"/api/v1/auth/login","/api/v1/auth/register","/api/v1/auth/verify",
            "/api/v1/auth/resend", "/api/v1/auth/forgot-password",
            "/api/v1/auth/reset-password").permitAll()
        .requestMatchers(HttpMethod.GET,"/api/v1/auth/registration-status").permitAll()
        .requestMatchers(HttpMethod.GET,"/api/v1/auth/me").authenticated()
        .requestMatchers("/api/v1/demo-appointments","/api/v1/demo-appointments/**").authenticated()
        .requestMatchers("/api/v1/patients","/api/v1/patients/**").authenticated()
        .anyRequest().denyAll());
    if(authEnabled)http.oauth2ResourceServer(oauth->oauth.jwt(Customizer.withDefaults()));
    return http.build();
  }
}

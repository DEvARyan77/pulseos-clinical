package com.pulseos.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * SMTP beans are created only for explicitly enabled SMTP delivery.
 * Free Render web services block outbound SMTP ports: prefer HTTPS provider there.
 */
@Configuration
@ConditionalOnProperty(prefix = "pulseos.mail", name = "provider", havingValue = "smtp")
public class SmtpMailConfiguration {
    @Bean
    JavaMailSender pulseosSmtpSender(
        @Value("${PULSEOS_SMTP_HOST:}") String host,
        @Value("${PULSEOS_SMTP_PORT:587}") int port,
        @Value("${PULSEOS_SMTP_USER:}") String username,
        @Value("${PULSEOS_SMTP_PASSWORD:}") String password
    ) {
        if (host.isBlank() || username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("SMTP requires host, username and password configured in the environment");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setUsername(username);
        sender.setPassword(password);
        var props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return sender;
    }
}

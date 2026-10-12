package com.pulseos.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * HTTPS email provider works on Render Free; SMTP works only when outgoing ports are allowed.
 * Never log the verification code, provider token, or SMTP credentials.
 */
@Service
public class VerificationMailSender {
    private final String provider;
    private final String from;
    private final String resendApiKey;
    private final ObjectProvider<JavaMailSender> smtp;
    private final HttpClient client;

    public VerificationMailSender(
        @Value("${PULSEOS_EMAIL_PROVIDER:disabled}") String provider,
        @Value("${PULSEOS_EMAIL_FROM:}") String from,
        @Value("${PULSEOS_RESEND_API_KEY:}") String resendApiKey,
        ObjectProvider<JavaMailSender> smtp) {
        this.provider = provider;
        this.from = from;
        this.resendApiKey = resendApiKey;
        this.smtp = smtp;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public void send(String destination, String code) {
        if (from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Verification email is not configured");
        }
        String message = "Your PulseOS verification code is: " + code +
            "\nThis code expires in 10 minutes. If you did not request this code, ignore this message." +
            "\nNever use real patient data in this portfolio application.";
        try {
            switch (provider.toLowerCase(java.util.Locale.ROOT)) {
                case "smtp" -> {
                    JavaMailSender mail = smtp.getIfAvailable();
                    if (mail == null) throw new IllegalStateException("SMTP mail service is not configured");
                    SimpleMailMessage email = new SimpleMailMessage();
                    email.setFrom(from);
                    email.setTo(destination);
                    email.setSubject("Verify your PulseOS email");
                    email.setText(message);
                    mail.send(email);
                }
                case "resend" -> {
                    if (resendApiKey.isBlank()) throw new IllegalStateException("HTTPS mail API key missing");
                    String payload = "{\"from\":" + quote(from) + ",\"to\":[" + quote(destination) +
                        "],\"subject\":\"Verify your PulseOS email\",\"text\":" + quote(message) + "}";
                    HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
                        .timeout(Duration.ofSeconds(20))
                        .header("Authorization", "Bearer " + resendApiKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                    HttpResponse<String> result = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (result.statusCode() < 200 || result.statusCode() >= 300)
                        throw new IllegalStateException("Email provider rejected the message");
                }
                default -> throw new IllegalStateException("Email delivery provider is not enabled");
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            // Do not expose SMTP/API credential details, email contents or OTP in errors.
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Verification email could not be delivered. Contact the application administrator.");
        }
    }
    private static String quote(String text) {
        StringBuilder encoded = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> encoded.append("\\\"");
                case '\\' -> encoded.append("\\\\");
                case '\n' -> encoded.append("\\n");
                case '\r' -> encoded.append("\\r");
                case '\t' -> encoded.append("\\t");
                default -> {
                    if (c < 32) encoded.append(String.format("\\u%04x", (int) c));
                    else encoded.append(c);
                }
            }
        }
        return encoded.append('"').toString();
    }

}

package com.pulseos.auth;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(VerificationMailSender.class);
    private final String provider;
    private final String from;
    private final String resendApiKey;
    private final String googleScriptUrl;
    private final String googleScriptKey;
    private final ObjectProvider<JavaMailSender> smtp;
    private final HttpClient client;

    public VerificationMailSender(
        @Value("${PULSEOS_EMAIL_PROVIDER:disabled}") String provider,
        @Value("${PULSEOS_EMAIL_FROM:}") String from,
        @Value("${PULSEOS_RESEND_API_KEY:}") String resendApiKey,
        @Value("${PULSEOS_GOOGLE_SCRIPT_URL:}") String googleScriptUrl,
        @Value("${PULSEOS_GOOGLE_SCRIPT_KEY:}") String googleScriptKey,
        ObjectProvider<JavaMailSender> smtp) {
        this.provider = provider;
        this.from = from;
        this.resendApiKey = resendApiKey;
        this.googleScriptUrl = googleScriptUrl;
        this.googleScriptKey = googleScriptKey;
        this.smtp = smtp;
        this.client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10)).build();
    }


    /**
     * Configuration check only. Does not send email, reveal credentials,
     * or promise the remote provider is currently reachable.
     */
    public boolean isConfigured() {
        if (from.isBlank()) return false;
        return switch (provider.toLowerCase(java.util.Locale.ROOT)) {
            case "smtp" -> smtp.getIfAvailable() != null;
            case "resend" -> !resendApiKey.isBlank();
            case "google-script" -> googleScriptKey.length() >= 48 && isGoogleScriptEndpoint();
            default -> false;
        };
    }

    private boolean isGoogleScriptEndpoint() {
        try {
            URI endpoint = URI.create(googleScriptUrl);
            return "https".equals(endpoint.getScheme())
                && "script.google.com".equals(endpoint.getHost())
                && endpoint.getPort() == -1
                && endpoint.getRawQuery() == null
                && endpoint.getPath().matches("/macros/s/[^/]+/exec");
        } catch (IllegalArgumentException exception) {
            return false;
        }
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
                case "google-script" -> {
                    if (googleScriptKey.length() < 48) {
                        throw new IllegalStateException("Google Apps Script relay secret is not configured");
                    }
                    URI endpoint = URI.create(googleScriptUrl);
                    if (!"https".equals(endpoint.getScheme())
                        || !"script.google.com".equals(endpoint.getHost())
                        || endpoint.getPort() != -1
                        || endpoint.getRawQuery() != null
                        || !endpoint.getPath().matches("/macros/s/[^/]+/exec")) {
                        throw new IllegalStateException("Google Apps Script relay URL is invalid");
                    }
                    String payload = "{\"relayKey\":" + quote(googleScriptKey) +
                        ",\"email\":" + quote(destination) + ",\"code\":" + quote(code) + "}";
                    HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .timeout(Duration.ofSeconds(45))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();
                    HttpResponse<String> result = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (result.statusCode() != 200) {
                        log.warn("Apps Script relay HTTP status={}", result.statusCode());
                        throw new IllegalStateException("Apps Script relay HTTP failure");
                    }
                    if (!"OK".equals(result.body().trim())) {
                        String category = switch (result.body().trim()) {
                            case "DENIED" -> "shared_key_rejected";
                            case "INVALID" -> "invalid_delivery_request";
                            case "ERROR" -> "script_execution_error";
                            default -> "unexpected_script_response";
                        };
                        log.warn("Apps Script relay rejected delivery category={}", category);
                        throw new IllegalStateException("Apps Script relay failed");
                    }
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
                    if (result.statusCode() < 200 || result.statusCode() >= 300) {
                        log.warn("HTTPS mail provider HTTP status={}", result.statusCode());
                        throw new IllegalStateException("Email provider rejected the message");
                    }
                }
                default -> throw new IllegalStateException("Email delivery provider is not enabled");
            }
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            String safeProvider = switch (provider.toLowerCase(java.util.Locale.ROOT)) {
                case "smtp", "resend", "google-script" -> provider.toLowerCase(java.util.Locale.ROOT);
                default -> "unknown";
            };
            log.warn("Verification mail failed provider={} category={}",
                safeProvider, exception.getClass().getSimpleName());
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

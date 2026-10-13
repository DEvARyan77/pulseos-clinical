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
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
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
        sendCode(destination, code, false);
    }

    public void sendPasswordReset(String destination, String code) {
        sendCode(destination, code, true);
    }

    private void sendCode(String destination, String code, boolean reset) {
        if (from.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Verification email is not configured");
        }
        String subject = reset ? "Reset your PulseOS password" : "Verify your PulseOS email";
        String message = (reset ? "Your PulseOS password reset code is: " :
            "Your PulseOS verification code is: ") + code +
            "\nThis code expires in 10 minutes. If you did not request it, ignore this message." +
            "\nThis portfolio is for synthetic data only.";
        String html = brandedHtml(code, reset);
        try {
            switch (provider.toLowerCase(java.util.Locale.ROOT)) {
                case "smtp" -> {
                    JavaMailSender mail = smtp.getIfAvailable();
                    if (mail == null) throw new IllegalStateException("SMTP mail service is not configured");
                    MimeMessage email = mail.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(email, true, "UTF-8");
                    helper.setFrom(from);
                    helper.setTo(destination);
                    helper.setSubject(subject);
                    helper.setText(message, html);
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
                        ",\"email\":" + quote(destination) + ",\"code\":" + quote(code) +
                        ",\"kind\":" + quote(reset ? "reset" : "signup") + "}";
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
                        "],\"subject\":" + quote(subject) + ",\"text\":" + quote(message) +
                        ",\"html\":" + quote(html) + "}";
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

    private static String brandedHtml(String code, boolean reset) {
        String title = reset ? "Reset your password" : "Verify your email";
        String description = reset ? "Use this one-time code to choose a new password." :
            "Use this one-time code to finish creating your account.";
        return "<!doctype html><html><head><meta charset=\"UTF-8\"></head>" +
            "<body style=\"margin:0;padding:32px 12px;background:#f7f7f4;font-family:Arial,Helvetica,sans-serif;color:#272933\">" +
            "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" style=\"max-width:520px;width:100%;margin:auto;background:#fff;border:1px solid #e8e4d9;border-radius:16px\"><tr><td style=\"padding:34px 34px 14px\">" +
            "<div style=\"font-size:28px;font-weight:800;letter-spacing:-1px\">Pulse<span style=\"color:#bb8a16\">OS</span></div>" +
            "<p style=\"color:#785409;font-size:11px;letter-spacing:2px;font-weight:700;margin:30px 0 8px\">SECURE ACCOUNT ACCESS</p>" +
            "<h1 style=\"margin:0 0 12px;font-size:26px;line-height:1.3\">" + title + "</h1>" +
            "<p style=\"font-size:15px;color:#555861;line-height:1.6;margin:0 0 24px\">" + description + "</p>" +
            "<div style=\"text-align:center;background:#fff5cf;border:1px solid #e5b52b;border-radius:12px;padding:21px 10px;margin-bottom:20px\">" +
            "<span style=\"font-family:Arial,Helvetica,sans-serif;font-size:34px;letter-spacing:9px;font-weight:800;color:#171b24\">" + code + "</span></div>" +
            "<p style=\"font-size:13px;line-height:1.7;color:#555861\">This code expires in <b>10 minutes</b>. Never share it. If you did not request it, you can ignore this email.</p>" +
            "</td></tr><tr><td style=\"padding:18px 34px 28px;border-top:1px solid #e8e4d9;color:#777983;font-size:11px;line-height:1.6\">" +
            "PulseOS is a clinical operations portfolio using fictional data only. No real patient records.</td></tr></table></body></html>";
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

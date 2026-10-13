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
        String subtitle = reset
            ? "We received a request to reset the password for your PulseOS account."
            : "Welcome to PulseOS. You're one step away from activating your account.";
        String instruction = reset
            ? "Enter the verification code below on the password recovery screen."
            : "Enter the verification code below to complete your registration.";
        String eyebrow = reset ? "PASSWORD RECOVERY" : "ACCOUNT VERIFICATION";
        String preview = reset
            ? "Your PulseOS password reset code is ready. Expires in 10 minutes."
            : "Your PulseOS email verification code is ready. Expires in 10 minutes.";

        // Inline, table-based markup: no tracking pixels, remote assets or user HTML.
        // Code is created by the server as exactly six digits.
        return """
            <!doctype html><html lang="en"><head>
            <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="color-scheme" content="light">
            <title>PulseOS · __TITLE__</title></head>
            <body style="margin:0;padding:0;background-color:#f4f5f3;color:#22262f;font-family:Arial,Helvetica,sans-serif;-webkit-text-size-adjust:100%;">
            <div style="display:none!important;font-size:1px;line-height:1px;color:#f4f5f3;max-height:0;max-width:0;opacity:0;overflow:hidden;">__PREVIEW__</div>
            <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%" style="width:100%;border-collapse:collapse;background-color:#f4f5f3;">
              <tr><td align="center" style="padding:36px 16px 40px;">
                <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="560" style="width:100%;max-width:560px;border-collapse:separate;border-spacing:0;">
                  <tr><td style="background-color:#171b24;padding:27px 34px;border-radius:16px 16px 0 0;">
                    <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0">
                      <tr><td style="font-size:26px;font-weight:800;letter-spacing:-1.2px;color:#ffffff;">Pulse<span style="color:#f4c64d;">OS</span></td>
                      <td align="right" style="font-size:10px;letter-spacing:1.6px;font-weight:bold;color:#d3d7de;">SECURE ACCESS</td></tr>
                    </table>
                    <div style="height:4px;width:64px;background-color:#f4c64d;border-radius:4px;margin-top:19px;"></div>
                  </td></tr>
                  <tr><td style="background-color:#ffffff;border-left:1px solid #e5e8e5;border-right:1px solid #e5e8e5;padding:36px 34px 12px;">
                    <p style="margin:0 0 12px;font-size:11px;letter-spacing:2px;font-weight:700;color:#987015;">__EYEBROW__</p>
                    <h1 style="font-size:29px;line-height:1.25;font-weight:800;letter-spacing:-0.6px;color:#20242c;margin:0 0 14px;">__TITLE__</h1>
                    <p style="font-size:15px;line-height:1.7;color:#606773;margin:0 0 8px;">__SUBTITLE__ __INSTRUCTION__</p>
                  </td></tr>
                  <tr><td style="background-color:#ffffff;border-left:1px solid #e5e8e5;border-right:1px solid #e5e8e5;padding:14px 34px 22px;">
                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="background-color:#fff6dc;border:1px solid #f0d18b;border-radius:12px;width:100%;">
                      <tr><td align="center" style="padding:24px 10px 21px;">
                        <div style="font-size:10px;font-weight:800;letter-spacing:1.8px;color:#886313;margin-bottom:12px;">YOUR ONE-TIME CODE</div>
                        <div style="font-size:38px;line-height:1.2;letter-spacing:9px;font-weight:800;color:#242832;font-family:Arial,Helvetica,sans-serif;">__CODE__</div>
                        <div style="font-size:12px;line-height:1.5;color:#876c32;margin-top:11px;">Valid for 10 minutes</div>
                      </td></tr>
                    </table>
                  </td></tr>
                  <tr><td style="background-color:#ffffff;border-left:1px solid #e5e8e5;border-right:1px solid #e5e8e5;padding:0 34px 36px;">
                    <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">
                      <tr><td style="border-left:3px solid #e6b642;background-color:#faf9f5;padding:14px 16px;font-size:12px;line-height:1.7;color:#606773;">
                        <strong style="color:#333743;">Keep your account secure.</strong>
                        Never share this code. PulseOS will not ask you to send it by email or chat.
                        <br>If you did not request this, simply ignore the message.
                      </td></tr>
                    </table>
                  </td></tr>
                  <tr><td style="background-color:#f9faf9;border:1px solid #e5e8e5;border-top:1px solid #eceeea;border-radius:0 0 16px 16px;padding:22px 34px 26px;">
                    <p style="font-size:12px;font-weight:800;color:#464c56;margin:0 0 6px;">PulseOS &nbsp;·&nbsp; Clinical operations demo</p>
                    <p style="font-size:11px;line-height:1.7;color:#818794;margin:0;">
                      Fictional data only. Not approved for real patient information.
                      <br>This message was sent automatically. Please do not reply.
                    </p>
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body></html>
            """
            .replace("__TITLE__", title)
            .replace("__SUBTITLE__", subtitle)
            .replace("__INSTRUCTION__", instruction)
            .replace("__EYEBROW__", eyebrow)
            .replace("__PREVIEW__", preview)
            .replace("__CODE__", code);
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

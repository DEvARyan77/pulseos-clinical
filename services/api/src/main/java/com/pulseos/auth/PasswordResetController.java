package com.pulseos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "pulseos.auth", name = "enabled", havingValue = "true")
public class PasswordResetController {
    private static final String GENERIC_NOTICE =
        "If that email belongs to an account, a reset code will be sent shortly.";

    private final StaffAccountRepository accounts;
    private final PasswordResetChallengeRepository challenges;
    private final PasswordEncoder passwordEncoder;
    private final VerificationMailSender mailer;
    private final SecretKey signingKey;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetController(
        StaffAccountRepository accounts, PasswordResetChallengeRepository challenges,
        PasswordEncoder passwordEncoder, VerificationMailSender mailer, SecretKey signingKey) {
        this.accounts = accounts;
        this.challenges = challenges;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.signingKey = signingKey;
    }

    public record ForgotRequest(@NotBlank @Email @Size(max = 254) String email) {}
    public record ResetRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code,
        @NotBlank @Size(min = 8, max = 128) String password) {}

    private String codeProof(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return HexFormat.of().formatHex(mac.doFinal(
                ("pulseos-password-reset:v1:" + email + ":" + code)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Password reset proof unavailable");
        }
    }

    private ResponseEntity<Map<String, String>> genericAccepted() {
        return ResponseEntity.accepted().header("Cache-Control", "no-store")
            .body(Map.of("message", GENERIC_NOTICE));
    }

    @PostMapping("/forgot-password")
    @Transactional
    public ResponseEntity<Map<String, String>> forgot(@Valid @RequestBody ForgotRequest request) {
        if (!mailer.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Cache-Control", "no-store")
                .body(Map.of("error", "Email delivery is not configured."));
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (accounts.findByEmail(email).filter(StaffAccount::isEnabled).isEmpty()) {
            return genericAccepted();
        }
        Instant now = Instant.now();
        challenges.deleteByExpiresAtBefore(now);
        var existing = challenges.findByEmail(email);
        if (existing.isPresent() && existing.get().getSentAt().isAfter(now.minusSeconds(60))) {
            return genericAccepted();
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String hash = codeProof(email, code);
        if (existing.isPresent()) {
            existing.get().replace(hash, now);
            challenges.saveAndFlush(existing.get());
        } else {
            challenges.saveAndFlush(new PasswordResetChallenge(email, hash, now));
        }
        try {
            mailer.sendPasswordReset(email, code);
        } catch (ResponseStatusException deliveryError) {
            // Never disclose which addresses exist, even when the provider fails.
            // Preserve the generic response while rolling back the challenge.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return genericAccepted();
    }

    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<Map<String, String>> reset(@Valid @RequestBody ResetRequest request) {
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        PasswordResetChallenge challenge = challenges.findByEmail(email).orElse(null);
        Instant now = Instant.now();
        if (challenge == null || !challenge.getExpiresAt().isAfter(now)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset code."));
        }
        if (challenge.getAttempts() >= 5) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Too many incorrect codes. Request a new code."));
        }
        String actual = codeProof(email, request.code());
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                challenge.getVerificationHash().getBytes(StandardCharsets.US_ASCII))) {
            challenge.failedAttempt();
            challenges.saveAndFlush(challenge);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset code."));
        }
        StaffAccount account = accounts.findByEmail(email).filter(StaffAccount::isEnabled).orElse(null);
        if (account == null) {
            challenges.delete(challenge);
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired reset code."));
        }
        account.updatePasswordHash(passwordEncoder.encode(request.password()));
        accounts.saveAndFlush(account);
        challenges.delete(challenge);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .body(Map.of("message", "Password updated. You may now sign in."));
    }
}

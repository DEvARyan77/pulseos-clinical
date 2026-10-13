package com.pulseos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "pulseos.auth", name = "enabled", havingValue = "true")
public class RegistrationController {
    private static final Logger log = LoggerFactory.getLogger(RegistrationController.class);
    private final StaffAccountRepository accounts;
    private final PendingRegistrationRepository pending;
    private final PasswordEncoder passwordEncoder;
    private final VerificationMailSender mailer;
    private final SecretKey signingKey;
    private final boolean registrationEnabled;
    private final SecureRandom random = new SecureRandom();

    public RegistrationController(
        StaffAccountRepository accounts, PendingRegistrationRepository pending,
        PasswordEncoder passwordEncoder, VerificationMailSender mailer, SecretKey signingKey,
        @Value("${PULSEOS_REGISTRATION_ENABLED:false}") boolean registrationEnabled) {
        this.accounts = accounts;
        this.pending = pending;
        this.passwordEncoder = passwordEncoder;
        this.mailer = mailer;
        this.signingKey = signingKey;
        this.registrationEnabled = registrationEnabled;
    }

    public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_.-]{2,39}$") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 128) String password
    ) {}
    public record VerifyRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code
    ) {}

    private String verificationHash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(signingKey);
            return HexFormat.of().formatHex(mac.doFinal(
                ("pulseos-email-verification:v1:" + email + ":" + code)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute verification proof", exception);
        }
    }


    /**
     * Public configuration state only; contains no provider key, mailbox, or token.
     * Indicates credentials are present, not that delivery has been tested.
     */
    @GetMapping("/registration-status")
    public ResponseEntity<Map<String, Boolean>> registrationStatus() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(Map.of(
            "registrationEnabled", registrationEnabled,
            "emailConfigured", mailer.isConfigured()));
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        if (!registrationEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Registration is closed"));
        }
        if (!mailer.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Cache-Control", "no-store")
                .body(Map.of("error", "Verification email delivery is not configured on the server"));
        }
        String username = request.username().strip().toLowerCase(Locale.ROOT);
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        if (accounts.existsByUsername(username) || accounts.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username or email already registered"));
        }
        Instant now = Instant.now();
        pending.deleteByExpiresAtBefore(now);
        Optional<PendingRegistration> old = pending.findByEmail(email);
        if (old.isPresent() && old.get().getSentAt().isAfter(now.minusSeconds(60))) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "Wait at least 60 seconds before requesting another code"));
        }
        if (pending.existsByUsername(username) &&
            (old.isEmpty() || !old.get().getUsername().equals(username))) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username unavailable"));
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String hash = verificationHash(email, code);
        String passwordHash = passwordEncoder.encode(request.password());
        if (old.isPresent()) {
            old.get().replace(username, passwordHash, hash, now);
            pending.saveAndFlush(old.get());
        } else {
            pending.saveAndFlush(new PendingRegistration(username, email, passwordHash, hash,
                now, now.plusSeconds(600)));
        }
        // Mail failure rolls back the pending challenge; no account is activated.
        mailer.send(email, code);
        // Provider acceptance, not a delivery/read receipt. Never log codes or emails.
        log.info("Initial signup verification accepted by email provider");
        return ResponseEntity.accepted()
            .header("Cache-Control", "no-store")
            .body(Map.of("message", "Verification code sent. Check your email.",
                "expiresIn", 600));
    }

    public record ResendRequest(@NotBlank @Email @Size(max = 254) String email) {}

    @PostMapping("/resend")
    @Transactional
    public ResponseEntity<Map<String, String>> resend(@Valid @RequestBody ResendRequest request) {
        if (!registrationEnabled) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Registration is closed"));
        }
        if (!mailer.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Email delivery is not configured"));
        }
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        PendingRegistration challenge = pending.findByEmail(email).orElse(null);
        // Return identical responses for missing challenges and cooldown.
        if (challenge == null || !challenge.getExpiresAt().isAfter(now) ||
            challenge.getSentAt().isAfter(now.minusSeconds(60))) {
            return ResponseEntity.accepted().header("Cache-Control", "no-store")
                .body(Map.of("message", "If signup is pending, a new code will be sent."));
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        String hash = verificationHash(email, code);
        challenge.replace(challenge.getUsername(), challenge.getPasswordHash(), hash, now);
        pending.saveAndFlush(challenge);
        try {
            mailer.send(email, code);
            log.info("Signup resend accepted by email provider");
        } catch (ResponseStatusException exception) {
            // Keep unknown-account and delivery-error responses indistinguishable.
            // Render logs show the safe failure category, not the recipient or code.
            log.warn("Signup resend could not be accepted by email provider");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
        return ResponseEntity.accepted().header("Cache-Control", "no-store")
            .body(Map.of("message", "If signup is pending, a new code will be sent."));
    }

    @PostMapping("/verify")
    @Transactional
    public ResponseEntity<Map<String, String>> verify(@Valid @RequestBody VerifyRequest request) {
        if (!registrationEnabled)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Registration is closed"));
        String email = request.email().strip().toLowerCase(Locale.ROOT);
        PendingRegistration attempt = pending.findByEmail(email).orElse(null);
        if (attempt == null || !attempt.getExpiresAt().isAfter(Instant.now())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Code expired. Request a new one."));
        }
        if (attempt.getAttempts() >= 5) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("error", "Too many incorrect attempts. Request a new code."));
        }
        String actual = verificationHash(email, request.code());
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                attempt.getVerificationHash().getBytes(StandardCharsets.US_ASCII))) {
            attempt.failedAttempt();
            pending.saveAndFlush(attempt);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Incorrect verification code"));
        }
        if (accounts.existsByUsername(attempt.getUsername()) || accounts.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Account already registered"));
        }
        try {
            accounts.saveAndFlush(new StaffAccount(
                attempt.getUsername(), email, attempt.getPasswordHash()));
            pending.delete(attempt);
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(Map.of("message", "Email verified. Your account is ready. Please sign in."));
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Account already registered"));
        }
    }
}

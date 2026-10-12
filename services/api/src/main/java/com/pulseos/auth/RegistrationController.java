package com.pulseos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "pulseos.auth", name = "enabled", havingValue = "true")
public class RegistrationController {
    private final StaffAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final boolean registrationEnabled;
    private final String invitationCode;

    public RegistrationController(StaffAccountRepository accounts, PasswordEncoder encoder,
        @Value("${PULSEOS_REGISTRATION_ENABLED:false}") boolean registrationEnabled,
        @Value("${PULSEOS_REGISTRATION_INVITE_CODE:}") String invitationCode) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.registrationEnabled = registrationEnabled;
        this.invitationCode = invitationCode;
    }

    public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_.-]{2,39}$") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank String invitationCode
    ) {}

    @PostMapping("/register")
    public ResponseEntity<Map<String,String>> register(@Valid @RequestBody RegisterRequest request) {
        // Enrollment is opt-in and requires a high-entropy private invitation.
        if (!registrationEnabled || invitationCode.length() < 24) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Registration is closed"));
        }
        if (request.invitationCode().length() > 256 ||
            !MessageDigest.isEqual(invitationCode.getBytes(StandardCharsets.UTF_8),
                                   request.invitationCode().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid invitation code"));
        }
        String username = request.username().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (accounts.existsByUsername(username) || accounts.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username or email already in use"));
        }
        try {
            accounts.saveAndFlush(new StaffAccount(username, email, encoder.encode(request.password())));
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username or email already in use"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
            .header("Cache-Control", "no-store")
            .body(Map.of("message", "Account created. Please sign in."));
    }
}

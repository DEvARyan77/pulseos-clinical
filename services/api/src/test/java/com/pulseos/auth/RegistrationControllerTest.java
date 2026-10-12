package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationControllerTest {
    private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
    private final PendingRegistrationRepository pending = mock(PendingRegistrationRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final VerificationMailSender mailer = mock(VerificationMailSender.class);
    private final SecretKey key = new SecretKeySpec(new byte[32], "HmacSHA256");

    private RegistrationController controller(boolean enabled) {
        return new RegistrationController(accounts, pending, passwords, mailer, key, enabled);
    }

    private RegistrationController.RegisterRequest registration() {
        return new RegistrationController.RegisterRequest(
            "Doctor", "doctor@example.test", "long-sample-password");
    }

    @Test
    void closedEnrollmentDoesNotSendEmailOrCreateAccount() {
        assertThat(controller(false).register(registration()).getStatusCode())
            .isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(accounts, pending, passwords, mailer);
    }

    @Test
    void signupOnlyCreatesPendingRecordAndEmailsCode() {
        when(passwords.encode("long-sample-password")).thenReturn("BCrypt-test-hash");
        var result = controller(true).register(registration());
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(accounts, never()).saveAndFlush(any());
        ArgumentCaptor<PendingRegistration> captor =
            ArgumentCaptor.forClass(PendingRegistration.class);
        verify(pending).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("doctor");
        assertThat(captor.getValue().getEmail()).isEqualTo("doctor@example.test");
        assertThat(captor.getValue().getExpiresAt().isAfter(captor.getValue().getSentAt()))
            .isTrue();
        verify(mailer).send(eq("doctor@example.test"), argThat(code -> code.matches("[0-9]{6}")));
    }

    @Test
    void incorrectOrExpiredCodeCannotActivateAccount() {
        var row = new PendingRegistration("doctor", "doctor@example.test",
            "hashed-password", "not-the-real-hash",
            java.time.Instant.now(), java.time.Instant.now().plusSeconds(600));
        when(pending.findByEmail("doctor@example.test")).thenReturn(Optional.of(row));
        var result = controller(true).verify(new RegistrationController.VerifyRequest(
            "doctor@example.test", "123456"));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(row.getAttempts()).isEqualTo(1);
        verify(accounts, never()).saveAndFlush(any());
    }
    @Test
    void requiresEightCharactersForNewPasswords() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var valid = new RegistrationController.RegisterRequest(
                "testuser", "testuser@example.test", "12345678");
            var invalid = new RegistrationController.RegisterRequest(
                "testuser", "testuser@example.test", "1234567");

            assertThat(validator.validate(valid)).isEmpty();
            assertThat(validator.validate(invalid))
                .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                    .isEqualTo("password"));
        }
    }

}

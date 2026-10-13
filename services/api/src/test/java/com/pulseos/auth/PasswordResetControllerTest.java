package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordResetControllerTest {
    private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
    private final PasswordResetChallengeRepository challenges = mock(PasswordResetChallengeRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final VerificationMailSender mailer = mock(VerificationMailSender.class);
    private final SecretKey key = new SecretKeySpec(new byte[32], "HmacSHA256");

    private PasswordResetController controller() {
        return new PasswordResetController(accounts, challenges, encoder, mailer, key);
    }

    @Test
    void unknownEmailReturnsGenericResponseWithoutSending() {
        when(mailer.isConfigured()).thenReturn(true);
        var response = controller().forgot(new PasswordResetController.ForgotRequest("unknown@example.test"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().get("message")).contains("If that email");
        verify(mailer, never()).sendPasswordReset(anyString(), anyString());
        verify(challenges, never()).saveAndFlush(any());
    }

    @Test
    void successfulRecoveryStoresOnlyHashAndChangesPasswordOnce() {
        var staff = new StaffAccount("clinician", "member@example.test", "old-encoded");
        when(mailer.isConfigured()).thenReturn(true);
        when(accounts.findByEmail("member@example.test")).thenReturn(Optional.of(staff));
        var first = controller().forgot(new PasswordResetController.ForgotRequest("member@example.test"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        var row = ArgumentCaptor.forClass(PasswordResetChallenge.class);
        var emailedCode = ArgumentCaptor.forClass(String.class);
        verify(challenges).saveAndFlush(row.capture());
        verify(mailer).sendPasswordReset(eq("member@example.test"), emailedCode.capture());
        assertThat(emailedCode.getValue()).matches("[0-9]{6}");
        assertThat(row.getValue().getVerificationHash()).hasSize(64);
        assertThat(row.getValue().getVerificationHash()).isNotEqualTo(emailedCode.getValue());

        when(challenges.findByEmail("member@example.test")).thenReturn(Optional.of(row.getValue()));
        when(encoder.encode("newpass88")).thenReturn("new-bcrypt-value");
        var result = controller().reset(new PasswordResetController.ResetRequest(
            "member@example.test", emailedCode.getValue(), "newpass88"));

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(staff.getPasswordHash()).isEqualTo("new-bcrypt-value");
        verify(accounts).saveAndFlush(staff);
        verify(challenges).delete(row.getValue());
    }

    @Test
    void incorrectCodeIsCountedWithoutUpdatingPassword() {
        var row = new PasswordResetChallenge("member@example.test",
            "not-matching-proof", Instant.now());
        when(challenges.findByEmail("member@example.test")).thenReturn(Optional.of(row));
        var response = controller().reset(new PasswordResetController.ResetRequest(
            "member@example.test", "123456", "newpass88"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(row.getAttempts()).isEqualTo(1);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void cooldownDoesNotSendAnotherCode() {
        var staff = new StaffAccount("clinician", "member@example.test", "old-encoded");
        when(mailer.isConfigured()).thenReturn(true);
        when(accounts.findByEmail("member@example.test")).thenReturn(Optional.of(staff));
        when(challenges.findByEmail("member@example.test"))
            .thenReturn(Optional.of(new PasswordResetChallenge("member@example.test", "old-hash", Instant.now())));
        var response = controller().forgot(new PasswordResetController.ForgotRequest("member@example.test"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(mailer, never()).sendPasswordReset(anyString(), anyString());
    }
}

package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.server.ResponseStatusException;

class VerificationMailSenderTest {
    @SuppressWarnings("unchecked")
    private final ObjectProvider<JavaMailSender> smtp = mock(ObjectProvider.class);

    @Test
    void missingProviderConfigurationCannotSendCodes() {
        var sender = new VerificationMailSender("disabled", "sender@example.test",
            "", "", "", smtp);
        assertThatThrownBy(() -> sender.send("recipient@example.test", "123456"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> org.assertj.core.api.Assertions.assertThat(
                ((ResponseStatusException) error).getStatusCode()
            ).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void rejectsUnexpectedGoogleScriptHostWithoutSendingData() {
        var sender = new VerificationMailSender("google-script", "sender@example.test",
            "", "https://attacker.example.test/macros/s/1234/exec",
            "a".repeat(64), smtp);
        assertThatThrownBy(() -> sender.send("recipient@example.test", "123456"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsMissingGoogleScriptSecretWithoutSendingData() {
        var sender = new VerificationMailSender("google-script", "sender@example.test",
            "", "https://script.google.com/macros/s/1234/exec",
            "", smtp);
        assertThatThrownBy(() -> sender.send("recipient@example.test", "123456"))
            .isInstanceOf(ResponseStatusException.class);
    }
    @Test
    void configurationCheckFailsClosedForDisabledProvider() {
        var sender = new VerificationMailSender("disabled", "sender@example.test",
            "", "", "", smtp);
        org.assertj.core.api.Assertions.assertThat(sender.isConfigured()).isFalse();
    }

    @Test
    void configurationCheckRejectsMissingGoogleRelaySettings() {
        var sender = new VerificationMailSender("google-script", "sender@example.test",
            "", "https://attacker.example.test/macros/s/1234/exec",
            "a".repeat(64), smtp);
        org.assertj.core.api.Assertions.assertThat(sender.isConfigured()).isFalse();
    }

    @Test
    void smtpEmailsIncludeBrandedHtmlAndDistinctSubjects() throws Exception {
        var mail = mock(JavaMailSender.class);
        org.mockito.Mockito.when(smtp.getIfAvailable()).thenReturn(mail);

        var signup = new jakarta.mail.internet.MimeMessage(
            jakarta.mail.Session.getInstance(new java.util.Properties()));
        var reset = new jakarta.mail.internet.MimeMessage(
            jakarta.mail.Session.getInstance(new java.util.Properties()));
        org.mockito.Mockito.when(mail.createMimeMessage()).thenReturn(signup, reset);

        var sender = new VerificationMailSender("smtp", "sender@example.test", "", "", "", smtp);
        sender.send("recipient@example.test", "123456");
        sender.sendPasswordReset("recipient@example.test", "654321");

        org.assertj.core.api.Assertions.assertThat(signup.getSubject())
            .isEqualTo("Verify your PulseOS email");
        org.assertj.core.api.Assertions.assertThat(reset.getSubject())
            .isEqualTo("Reset your PulseOS password");
        org.assertj.core.api.Assertions.assertThat(signup.getContentType())
            .contains("multipart/");
        org.assertj.core.api.Assertions.assertThat(reset.getContentType())
            .contains("multipart/");
        org.mockito.Mockito.verify(mail).send(signup);
        org.mockito.Mockito.verify(mail).send(reset);
    }

}

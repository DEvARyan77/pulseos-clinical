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
}

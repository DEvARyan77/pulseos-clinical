package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RegistrationFailureHandlerTest {
    private final RegistrationFailureHandler advice = new RegistrationFailureHandler();

    @Test
    void mailFailureHasSafe503AndNoSecretDetails() {
        var response = advice.deliveryError(new ResponseStatusException(
            HttpStatus.SERVICE_UNAVAILABLE, "provider rejected secret-example-value"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "MAIL_DELIVERY_FAILED");
        assertThat(response.toString()).doesNotContain("secret-example-value");
    }

    @Test
    void databaseFailureHasDistinctSafeCode() {
        var response = advice.databaseError(new DataIntegrityViolationException(
            "SQL error: private-example-value"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry("code", "REGISTRATION_STORAGE_UNAVAILABLE");
        assertThat(response.toString()).doesNotContain("private-example-value");
    }

    @Test
    void unexpectedFailureIs500NotMislabelledAsEmailFailure() {
        var response = advice.unexpected(new IllegalStateException(
            "internal-failure-containing-private-example"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("code", "REGISTRATION_INTERNAL_ERROR");
        assertThat(response.toString()).doesNotContain("private-example");
    }
}

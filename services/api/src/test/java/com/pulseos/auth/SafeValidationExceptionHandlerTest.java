package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class SafeValidationExceptionHandlerTest {
    @Test
    void validationResponseNeverContainsTheRejectedPassword() throws NoSuchMethodException {
        Method endpoint = RegistrationController.class.getMethod(
            "register", RegistrationController.RegisterRequest.class);
        var result = new MapBindingResult(new HashMap<>(), "registerRequest");
        result.addError(new FieldError("registerRequest", "password",
            "synthetic-secret-example", false, null, null, "size invalid"));
        var exception = new MethodArgumentNotValidException(
            new MethodParameter(endpoint, 0), result);

        var response = new SafeValidationExceptionHandler().invalidFields(exception);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("error", "Password must be 8 to 128 characters.");
        assertThat(response.toString()).doesNotContain("synthetic-secret-example");
    }
}

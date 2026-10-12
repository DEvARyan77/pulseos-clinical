package com.pulseos.auth;

import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Prevent Spring MVC's default argument-validation logging from recording
 * rejected field values, which can include submitted passwords and OTPs.
 */
@RestControllerAdvice
public class SafeValidationExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> invalidFields(MethodArgumentNotValidException error) {
        boolean passwordInvalid = error.getBindingResult().getFieldErrors().stream()
            .anyMatch(field -> "password".equals(field.getField()));
        String message = passwordInvalid
            ? "Password must be 8 to 128 characters."
            : "Check the submitted fields and try again.";
        return ResponseEntity.badRequest()
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of("error", message));
    }
}

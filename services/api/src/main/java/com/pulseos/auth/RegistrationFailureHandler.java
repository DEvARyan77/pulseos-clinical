package com.pulseos.auth;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Registration diagnostics with deliberately non-sensitive logs.
 * Do not log exception messages/stack traces: those may include submitted fields,
 * provider URLs, email addresses, or challenge details.
 */
@RestControllerAdvice(assignableTypes = {RegistrationController.class, PasswordResetController.class})
@Order(Ordered.LOWEST_PRECEDENCE)
public class RegistrationFailureHandler {
    private static final Logger log = LoggerFactory.getLogger(RegistrationFailureHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> deliveryError(ResponseStatusException exception) {
        int status = exception.getStatusCode().value();
        log.warn("Registration dependency returned status={} exceptionType={}",
            status, exception.getClass().getSimpleName());
        String message = status == 503
            ? "Verification email could not be sent. Check the configured mail provider."
            : "Registration could not be completed.";
        return ResponseEntity.status(exception.getStatusCode())
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of("error", message, "code",
                status == 503 ? "MAIL_DELIVERY_FAILED" : "REGISTRATION_FAILED"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> databaseError(DataAccessException exception) {
        log.error("Registration persistence error category={}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of("error", "Registration storage is temporarily unavailable.",
                "code", "REGISTRATION_STORAGE_UNAVAILABLE"));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> unexpected(RuntimeException exception) {
        log.error("Registration unexpected server error category={}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(Map.of("error", "An internal registration error occurred.",
                "code", "REGISTRATION_INTERNAL_ERROR"));
    }
}

package com.pulseos.appointments;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Concurrent updates must not produce generic server errors or expose entity details. */
@RestControllerAdvice(assignableTypes={DemoAppointmentController.class,AppointmentCheckinController.class,
  ClinicianAvailabilityController.class})
public class AppointmentConflictHandler {
  @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
  public ResponseEntity<Map<String,String>> conflict(ObjectOptimisticLockingFailureException ignored) {
    return ResponseEntity.status(409).header("Cache-Control","no-store")
      .body(Map.of("error","This record changed in another request. Refresh before trying again."));
  }
}

package com.pulseos.appointments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/demo-appointments")
@ConditionalOnProperty(prefix = "pulseos.auth", name = "enabled", havingValue = "true")
public class DemoAppointmentController {
    private static final Set<String> DEMO_PATIENTS = Set.of(
        "DEMO-1001", "DEMO-1002", "DEMO-1003", "DEMO-1004",
        "DEMO-1005", "DEMO-1006", "DEMO-1007");

    private final DemoAppointmentRepository appointments;

    public DemoAppointmentController(DemoAppointmentRepository appointments) {
        this.appointments = appointments;
    }

    public record CreateAppointmentRequest(@NotBlank String patientId,
        @NotNull @Future Instant scheduledAt, @NotNull DemoAppointment.VisitType visitType) {}

    public record AppointmentResponse(UUID id, String patientId, Instant scheduledAt,
        DemoAppointment.VisitType visitType, DemoAppointment.Status status) {
        static AppointmentResponse from(DemoAppointment row) {
            return new AppointmentResponse(row.getId(), row.getPatientId(),
                row.getScheduledAt(), row.getVisitType(), row.getStatus());
        }
    }

    @GetMapping
    public List<AppointmentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return appointments.findByCreatedByOrderByScheduledAtAsc(jwt.getSubject()).stream()
            .map(AppointmentResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody CreateAppointmentRequest request) {
        if (!DEMO_PATIENTS.contains(request.patientId())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Only fictional DEMO patient IDs are accepted"));
        }
        if (request.scheduledAt().isAfter(Instant.now().plus(365, ChronoUnit.DAYS))) {
            return ResponseEntity.badRequest().body(Map.of("error", "Date must be within the next year"));
        }
        var row = appointments.saveAndFlush(new DemoAppointment(
            jwt.getSubject(), request.patientId(), request.scheduledAt(), request.visitType()));
        return ResponseEntity.status(HttpStatus.CREATED)
            .header("Cache-Control", "no-store").body(AppointmentResponse.from(row));
    }

    @PatchMapping("/{id}/cancel")
    @Transactional
    public ResponseEntity<?> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return appointments.findByIdAndCreatedBy(id, jwt.getSubject())
            .<ResponseEntity<?>>map(row -> {
                row.cancel();
                return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .body(AppointmentResponse.from(row));
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Appointment not found")));
    }
}

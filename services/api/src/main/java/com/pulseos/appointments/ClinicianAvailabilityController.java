package com.pulseos.appointments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * The clinician edits only their own schedule; all validation is repeated by
 * DemoAppointmentController before any appointment write.
 */
@RestController
@RequestMapping("/api/v1/demo-appointments/availability")
@ConditionalOnProperty(prefix="pulseos.auth",name="enabled",havingValue="true")
public class ClinicianAvailabilityController {
    private final ClinicianAvailabilityRepository schedules;
    private final DemoAppointmentRepository appointments;

    public ClinicianAvailabilityController(
        ClinicianAvailabilityRepository schedules, DemoAppointmentRepository appointments) {
        this.schedules = schedules;
        this.appointments = appointments;
    }

    public record AvailabilityResponse(boolean configured, String zoneId,
        List<DayOfWeek> workDays, LocalTime startTime, LocalTime endTime,
        LocalTime breakStartTime, LocalTime breakEndTime,
        List<LocalDate> blockedDates) {
        static AvailabilityResponse from(ClinicianAvailability row) {
            if (row == null) return new AvailabilityResponse(
                false, "", List.of(), LocalTime.of(9,0), LocalTime.of(18,0),
                null, null, List.of());
            return new AvailabilityResponse(true,row.getZoneId(),row.getWorkDays(),
                row.getOpensAt(),row.getClosesAt(),row.getBreakStartAt(),
                row.getBreakEndAt(),row.getBlockedDates());
        }
    }

    public record SaveAvailabilityRequest(
        @NotBlank String zoneId,
        @NotEmpty List<@NotNull DayOfWeek> workDays,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        LocalTime breakStartTime,
        LocalTime breakEndTime,
        @NotNull List<@NotNull LocalDate> blockedDates) {}

    @GetMapping
    public ResponseEntity<AvailabilityResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(AvailabilityResponse.from(schedules.findById(jwt.getSubject()).orElse(null)));
    }

    @PutMapping
    @Transactional
    public ResponseEntity<?> save(@AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody SaveAvailabilityRequest request) {
        try {
            if (!ZoneId.getAvailableZoneIds().contains(request.zoneId()))
                return bad("Select a valid IANA time zone, such as Asia/Kolkata");
            if (request.workDays().size() > 7 ||
                new HashSet<>(request.workDays()).size() != request.workDays().size())
                return bad("Select each working day only once");
            if (!request.startTime().isBefore(request.endTime()) ||
                Duration.between(request.startTime(), request.endTime()).toMinutes() < 30)
                return bad("Working hours must be at least 30 minutes, without crossing midnight");
            boolean hasBreakStart = request.breakStartTime() != null;
            if (hasBreakStart != (request.breakEndTime() != null))
                return bad("Set both break times, or leave both empty");
            if (hasBreakStart && (!request.breakStartTime().isBefore(request.breakEndTime())
                || request.breakStartTime().isBefore(request.startTime())
                || request.breakEndTime().isAfter(request.endTime())
                || Duration.between(request.breakStartTime(),request.breakEndTime()).toMinutes()<15))
                return bad("Break must be at least 15 minutes within working hours");
            if (request.blockedDates().size()>90 ||
                new HashSet<>(request.blockedDates()).size()!=request.blockedDates().size())
                return bad("Select up to 90 distinct blocked dates");
            LocalDate today = LocalDate.now(ZoneId.of(request.zoneId()));
            if (request.blockedDates().stream().anyMatch(day ->
                day.isBefore(today) || day.isAfter(today.plus(365,ChronoUnit.DAYS))))
                return bad("Blocked dates must be within the next 365 days");

            // Share the same per-clinician transaction lock used by booking/rescheduling.
            appointments.lockSchedule(jwt.getSubject());
            ClinicianAvailability row=schedules.findById(jwt.getSubject()).orElse(null);
            if(row==null){
                row=new ClinicianAvailability(jwt.getSubject(),request.zoneId(),
                    request.workDays(),request.startTime(),request.endTime(),
                    request.breakStartTime(),request.breakEndTime(),request.blockedDates());
            }else{
                row.update(request.zoneId(),request.workDays(),request.startTime(),
                    request.endTime(),request.breakStartTime(),request.breakEndTime(),
                    request.blockedDates());
            }
            schedules.saveAndFlush(row);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(AvailabilityResponse.from(row));
        } catch (DateTimeException | IllegalArgumentException error) {
            return bad("Invalid availability settings");
        }
    }

    private static ResponseEntity<?> bad(String message) {
        return ResponseEntity.badRequest().body(Map.of("error",message));
    }
}

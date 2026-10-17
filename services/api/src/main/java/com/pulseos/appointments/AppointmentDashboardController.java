package com.pulseos.appointments;

import java.sql.Timestamp;
import java.time.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Owner-scoped demo operations; no recipient addresses or clinical notes. */
@RestController
@RequestMapping("/api/v1/demo-appointments/dashboard")
@ConditionalOnProperty(prefix="pulseos.auth", name="enabled", havingValue="true")
public class AppointmentDashboardController {
  private final DemoAppointmentRepository appointments;
  private final ClinicianAvailabilityRepository availability;
  private final JdbcTemplate jdbc;
  private final boolean remindersEnabled;
  private final Clock clock;

  @Autowired
  public AppointmentDashboardController(DemoAppointmentRepository appointments,
      ClinicianAvailabilityRepository availability, JdbcTemplate jdbc,
      @Value("${PULSEOS_APPOINTMENT_REMINDERS_ENABLED:false}") boolean remindersEnabled) {
    this(appointments, availability, jdbc, remindersEnabled, Clock.systemUTC());
  }

  AppointmentDashboardController(DemoAppointmentRepository appointments,
      ClinicianAvailabilityRepository availability, JdbcTemplate jdbc,
      boolean remindersEnabled, Clock clock) {
    this.appointments=appointments; this.availability=availability;
    this.jdbc=jdbc; this.remindersEnabled=remindersEnabled; this.clock=clock;
  }

  public record ReminderCount(String status, long count) {}
  public record Dashboard(Instant generatedAt, LocalDate date, String zoneId,
      long todayVisits, long pendingCheckins, long missedAppointments,
      List<DemoAppointmentController.AppointmentResponse> visits,
      boolean remindersEnabled, Instant reminderWindowStart, List<ReminderCount> reminderCounts) {}

  @GetMapping
  public ResponseEntity<Dashboard> dashboard(@AuthenticationPrincipal Jwt jwt) {
    String owner=jwt.getSubject();
    Instant now=clock.instant();
    ZoneId zone=availability.findById(owner).map(row->ZoneId.of(row.getZoneId()))
      .orElse(ZoneId.of("UTC"));
    LocalDate today=now.atZone(zone).toLocalDate();
    // Calendar boundaries, rather than a 24-hour duration, handle DST days.
    List<DemoAppointment> visits=appointments
      .findByCreatedByAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
        owner,today.atStartOfDay(zone).toInstant(),today.plusDays(1).atStartOfDay(zone).toInstant());
    long active=visits.stream().filter(row->row.getStatus()!=DemoAppointment.Status.CANCELLED).count();
    long missed=visits.stream().filter(row->missed(row,now)).count();
    long pending=visits.stream().filter(row->row.getStatus()==DemoAppointment.Status.SCHEDULED)
      .filter(row->!missed(row,now)).count();
    Instant since=now.minus(Duration.ofHours(24));
    List<ReminderCount> counts=jdbc.query("""
      SELECT status, COUNT(*) AS total FROM appointment_reminder_attempts
       WHERE created_by = ? AND attempted_at >= ? AND attempted_at <= ?
       GROUP BY status
      """,(rs,index)->new ReminderCount(rs.getString("status"),rs.getLong("total")),
      owner,Timestamp.from(since),Timestamp.from(now));
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Dashboard(
      now,today,zone.getId(),active,pending,missed,
      visits.stream().map(DemoAppointmentController.AppointmentResponse::from).toList(),
      remindersEnabled,since,counts));
  }

  static boolean missed(DemoAppointment visit,Instant now) {
    return visit.getStatus()==DemoAppointment.Status.SCHEDULED
      && !visit.getScheduledAt().plus(Duration.ofMinutes(30)).isAfter(now);
  }
}

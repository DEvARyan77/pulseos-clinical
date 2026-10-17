package com.pulseos.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.oauth2.jwt.Jwt;

class AppointmentDashboardControllerTest {
  private final DemoAppointmentRepository appointments=mock(DemoAppointmentRepository.class);
  private final ClinicianAvailabilityRepository availability=mock(ClinicianAvailabilityRepository.class);
  private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
  private final Jwt jwt=mock(Jwt.class);
  private final Instant now=Instant.parse("2026-11-01T18:00:00Z");
  private final Clock clock=Clock.fixed(now,ZoneOffset.UTC);

  private AppointmentDashboardController controller() {
    when(jwt.getSubject()).thenReturn("only-me");
    return new AppointmentDashboardController(appointments,availability,jdbc,true,clock);
  }
  private DemoAppointment visit(Instant at) {
    return new DemoAppointment("only-me","DEMO-1001",at,DemoAppointment.VisitType.FOLLOW_UP);
  }
  @Test void countsStagesAndMissedBoundaryWithoutMutatingVisits() {
    var missed=visit(now.minusSeconds(1800));
    var pending=visit(now.minusSeconds(1799));
    var future=visit(now.plusSeconds(3600));
    var checked=visit(now.minusSeconds(3600)); checked.checkIn();
    var caring=visit(now.minusSeconds(3600)); caring.checkIn(); caring.advance();
    var completed=visit(now.minusSeconds(3600)); completed.checkIn(); completed.advance(); completed.advance();
    var cancelled=visit(now.minusSeconds(3600)); cancelled.cancel();
    when(appointments.findByCreatedByAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
      eq("only-me"),any(),any())).thenReturn(List.of(missed,pending,future,checked,caring,completed,cancelled));
    var response=controller().dashboard(jwt);
    var body=response.getBody();
    assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
    assertThat(body.todayVisits()).isEqualTo(6);
    assertThat(body.pendingCheckins()).isEqualTo(2);
    assertThat(body.missedAppointments()).isEqualTo(1);
    assertThat(body.visits()).hasSize(7);
    assertThat(missed.getStatus()).isEqualTo(DemoAppointment.Status.SCHEDULED);
    assertThat(body.zoneId()).isEqualTo("UTC");
    verify(jdbc).query(contains("WHERE created_by = ?"),any(RowMapper.class),
      eq("only-me"),eq(Timestamp.from(now.minus(Duration.ofHours(24)))),eq(Timestamp.from(now)));
    verify(appointments,never()).save(any());
  }
  @Test void clinicianTimezoneUsesHalfOpenCalendarRangeOnDstTransition() {
    when(availability.findById("only-me")).thenReturn(Optional.of(new ClinicianAvailability(
      "only-me","America/New_York",List.of(DayOfWeek.SUNDAY),LocalTime.of(9,0),
      LocalTime.of(17,0),null,null,List.of())));
    var body=controller().dashboard(jwt).getBody();
    assertThat(body.zoneId()).isEqualTo("America/New_York");
    assertThat(body.date()).isEqualTo(LocalDate.of(2026,11,1));
    verify(appointments).findByCreatedByAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
      "only-me",Instant.parse("2026-11-01T04:00:00Z"),Instant.parse("2026-11-02T05:00:00Z"));
    assertThat(body.todayVisits()).isZero();
  }
  @Test void lateCheckinClearsMissedFlagAndClosedVisitsAreExcluded() {
    var visit=visit(now.minusSeconds(7200));
    assertThat(AppointmentDashboardController.missed(visit,now)).isTrue();
    visit.checkIn();
    assertThat(AppointmentDashboardController.missed(visit,now)).isFalse();
    visit.cancel();
    assertThat(AppointmentDashboardController.missed(visit,now)).isFalse();
  }
}

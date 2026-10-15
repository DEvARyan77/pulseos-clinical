package com.pulseos.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;

class AppointmentReminderServiceTest {
  private static AppointmentReminderService service(JdbcTemplate jdbc,boolean enabled){
    return new AppointmentReminderService(jdbc,mock(DemoAppointmentRepository.class),
      mock(com.pulseos.patients.PatientRepository.class),
      mock(com.pulseos.auth.StaffAccountRepository.class),
      mock(AppointmentNoticeService.class),enabled);
  }
  @Test void remainsOptInUntilExplicitlyEnabled(){
    var jdbc=mock(JdbcTemplate.class);
    service(jdbc,false).scan();
    verifyNoInteractions(jdbc);
  }
  @Test void onlyFutureScheduledVisitsInReminderWindowQualify(){
    Instant now=Instant.now();
    var visit=new DemoAppointment("clinician","DEMO-1001",
      now.plus(Duration.ofHours(6)),DemoAppointment.VisitType.FOLLOW_UP);
    assertThat(AppointmentReminderService.due(visit,now)).isTrue();
    visit.reschedule(now.plus(Duration.ofHours(26)));
    assertThat(AppointmentReminderService.due(visit,now)).isFalse();
    visit.reschedule(now.plus(Duration.ofMinutes(40)));
    assertThat(AppointmentReminderService.due(visit,now)).isFalse();
    visit.reschedule(now.plus(Duration.ofHours(6)));
    visit.cancel();
    assertThat(AppointmentReminderService.due(visit,now)).isFalse();
  }
  @Test void historyIsAlwaysFilteredByAuthenticatedClinician(){
    var jdbc=mock(JdbcTemplate.class);var jwt=mock(Jwt.class);
    when(jwt.getSubject()).thenReturn("only-me");
    assertThat(service(jdbc,true).history(jwt).getBody().enabled()).isTrue();
    verify(jdbc).query(contains("WHERE created_by = ?"),
      any(org.springframework.jdbc.core.RowMapper.class),eq("only-me"));
  }
  @Test void noRowMeansNoSendOrDatabaseClaim(){
    var jdbc=mock(JdbcTemplate.class);
    service(jdbc,true).attempt(UUID.randomUUID(),Instant.now());
    verifyNoInteractions(jdbc);
  }
}

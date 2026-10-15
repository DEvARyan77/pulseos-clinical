package com.pulseos.appointments;

import com.pulseos.auth.StaffAccount;
import com.pulseos.auth.StaffAccountRepository;
import com.pulseos.auth.StaffRole;
import com.pulseos.patients.PatientRecord;
import com.pulseos.patients.PatientRepository;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Sends a repeat of the existing QR appointment notice within 24 hours of a
 * synthetic scheduled visit. PostgreSQL claims prevent duplicate sends across
 * replicas; ambiguous attempts are never retried automatically.
 * No recipient addresses, clinical notes or message bodies are persisted here.
 */
@RestController
@RequestMapping("/api/v1/demo-appointments/reminders")
public class AppointmentReminderService {
  private final JdbcTemplate jdbc;
  private final DemoAppointmentRepository appointments;
  private final PatientRepository patients;
  private final StaffAccountRepository accounts;
  private final AppointmentNoticeService notices;
  private final boolean enabled;

  public AppointmentReminderService(JdbcTemplate jdbc,DemoAppointmentRepository appointments,
      PatientRepository patients,StaffAccountRepository accounts,AppointmentNoticeService notices,
      @Value("${PULSEOS_APPOINTMENT_REMINDERS_ENABLED:false}") boolean enabled) {
    this.jdbc=jdbc;this.appointments=appointments;this.patients=patients;
    this.accounts=accounts;this.notices=notices;this.enabled=enabled;
  }

  public record ReminderView(UUID appointmentId,Instant scheduledAt,String status,
      Instant attemptedAt,Instant finishedAt) {}
  public record ReminderHistory(boolean enabled,List<ReminderView> attempts) {}

  @GetMapping
  public ResponseEntity<ReminderHistory> history(@AuthenticationPrincipal Jwt jwt) {
    List<ReminderView> entries=jdbc.query("""
      SELECT appointment_id,scheduled_at,status,attempted_at,finished_at
        FROM appointment_reminder_attempts WHERE created_by = ?
       ORDER BY attempted_at DESC LIMIT 50
      """,(rs,index)->new ReminderView(
        rs.getObject("appointment_id",UUID.class),
        rs.getTimestamp("scheduled_at").toInstant(),
        rs.getString("status"),
        rs.getTimestamp("attempted_at").toInstant(),
        rs.getTimestamp("finished_at")==null?null:rs.getTimestamp("finished_at").toInstant()),
      jwt.getSubject());
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
      .body(new ReminderHistory(enabled,entries));
  }

  @Scheduled(fixedDelay=300000,initialDelay=60000)
  public void scan() {
    if(!enabled)return;
    Instant now=Instant.now();
    List<UUID> due=jdbc.query("""
      SELECT a.id FROM demo_appointments a
        LEFT JOIN appointment_reminder_attempts r
          ON r.appointment_id=a.id AND r.scheduled_at=a.scheduled_at
       WHERE a.status='SCHEDULED'
         AND a.scheduled_at > ? AND a.scheduled_at <= ?
         AND r.id IS NULL
       ORDER BY a.scheduled_at ASC LIMIT 100
      """,(rs,index)->rs.getObject(1,UUID.class),
      Timestamp.from(now.plus(Duration.ofHours(1))),
      Timestamp.from(now.plus(Duration.ofHours(24))));
    for(UUID id:due){
      try{attempt(id,now);}
      catch(Exception ignored){
        // Never log email addresses, QR codes, mail content or credentials.
      }
    }
  }

  /** Claims commit before the external call. Crashes leave CLAIMED (unknown). */
  void attempt(UUID id,Instant checkedAt) {
    var existing=appointments.findById(id);
    if(existing.isEmpty()||!due(existing.get(),checkedAt))return;
    DemoAppointment visit=existing.get();
    UUID attemptId=UUID.randomUUID();
    int claimed=jdbc.update("""
      INSERT INTO appointment_reminder_attempts
        (id,appointment_id,created_by,scheduled_at,status,attempted_at)
      VALUES (?,?,?,?,'CLAIMED',?)
      ON CONFLICT (appointment_id,scheduled_at) DO NOTHING
      """,attemptId,visit.getId(),visit.getCreatedBy(),
      Timestamp.from(visit.getScheduledAt()),Timestamp.from(checkedAt));
    if(claimed!=1)return;
    String result="SKIPPED";
    try{
      var latest=appointments.findById(id);
      if(latest.isPresent() && latest.get().getScheduledAt().equals(visit.getScheduledAt())
          && due(latest.get(),Instant.now()) && isActiveClinician(visit.getCreatedBy())){
        var patient=patientFor(latest.get());
        if(patient.isPresent()&&notices.isReminderRecipientAllowed(patient.get())){
          // Existing notice already rechecks the allowlist before sending.
          result=notices.sendAppointment(latest.get(),patient.get())?"ACCEPTED":"FAILED";
        }
      }
    }catch(Exception ignored){result="FAILED";}
    finally{
      jdbc.update("""
        UPDATE appointment_reminder_attempts SET status=?,finished_at=?
         WHERE id=? AND status='CLAIMED'
        """,result,Timestamp.from(Instant.now()),attemptId);
    }
  }

  static boolean due(DemoAppointment visit,Instant now){
    return visit.getStatus()==DemoAppointment.Status.SCHEDULED
      && visit.getScheduledAt().isAfter(now.plus(Duration.ofHours(1)))
      && !visit.getScheduledAt().isAfter(now.plus(Duration.ofHours(24)));
  }
  private boolean isActiveClinician(String username){
    return accounts.findByUsername(username).filter(StaffAccount::isEnabled)
      .filter(account->StaffRole.CLINICIAN.name().equals(account.getRole())).isPresent();
  }
  private Optional<PatientRecord> patientFor(DemoAppointment visit){
    try{return patients.findByIdAndOwner(UUID.fromString(visit.getPatientId()),
      visit.getCreatedBy());}
    catch(IllegalArgumentException error){return Optional.empty();}
  }
}

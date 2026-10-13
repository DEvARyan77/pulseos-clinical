package com.pulseos.appointments;
import com.pulseos.auth.StaffAccountRepository;
import com.pulseos.auth.StaffRole;
import com.pulseos.patients.PatientRecord;
import com.pulseos.patients.PatientRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Scanning is not authorization: a verified matching patient must confirm the visit. */
@RestController @RequestMapping("/api/v1/patient-portal/appointments")
public class AppointmentCheckinController {
  private final StaffAccountRepository accounts;
  private final PatientRepository patients;
  private final DemoAppointmentRepository appointments;
  private final AppointmentNoticeService notices;
  public AppointmentCheckinController(StaffAccountRepository accounts,PatientRepository patients,
      DemoAppointmentRepository appointments,AppointmentNoticeService notices){
    this.accounts=accounts;this.patients=patients;this.appointments=appointments;this.notices=notices;
  }
  public record CheckinResponse(UUID appointmentId,DemoAppointment.Status status) {}
  @PostMapping("/{id}/check-in") @Transactional
  public ResponseEntity<CheckinResponse> checkIn(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    if(jwt==null||jwt.getSubject()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    var patientAccount=accounts.findByUsername(jwt.getSubject())
      .filter(a->a.isEnabled()&&StaffRole.PATIENT.name().equals(a.getRole()))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Patient account required"));
    var appointment=appointments.findById(id)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
    PatientRecord registered;
    try {registered=patients.findById(UUID.fromString(appointment.getPatientId()))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));}
    catch(IllegalArgumentException ignored){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found");}
    if(registered.getContactEmail()==null||
       !patientAccount.getEmail().equalsIgnoreCase(registered.getContactEmail())||
       !appointment.getCreatedBy().equals(registered.getOwner()))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found");
    Instant now=Instant.now();
    if(now.isBefore(appointment.getScheduledAt().minus(24,ChronoUnit.HOURS))||
       now.isAfter(appointment.getScheduledAt().plus(12,ChronoUnit.HOURS)))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Check-in opens 24 hours before the visit");
    if(appointment.getStatus()!=DemoAppointment.Status.SCHEDULED)
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Appointment already checked in or closed");
    appointment.checkIn();appointments.saveAndFlush(appointment);
    notices.sendAppointment(appointment,registered);
    return ResponseEntity.ok().header("Cache-Control","no-store")
      .body(new CheckinResponse(appointment.getId(),appointment.getStatus()));
  }
}

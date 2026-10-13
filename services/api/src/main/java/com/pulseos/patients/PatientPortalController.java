package com.pulseos.patients;
import com.pulseos.appointments.DemoAppointment;
import com.pulseos.appointments.DemoAppointmentRepository;
import com.pulseos.auth.StaffAccount;
import com.pulseos.auth.StaffAccountRepository;
import com.pulseos.auth.StaffRole;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** No chart/notes access: only appointment logistics matched to the verified email. */
@RestController @RequestMapping("/api/v1/patient-portal")
public class PatientPortalController {
  private final StaffAccountRepository accounts;
  private final PatientRepository patients;
  private final DemoAppointmentRepository appointments;
  public PatientPortalController(StaffAccountRepository accounts,PatientRepository patients,DemoAppointmentRepository appointments){
    this.accounts=accounts;this.patients=patients;this.appointments=appointments;
  }
  public record Visit(UUID id,Instant scheduledAt,DemoAppointment.VisitType visitType,DemoAppointment.Status status){}
  public record RegistryMatch(UUID id,String fullName,String clinicianUsername,PatientRecord.Status status,List<Visit> appointments){}
  public record Summary(String username,String email,List<RegistryMatch> records){}
  @GetMapping("/summary")
  public ResponseEntity<Summary> summary(@AuthenticationPrincipal Jwt jwt){
    if(jwt==null||jwt.getSubject()==null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required");
    StaffAccount patient=accounts.findByUsername(jwt.getSubject()).filter(StaffAccount::isEnabled)
      .filter(a->StaffRole.PATIENT.name().equals(a.getRole()))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Patient account required"));
    var items=patients.findByContactEmailOrderByUpdatedAtDesc(patient.getEmail()).stream()
      .map(row->new RegistryMatch(row.getId(),row.getFullName(),row.getOwner(),row.getStatus(),
        appointments.findByPatientIdOrderByScheduledAtAsc(row.getId().toString()).stream()
          .filter(a->a.getCreatedBy().equals(row.getOwner()))
          .map(a->new Visit(a.getId(),a.getScheduledAt(),a.getVisitType(),a.getStatus())).toList()))
      .toList();
    return ResponseEntity.ok().header("Cache-Control","no-store")
      .body(new Summary(patient.getUsername(),patient.getEmail(),items));
  }
}

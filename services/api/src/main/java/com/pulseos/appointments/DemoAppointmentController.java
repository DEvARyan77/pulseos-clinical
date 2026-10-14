package com.pulseos.appointments;

import com.pulseos.patients.PatientRepository;
import com.pulseos.patients.PatientRecord;
import org.springframework.beans.factory.annotation.Autowired;
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
  private static final List<DemoAppointment.Status> BUSY_STATUSES=List.of(
    DemoAppointment.Status.SCHEDULED,DemoAppointment.Status.CHECKED_IN,
    DemoAppointment.Status.IN_CARE);
  private static final String BOOKING_CONFLICT=
    "This clinician already has an appointment within 30 minutes of the selected time. Choose another slot.";
  private static final Set<String> LEGACY_DEMO_IDS=Set.of(
    "DEMO-1001","DEMO-1002","DEMO-1003","DEMO-1004","DEMO-1005","DEMO-1006","DEMO-1007");
  private final DemoAppointmentRepository appointments;
  private final PatientRepository patients;
  private final AppointmentNoticeService notices;
  @Autowired
  public DemoAppointmentController(DemoAppointmentRepository appointments,PatientRepository patients,AppointmentNoticeService notices){
    this.appointments=appointments;this.patients=patients;this.notices=notices;
  }
  DemoAppointmentController(DemoAppointmentRepository appointments,PatientRepository patients){
    this(appointments,patients,null);
  }
  public record CreateAppointmentRequest(@NotBlank String patientId,
     @NotNull @Future Instant scheduledAt,@NotNull DemoAppointment.VisitType visitType){}
  public record RescheduleAppointmentRequest(@NotNull @Future Instant scheduledAt){}
  public record AppointmentResponse(UUID id,String patientId,Instant scheduledAt,
     DemoAppointment.VisitType visitType,DemoAppointment.Status status){
    static AppointmentResponse from(DemoAppointment row){
      return new AppointmentResponse(row.getId(),row.getPatientId(),row.getScheduledAt(),row.getVisitType(),row.getStatus());
    }
  }
  /** Applies to all authenticated clinician writes, regardless of client-side slot hints. */
  private boolean conflicts(String owner,Instant when,UUID excludingId){
    return appointments.findByCreatedByAndStatusInAndScheduledAtAfterAndScheduledAtBefore(
      owner,BUSY_STATUSES,when.minus(30,ChronoUnit.MINUTES),
      when.plus(30,ChronoUnit.MINUTES)).stream()
      .anyMatch(visit->excludingId==null||!excludingId.equals(visit.getId()));
  }
  @GetMapping
  public List<AppointmentResponse> list(@AuthenticationPrincipal Jwt jwt){
    return appointments.findByCreatedByOrderByScheduledAtAsc(jwt.getSubject()).stream()
      .map(AppointmentResponse::from).toList();
  }
  @PostMapping
  @Transactional
  public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateAppointmentRequest request){
    boolean valid=LEGACY_DEMO_IDS.contains(request.patientId());
    PatientRecord recipient=null;
    if(!valid){
      try {
        var record=patients.findByIdAndOwner(UUID.fromString(request.patientId()),jwt.getSubject());
        valid=record.isPresent();recipient=record.orElse(null);
      }catch(IllegalArgumentException ignored){valid=false;}
    }
    if(!valid)return ResponseEntity.badRequest().body(Map.of("error","Select a patient from your own registry"));
    if(request.scheduledAt().isAfter(Instant.now().plus(365,ChronoUnit.DAYS)))
      return ResponseEntity.badRequest().body(Map.of("error","Date must be within the next year"));
    appointments.lockSchedule(jwt.getSubject());
    if(conflicts(jwt.getSubject(),request.scheduledAt(),null))
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",BOOKING_CONFLICT));
    var row=appointments.saveAndFlush(new DemoAppointment(
      jwt.getSubject(),request.patientId(),request.scheduledAt(),request.visitType()));
    if(recipient!=null&&notices!=null)notices.sendAppointment(row,recipient);
    return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control","no-store").body(AppointmentResponse.from(row));
  }
  /** Only the owning clinician can move a scheduled visit. */
  @PatchMapping("/{id}/reschedule")
  @Transactional
  public ResponseEntity<?> reschedule(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,
      @Valid @RequestBody RescheduleAppointmentRequest request){
    appointments.lockSchedule(jwt.getSubject());
    DemoAppointment row=appointments.findByIdAndCreatedBy(id,jwt.getSubject())
      .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
    if(row.getStatus()!=DemoAppointment.Status.SCHEDULED)
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Only scheduled visits can be rescheduled"));
    if(request.scheduledAt().isAfter(Instant.now().plus(365,ChronoUnit.DAYS)))
      return ResponseEntity.badRequest().body(Map.of("error","Date must be within the next year"));
    if(row.getScheduledAt().equals(request.scheduledAt()))
      return ResponseEntity.badRequest().body(Map.of("error","Select a different appointment time"));
    if(conflicts(jwt.getSubject(),request.scheduledAt(),id))
      return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",BOOKING_CONFLICT));
    row.reschedule(request.scheduledAt());
    appointments.saveAndFlush(row);
    // Refresh the appointment QR email only for an approved fictional test address.
    if(notices!=null){
      try{patients.findByIdAndOwner(UUID.fromString(row.getPatientId()),jwt.getSubject())
        .ifPresent(patient->notices.sendAppointment(row,patient));}
      catch(IllegalArgumentException ignored){}
    }
    return ResponseEntity.ok().header("Cache-Control","no-store").body(AppointmentResponse.from(row));
  }
  @PatchMapping("/{id}/advance")
  @Transactional
  public ResponseEntity<?> advance(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    DemoAppointment row=appointments.findByIdAndCreatedBy(id,jwt.getSubject())
      .orElseThrow(()->new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,"Appointment not found"));
    try{row.advance();}
    catch(IllegalStateException error){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Visit cannot advance from this status"));}
    appointments.saveAndFlush(row);
    if(notices!=null){
      try{patients.findByIdAndOwner(UUID.fromString(row.getPatientId()),jwt.getSubject())
        .ifPresent(patient->notices.sendAppointment(row,patient));}
      catch(IllegalArgumentException ignored){}
    }
    return ResponseEntity.ok().header("Cache-Control","no-store").body(AppointmentResponse.from(row));
  }
  @PatchMapping("/{id}/cancel")
  @Transactional
  public ResponseEntity<?> cancel(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    return appointments.findByIdAndCreatedBy(id,jwt.getSubject())
      .<ResponseEntity<?>>map(row->{
        try{row.cancel();}catch(IllegalStateException error){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","Cannot cancel an active visit"));}
        return ResponseEntity.ok().header("Cache-Control","no-store").body(AppointmentResponse.from(row));})
      .orElseGet(()->ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error","Appointment not found")));
  }
}

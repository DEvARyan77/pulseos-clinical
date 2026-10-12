package com.pulseos.patients;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/patients")
public class PatientController {
  private final PatientRepository patients;
  private final VisitNoteRepository notes;
  private final PatientAuditRepository audits;
  public PatientController(PatientRepository patients,VisitNoteRepository notes,PatientAuditRepository audits){
    this.patients=patients;this.notes=notes;this.audits=audits;
  }
  public record PatientInput(
    @NotBlank @Size(max=120) String fullName,
    @NotNull @Min(0) @Max(120) Integer age,
    @NotBlank @Size(max=32) String gender,
    @NotBlank @Size(max=240) String reason,
    @NotBlank @Size(max=32) String room,
    @NotNull PatientRecord.Status status,
    @NotNull @Size(max=16) String bp,
    @Min(0) @Max(250) Integer heartRate,
    @Min(0) @Max(100) Integer oxygen,
    @NotNull @Size(max=16) String temperature,
    @NotNull @Size(max=500) String allergies,
    @NotNull @Size(max=500) String diagnoses,
    @NotNull @Size(max=500) String medications
  ) {}
  public record NoteInput(@NotBlank @Size(max=4000) String content) {}
  private PatientRecord owned(UUID id,String owner){
    return patients.findByIdAndOwner(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient not found"));
  }
  @GetMapping
  public List<PatientRecord> list(@AuthenticationPrincipal Jwt jwt){
    return patients.findByOwnerOrderByUpdatedAtDesc(jwt.getSubject());
  }
  @GetMapping("/{id}")
  public PatientRecord get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    return owned(id,jwt.getSubject());
  }
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public PatientRecord create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody PatientInput data){
    PatientRecord row=new PatientRecord(jwt.getSubject());
    row.apply(data);
    row=patients.saveAndFlush(row);
    audits.save(new PatientAudit(row.getId(),jwt.getSubject(),"PATIENT_CREATED"));
    return row;
  }
  @PutMapping("/{id}")
  @Transactional
  public PatientRecord update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody PatientInput data){
    PatientRecord row=owned(id,jwt.getSubject());
    boolean changedStatus=row.getStatus()!=data.status();
    row.apply(data);
    row=patients.saveAndFlush(row);
    audits.save(new PatientAudit(id,jwt.getSubject(),changedStatus?"STATUS_UPDATED":"PATIENT_UPDATED"));
    return row;
  }
  @GetMapping("/{id}/notes")
  public List<VisitNote> listNotes(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    owned(id,jwt.getSubject());
    return notes.findByPatientIdAndOwnerOrderByCreatedAtDesc(id,jwt.getSubject());
  }
  @PostMapping("/{id}/notes")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public VisitNote addNote(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody NoteInput input){
    owned(id,jwt.getSubject());
    VisitNote note=notes.save(new VisitNote(id,jwt.getSubject(),input.content().trim()));
    audits.save(new PatientAudit(id,jwt.getSubject(),"NOTE_ADDED"));
    return note;
  }
  @GetMapping("/{id}/history")
  public List<PatientAudit> history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    owned(id,jwt.getSubject());
    return audits.findByPatientIdOrderByHappenedAtDesc(id);
  }
}

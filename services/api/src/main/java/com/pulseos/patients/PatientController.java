package com.pulseos.patients;

import jakarta.validation.Valid;
import com.pulseos.appointments.AppointmentNoticeService;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final AppointmentNoticeService notices;
  @Autowired
  public PatientController(PatientRepository patients,VisitNoteRepository notes,PatientAuditRepository audits,AppointmentNoticeService notices){
    this.patients=patients;this.notes=notes;this.audits=audits;this.notices=notices;
  }
  public PatientController(PatientRepository patients,VisitNoteRepository notes,PatientAuditRepository audits){
    this(patients,notes,audits,null);
  }
  public record PatientInput(
    @NotBlank @Size(min=2,max=120)
    @Pattern(regexp="^[\\p{L}][\\p{L}\\p{M} .'-]{1,119}$",
      message="Use a fictional name with letters and ordinary name punctuation")
    String fullName,
    @NotNull @Min(0) @Max(120) Integer age,
    @NotBlank @Size(max=32)
    @Pattern(regexp="^(Male|Female|Non-binary|Other|Prefer not to say|Unspecified)$",
      message="Select a valid gender")
    String gender,
    @NotBlank @Size(min=3,max=240)
    @Pattern(regexp="^[^<>\\p{Cntrl}]+$",message="Reason contains invalid characters")
    String reason,
    @NotBlank @Size(max=32)
    @Pattern(regexp="^[\\p{L}\\p{N}][\\p{L}\\p{N} ./#-]*$",
      message="Use letters, numbers and ordinary room separators")
    String room,
    @NotNull PatientRecord.Status status,
    @NotNull @Pattern(regexp="^(—|[0-9]{2,3}/[0-9]{2,3})$",
      message="Use systolic/diastolic (for example 120/80), or — if unknown")
    String bp,
    @Min(20) @Max(250) Integer heartRate,
    @Min(50) @Max(100) Integer oxygen,
    @NotNull
    @Pattern(regexp="^(—|[0-9]{2,3}(?:\\.[0-9])?(?:\\s?°?[CF])?)$",
      message="Use 36.8°C, 98.6°F or — if unknown")
    String temperature,
    @NotNull @Size(max=500) String allergies,
    @NotNull @Size(max=500) String diagnoses,
    @NotNull @Size(max=500) String medications,
    @Email @Size(max=254) String contactEmail
  ) {
    @AssertTrue(message="Blood pressure values must be plausible: 60–250/30–150, systolic above diastolic")
    public boolean isBloodPressureValid() {
      if (bp == null || "—".equals(bp) || !bp.matches("[0-9]{2,3}/[0-9]{2,3}")) return true;
      String[] values = bp.split("/");
      int systolic = Integer.parseInt(values[0]), diastolic = Integer.parseInt(values[1]);
      return systolic >= 60 && systolic <= 250 && diastolic >= 30 &&
          diastolic <= 150 && systolic > diastolic;
    }
    @AssertTrue(message="Temperature must be 30–45°C or 86–113°F")
    public boolean isTemperatureValid() {
      if (temperature == null || "—".equals(temperature) ||
          !temperature.matches("[0-9]{2,3}(?:\\.[0-9])?(?:\\s?°?[CF])?")) return true;
      String cleaned = temperature.strip().replace(" ", "").replace("°", "");
      boolean celsius = cleaned.endsWith("C");
      boolean fahrenheit = cleaned.endsWith("F");
      String numeric = celsius || fahrenheit ? cleaned.substring(0,cleaned.length()-1) : cleaned;
      double value = Double.parseDouble(numeric);
      if (!celsius && !fahrenheit) celsius = value < 60;
      return celsius ? value >= 30 && value <= 45 : value >= 86 && value <= 113;
    }
  }
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
    if(notices!=null)notices.sendCreated(row);
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

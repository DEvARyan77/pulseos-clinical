package com.pulseos.patients;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.UUID;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class PatientControllerTest {
  private final PatientRepository patients=mock(PatientRepository.class);
  private final VisitNoteRepository notes=mock(VisitNoteRepository.class);
  private final PatientAuditRepository audit=mock(PatientAuditRepository.class);
  private final PatientController controller=new PatientController(patients,notes,audit);
  private final Jwt staff=mock(Jwt.class);

  @Test void aUserCannotReadAnotherStaffMembersChart(){
    UUID id=UUID.randomUUID();
    when(staff.getSubject()).thenReturn("staff-b");
    when(patients.findByIdAndOwner(id,"staff-b")).thenReturn(Optional.empty());
    assertThatThrownBy(()->controller.get(staff,id))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(e->assertThat(((ResponseStatusException)e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(patients).findByIdAndOwner(id,"staff-b");
  }
  @Test void rejectsInvalidGenderButAcceptsStandardOption(){
    try(var factory=Validation.buildDefaultValidatorFactory()){
      var validator=factory.getValidator();
      var invalid=new PatientController.PatientInput("Sample Person",30,"anything","Demo visit",
        "Room 1",PatientRecord.Status.WAITING,"—",null,null,"—","","","",null);
      assertThat(validator.validate(invalid)).anySatisfy(error->
        assertThat(error.getPropertyPath().toString()).isEqualTo("gender"));
      var valid=new PatientController.PatientInput("Sample Person",30,"Non-binary","Demo visit",
        "Room 1",PatientRecord.Status.WAITING,"—",null,null,"—","","","",null);
      assertThat(validator.validate(valid)).isEmpty();
    }
  }

  @Test void rejectsMalformedVitalsAndPatientIdentifiers() {
    try (var factory=Validation.buildDefaultValidatorFactory()) {
      var validator=factory.getValidator();
      var invalid=new PatientController.PatientInput("A12345",30,"Female","Demo visit",
        "Room 1",PatientRecord.Status.WAITING,"999/999",0,10,"900 F","","","",null);
      var fields=validator.validate(invalid).stream()
        .map(violation->violation.getPropertyPath().toString())
        .collect(java.util.stream.Collectors.toSet());
      assertThat(fields).contains("fullName","bloodPressureValid","heartRate","oxygen","temperatureValid");
    }
  }

  @Test void rejectsBadFormattingAndAcceptsUnspecifiedVitals() {
    try (var factory=Validation.buildDefaultValidatorFactory()) {
      var validator=factory.getValidator();
      var invalid=new PatientController.PatientInput("Fictional User",30,"Female","Hi",
        "Room <script>",PatientRecord.Status.WAITING,"120/??",null,null,"mystery","","","",null);
      var fields=validator.validate(invalid).stream()
        .map(violation->violation.getPropertyPath().toString())
        .collect(java.util.stream.Collectors.toSet());
      assertThat(fields).contains("reason","room","bp","temperature");

      var valid=new PatientController.PatientInput("Fictional User",0,"Unspecified","New visit",
        "Room A-12",PatientRecord.Status.WAITING,"120/80",72,98,"36.8°C","","","",null);
      assertThat(validator.validate(valid)).isEmpty();
      var unknown=new PatientController.PatientInput("Demo Person",30,"Non-binary","Follow up",
        "Unassigned",PatientRecord.Status.WAITING,"—",null,null,"—","","","",null);
      assertThat(validator.validate(unknown)).isEmpty();
    }
  }
  @Test void listsOnlyOwnedPatients(){
    when(staff.getSubject()).thenReturn("staff-a");
    when(patients.findByOwnerOrderByUpdatedAtDesc("staff-a")).thenReturn(java.util.List.of());
    assertThat(controller.list(staff)).isEmpty();
    verify(patients).findByOwnerOrderByUpdatedAtDesc("staff-a");
  }
}

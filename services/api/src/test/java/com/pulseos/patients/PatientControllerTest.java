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
  @Test void listsOnlyOwnedPatients(){
    when(staff.getSubject()).thenReturn("staff-a");
    when(patients.findByOwnerOrderByUpdatedAtDesc("staff-a")).thenReturn(java.util.List.of());
    assertThat(controller.list(staff)).isEmpty();
    verify(patients).findByOwnerOrderByUpdatedAtDesc("staff-a");
  }
}

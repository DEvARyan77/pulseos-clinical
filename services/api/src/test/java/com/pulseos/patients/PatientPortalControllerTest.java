package com.pulseos.patients;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pulseos.auth.*;
import com.pulseos.appointments.DemoAppointmentRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
class PatientPortalControllerTest {
  private final StaffAccountRepository accounts=mock(StaffAccountRepository.class);
  private final PatientRepository patients=mock(PatientRepository.class);
  private final DemoAppointmentRepository appointments=mock(DemoAppointmentRepository.class);
  private final PatientPortalController controller=new PatientPortalController(accounts,patients,appointments);
  @Test void staffCannotActAsPatient(){
    Jwt jwt=mock(Jwt.class);when(jwt.getSubject()).thenReturn("doctor");
    StaffAccount account=mock(StaffAccount.class);when(account.isEnabled()).thenReturn(true);
    when(account.getRole()).thenReturn("CLINICIAN");
    when(accounts.findByUsername("doctor")).thenReturn(Optional.of(account));
    assertThatThrownBy(()->controller.summary(jwt)).isInstanceOf(ResponseStatusException.class)
      .satisfies(e->assertThat(((ResponseStatusException)e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verifyNoInteractions(patients,appointments);
  }
}

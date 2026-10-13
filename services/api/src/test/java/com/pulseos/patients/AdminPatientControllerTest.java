package com.pulseos.patients;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.pulseos.auth.StaffAccount;
import com.pulseos.auth.StaffAccountRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class AdminPatientControllerTest {
  private final StaffAccountRepository accounts=mock(StaffAccountRepository.class);
  private final PatientRepository patients=mock(PatientRepository.class);
  private final AdminPatientController controller=new AdminPatientController(accounts,patients);
  private final Jwt jwt=mock(Jwt.class);
  @Test void clinicianCannotSeeCrossAccountPatientDirectory(){
    when(jwt.getSubject()).thenReturn("clinician");
    StaffAccount staff=mock(StaffAccount.class);
    when(staff.isEnabled()).thenReturn(true);
    when(staff.getRole()).thenReturn("CLINICIAN");
    when(accounts.findByUsername("clinician")).thenReturn(Optional.of(staff));
    assertThatThrownBy(()->controller.list(jwt)).isInstanceOf(ResponseStatusException.class)
      .satisfies(error->assertThat(((ResponseStatusException)error).getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN));
    verifyNoInteractions(patients);
  }
  @Test void activeAdminCanSeeOnlyDirectorySummary(){
    when(jwt.getSubject()).thenReturn("admin");
    StaffAccount staff=mock(StaffAccount.class);
    when(staff.isEnabled()).thenReturn(true);
    when(staff.getRole()).thenReturn("ADMIN");
    when(accounts.findByUsername("admin")).thenReturn(Optional.of(staff));
    when(patients.findTop100ByOrderByUpdatedAtDesc()).thenReturn(List.of());
    assertThat(controller.list(jwt).getBody()).isEmpty();
    verify(patients).findTop100ByOrderByUpdatedAtDesc();
  }
}

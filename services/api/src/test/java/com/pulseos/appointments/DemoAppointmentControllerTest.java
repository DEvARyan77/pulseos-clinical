package com.pulseos.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.pulseos.patients.PatientRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class DemoAppointmentControllerTest {
    @Test
    void rejectsUnknownPatients() {
        var repository = mock(DemoAppointmentRepository.class);
        var patients = mock(PatientRepository.class);
        var controller = new DemoAppointmentController(repository, patients);
        var staff = mock(Jwt.class);
        when(staff.getSubject()).thenReturn("demo-staff");
        var request = new DemoAppointmentController.CreateAppointmentRequest(
            "REAL-PATIENT-42", Instant.now().plusSeconds(86400),
            DemoAppointment.VisitType.FOLLOW_UP);
        assertThat(controller.create(staff, request).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(repository);
    }

    @Test
    void appointmentsAreQueriedPerSignedInStaffAccount() {
        var repository = mock(DemoAppointmentRepository.class);
        var controller = new DemoAppointmentController(repository, mock(PatientRepository.class));
        var staff = mock(Jwt.class);
        when(staff.getSubject()).thenReturn("demo-staff");
        when(repository.findByCreatedByOrderByScheduledAtAsc("demo-staff")).thenReturn(List.of());
        assertThat(controller.list(staff)).isEmpty();
        verify(repository).findByCreatedByOrderByScheduledAtAsc("demo-staff");
    }
}

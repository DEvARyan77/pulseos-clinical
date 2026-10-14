package com.pulseos.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.pulseos.patients.PatientRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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
    void rescheduleOwnScheduledVisitUpdatesTime() {
        var repository=mock(DemoAppointmentRepository.class);
        var controller=new DemoAppointmentController(repository,mock(PatientRepository.class));
        var staff=mock(Jwt.class);
        when(staff.getSubject()).thenReturn("demo-staff");
        var id=UUID.randomUUID();
        var appointment=new DemoAppointment("demo-staff","DEMO-1001",Instant.now().plusSeconds(86400),
            DemoAppointment.VisitType.FOLLOW_UP);
        var next=Instant.now().plusSeconds(172800);
        when(repository.findByIdAndCreatedBy(id,"demo-staff")).thenReturn(Optional.of(appointment));
        when(repository.saveAndFlush(appointment)).thenReturn(appointment);
        var result=controller.reschedule(staff,id,new DemoAppointmentController.RescheduleAppointmentRequest(next));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(appointment.getScheduledAt()).isEqualTo(next);
        verify(repository).saveAndFlush(appointment);
    }

    @Test
    void rescheduleCannotChangeAnotherCliniciansOrActiveVisit() {
        var repository=mock(DemoAppointmentRepository.class);
        var controller=new DemoAppointmentController(repository,mock(PatientRepository.class));
        var staff=mock(Jwt.class);
        when(staff.getSubject()).thenReturn("other");
        var id=UUID.randomUUID();
        var next=Instant.now().plusSeconds(172800);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->controller.reschedule(staff,id,
            new DemoAppointmentController.RescheduleAppointmentRequest(next)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .satisfies(error->assertThat(((org.springframework.web.server.ResponseStatusException)error).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
        var appointment=new DemoAppointment("other","DEMO-1001",Instant.now().plusSeconds(86400),
            DemoAppointment.VisitType.FOLLOW_UP);
        appointment.checkIn();
        when(repository.findByIdAndCreatedBy(id,"other")).thenReturn(Optional.of(appointment));
        assertThat(controller.reschedule(staff,id,
            new DemoAppointmentController.RescheduleAppointmentRequest(next)).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        verify(repository,never()).saveAndFlush(any());
    }

    @Test
    void rescheduleRejectsUnchangedAndBeyondOneYear() {
        var repository=mock(DemoAppointmentRepository.class);
        var controller=new DemoAppointmentController(repository,mock(PatientRepository.class));
        var staff=mock(Jwt.class);
        when(staff.getSubject()).thenReturn("demo-staff");
        var id=UUID.randomUUID();
        var original=Instant.now().plusSeconds(86400);
        var appointment=new DemoAppointment("demo-staff","DEMO-1001",original,
            DemoAppointment.VisitType.CONSULTATION);
        when(repository.findByIdAndCreatedBy(id,"demo-staff")).thenReturn(Optional.of(appointment));
        assertThat(controller.reschedule(staff,id,
            new DemoAppointmentController.RescheduleAppointmentRequest(original)).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.reschedule(staff,id,
            new DemoAppointmentController.RescheduleAppointmentRequest(Instant.now().plusSeconds(400L*86400))).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(repository,never()).saveAndFlush(any());
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

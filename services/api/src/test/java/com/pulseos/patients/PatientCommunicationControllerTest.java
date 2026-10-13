package com.pulseos.patients;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import com.pulseos.auth.VerificationMailSender;

class PatientCommunicationControllerTest {
  private final PatientRepository patients=mock(PatientRepository.class);
  private final PatientNotificationRepository notifications=mock(PatientNotificationRepository.class);
  private final PatientAuditRepository audit=mock(PatientAuditRepository.class);
  private final VerificationMailSender mailer=mock(VerificationMailSender.class);
  private final Jwt jwt=mock(Jwt.class);
  private final UUID id=UUID.randomUUID();

  private PatientCommunicationController controller(String allowlist) {
    return new PatientCommunicationController(patients,notifications,audit,mailer,allowlist);
  }
  private void own(String email) {
    when(jwt.getSubject()).thenReturn("clinician");
    PatientRecord record=mock(PatientRecord.class);
    when(record.getContactEmail()).thenReturn(email);
    when(patients.findByIdAndOwner(id,"clinician")).thenReturn(Optional.of(record));
  }

  @Test void anotherCliniciansPatientCannotBeContacted() {
    when(jwt.getSubject()).thenReturn("other");
    assertThatThrownBy(()->controller("test@example.test")
      .notifyPatient(jwt,id,new PatientCommunicationController.NotifyRequest(
          PatientCommunicationController.Channel.EMAIL)))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(x->assertThat(((ResponseStatusException)x).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verifyNoInteractions(mailer,notifications);
  }

  @Test void emailMustBeOnTheExplicitDemoAllowlist() {
    own("real-patient@example.test");
    assertThatThrownBy(()->controller("test@example.test")
      .notifyPatient(jwt,id,new PatientCommunicationController.NotifyRequest(
          PatientCommunicationController.Channel.EMAIL)))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(x->assertThat(((ResponseStatusException)x).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    verifyNoInteractions(mailer,notifications);
  }

  @Test void allowlistedEmailUsesTheFixedTemplate() {
    own("test@example.test");
    when(mailer.isConfigured()).thenReturn(true);
    when(notifications.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
    var response=controller(" test@example.test ")
      .notifyPatient(jwt,id,new PatientCommunicationController.NotifyRequest(
          PatientCommunicationController.Channel.EMAIL));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getChannel()).isEqualTo("EMAIL");
    verify(mailer).sendPatientNotice("test@example.test");
    verify(audit).save(any(PatientAudit.class));
  }

  @Test void localNotificationNeverSendsExternalMail() {
    own(null);
    when(notifications.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
    var response=controller("")
      .notifyPatient(jwt,id,new PatientCommunicationController.NotifyRequest(
          PatientCommunicationController.Channel.IN_APP));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    verifyNoInteractions(mailer);
  }
}

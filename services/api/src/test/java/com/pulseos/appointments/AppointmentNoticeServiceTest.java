package com.pulseos.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AppointmentNoticeServiceTest {
  @Test void providerJsonRoundTripsQuotesBackslashesAndEveryControlCharacter() {
    StringBuilder text=new StringBuilder("demo \"quote\" \\ path");
    for(char c=0;c<32;c++)text.append(c);
    String value=text.toString();
    assertThat(new JsonMapper().readValue(AppointmentNoticeService.json(value),String.class)).isEqualTo(value);
  }
  @Test void relayResultRedirectCannotPostSecretsToArbitraryHosts() {
    assertThat(AppointmentNoticeService.isRelayResultUrl(
      URI.create("https://script.googleusercontent.com/macros/echo?user_content_key=demo"))).isTrue();
    for(String url:new String[]{"http://script.googleusercontent.com/macros/echo",
      "https://attacker.example/macros/echo","https://script.googleusercontent.com.attacker.example/macros/echo",
      "https://secret@script.googleusercontent.com/macros/echo",
      "https://script.googleusercontent.com:8443/macros/echo","https://script.googleusercontent.com/other"}) {
      assertThat(AppointmentNoticeService.isRelayResultUrl(URI.create(url))).isFalse();
    }
  }
  @Test void emptyAllowlistNeverCallsMailProviders() {
    var smtp=org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
    var generic=org.mockito.Mockito.mock(com.pulseos.auth.VerificationMailSender.class);
    var patient=org.mockito.Mockito.mock(com.pulseos.patients.PatientRecord.class);
    org.mockito.Mockito.when(patient.getContactEmail()).thenReturn("blocked@example.test");
    var service=new AppointmentNoticeService("https://demo.example","smtp","sender@example.test",
      "","","","",smtp,generic);
    assertThat(service.isReminderRecipientAllowed(patient)).isFalse();
    assertThat(service.sendAppointment(null,patient)).isFalse();
    assertThat(service.sendCreated(patient)).isFalse();
    org.mockito.Mockito.verifyNoInteractions(smtp,generic);
  }
  @Test void reminderEligibilityRequiresExactAllowlistedAddress() {
    var service=new AppointmentNoticeService("","disabled","","","","",
      " approved@example.test ",org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class),
      org.mockito.Mockito.mock(com.pulseos.auth.VerificationMailSender.class));
    var patient=org.mockito.Mockito.mock(com.pulseos.patients.PatientRecord.class);
    org.mockito.Mockito.when(patient.getContactEmail()).thenReturn("APPROVED@example.test");
    assertThat(service.isReminderRecipientAllowed(patient)).isTrue();
    org.mockito.Mockito.when(patient.getContactEmail()).thenReturn("approved@example.test.attacker.example");
    assertThat(service.isReminderRecipientAllowed(patient)).isFalse();
  }

}

package com.pulseos.auth;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class StaffChatControllerTest {
  private final StaffAccountRepository accounts=mock(StaffAccountRepository.class);
  private final StaffMessageRepository messages=mock(StaffMessageRepository.class);
  private final StaffChatController controller=new StaffChatController(accounts,messages);
  private final Jwt jwt=mock(Jwt.class);

  private void authenticate() {
    when(jwt.getSubject()).thenReturn("doctor");
    var staff=mock(StaffAccount.class);
    when(staff.isEnabled()).thenReturn(true);
    when(staff.getRole()).thenReturn("CLINICIAN");
    when(staff.getUsername()).thenReturn("doctor");
    when(accounts.findByUsername("doctor")).thenReturn(Optional.of(staff));
  }
  @Test void disabledStaffCannotAccessMessages() {
    when(jwt.getSubject()).thenReturn("disabled");
    var staff=mock(StaffAccount.class);
    when(staff.isEnabled()).thenReturn(false);
    when(accounts.findByUsername("disabled")).thenReturn(Optional.of(staff));
    assertThatThrownBy(()->controller.inbox(jwt)).isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(messages);
  }
  @Test void cannotSendToUnknownOrDisabledStaff() {
    authenticate();
    assertThatThrownBy(()->controller.send(jwt,new StaffChatController.SendRequest("nobody","Hello")))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(x->assertThat(((ResponseStatusException)x).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    verifyNoInteractions(messages);
  }
  @Test void canOnlyMarkMessagesAddressedToSelf() {
    authenticate();
    var id=UUID.randomUUID();
    when(messages.findByIdAndRecipient(id,"doctor")).thenReturn(Optional.empty());
    assertThatThrownBy(()->controller.markRead(jwt,id))
      .isInstanceOf(ResponseStatusException.class)
      .satisfies(x->assertThat(((ResponseStatusException)x).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(messages,never()).saveAndFlush(any());
  }
  @Test void sendsMessageToVerifiedActiveColleague() {
    authenticate();
    var other=mock(StaffAccount.class);
    when(other.isEnabled()).thenReturn(true);
    when(other.getRole()).thenReturn("ADMIN");
    when(accounts.findByUsername("supervisor")).thenReturn(Optional.of(other));
    when(messages.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
    var response=controller.send(jwt,new StaffChatController.SendRequest("SUPERVISOR","Hello there"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody().getSender()).isEqualTo("doctor");
    assertThat(response.getBody().getRecipient()).isEqualTo("supervisor");
  }
}

package com.pulseos.auth;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
class StaffChatImageTest {
  private final StaffAccountRepository accounts=mock(StaffAccountRepository.class);
  private final StaffMessageRepository messages=mock(StaffMessageRepository.class);
  private final StaffChatController controller=new StaffChatController(accounts,messages);
  @Test void cannotDownloadOtherPeoplesAttachments(){
    Jwt jwt=mock(Jwt.class);when(jwt.getSubject()).thenReturn("doctor");
    StaffAccount user=mock(StaffAccount.class);
    when(user.getUsername()).thenReturn("doctor");when(user.isEnabled()).thenReturn(true);
    when(accounts.findByUsername("doctor")).thenReturn(Optional.of(user));
    UUID id=UUID.randomUUID();
    StaffMessage item=new StaffMessage("someone","other","demo");
    item.attachImage(new byte[]{(byte)255,(byte)216,(byte)255,0},"image/jpeg");
    when(messages.findById(id)).thenReturn(Optional.of(item));
    assertThatThrownBy(()->controller.image(jwt,id)).isInstanceOf(ResponseStatusException.class)
      .satisfies(error->assertThat(((ResponseStatusException)error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}

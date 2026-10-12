package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class RegistrationControllerTest {
    private static final String INVITE = "a-long-demo-only-invite-code-123456";

    @Test
    void refusesToRegisterWithoutAnInvitation() {
        StaffAccountRepository accounts = mock(StaffAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        RegistrationController controller = new RegistrationController(accounts, encoder, true, INVITE);
        var request = new RegistrationController.RegisterRequest(
            "clinician", "clinician@example.test", "long-demo-password", "invalid");
        assertThat(controller.register(request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(accounts);
    }

    @Test
    void closedEnrollmentCannotCreateUsers() {
        StaffAccountRepository accounts = mock(StaffAccountRepository.class);
        RegistrationController controller = new RegistrationController(
            accounts, mock(PasswordEncoder.class), false, INVITE);
        var request = new RegistrationController.RegisterRequest(
            "clinician", "clinician@example.test", "long-demo-password", INVITE);
        assertThat(controller.register(request).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(accounts);
    }

    @Test
    void invitedClinicianIsHashedAndPersisted() {
        StaffAccountRepository accounts = mock(StaffAccountRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("long-demo-password")).thenReturn("encoded-hash");
        when(accounts.saveAndFlush(any(StaffAccount.class))).thenAnswer(x -> x.getArgument(0));
        var controller = new RegistrationController(accounts, encoder, true, INVITE);
        var response = controller.register(new RegistrationController.RegisterRequest(
            "Clinician", "CLINICIAN@example.test", "long-demo-password", INVITE));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(accounts).saveAndFlush(any(StaffAccount.class));
        verify(encoder).encode("long-demo-password");
    }
}

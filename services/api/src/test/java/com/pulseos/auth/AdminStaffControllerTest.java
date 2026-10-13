package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class AdminStaffControllerTest {
    private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
    private final AdminStaffController controller = new AdminStaffController(accounts);

    private Jwt jwt() {
        return Jwt.withTokenValue("admin-session")
            .header("alg", "HS256").subject("owner")
            .claim("roles", List.of("ADMIN")).build();
    }

    private StaffAccount actor(UUID id, String role) {
        var account = mock(StaffAccount.class);
        when(account.getId()).thenReturn(id);
        when(account.getRole()).thenReturn(role);
        when(account.isEnabled()).thenReturn(true);
        when(accounts.findByUsername("owner")).thenReturn(Optional.of(account));
        return account;
    }

    @Test
    void clinicianCannotListOrPromoteStaffEvenWithAdminJwtClaim() {
        actor(UUID.randomUUID(), "CLINICIAN");
        assertThatThrownBy(() -> controller.list(jwt()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
        verify(accounts, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void adminCanPromoteAnotherStaffMember() {
        actor(UUID.randomUUID(), "ADMIN");
        var otherId = UUID.randomUUID();
        var target = mock(StaffAccount.class);
        when(target.getId()).thenReturn(otherId);
        when(accounts.findById(otherId)).thenReturn(Optional.of(target));
        var result = controller.updateRole(jwt(), otherId,
            new AdminStaffController.RoleChange(StaffRole.ADMIN));
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(target).changeRole(StaffRole.ADMIN);
        verify(accounts).saveAndFlush(target);
    }

    @Test
    void adminCannotDemoteOrDisableSelf() {
        var id = UUID.randomUUID();
        var account = actor(id, "ADMIN");
        when(accounts.findById(id)).thenReturn(Optional.of(account));
        assertThat(controller.updateRole(jwt(), id,
            new AdminStaffController.RoleChange(StaffRole.CLINICIAN)).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        assertThat(controller.updateStatus(jwt(), id,
            new AdminStaffController.StatusChange(false)).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
        verify(accounts, never()).saveAndFlush(any());
    }
}

package com.pulseos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class InitialAdminBootstrapTest {
    private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);

    @Test
    void noConfigurationDoesNotReadOrMutateAccounts() {
        new InitialAdminBootstrap(accounts, "").run(null);
        verifyNoInteractions(accounts);
    }

    @Test
    void promotesOnlyAnEnabledExistingAccount() {
        StaffAccount member = new StaffAccount("clinician", "member@example.test", "test-hash");
        when(accounts.findByEmail("member@example.test")).thenReturn(Optional.of(member));
        new InitialAdminBootstrap(accounts, " MEMBER@EXAMPLE.TEST ").run(null);
        assertThat(member.getRole()).isEqualTo("ADMIN");
        verify(accounts).saveAndFlush(member);
    }

    @Test
    void neverOverridesAnExistingAdmin() {
        when(accounts.existsByRole("ADMIN")).thenReturn(true);
        new InitialAdminBootstrap(accounts, "member@example.test").run(null);
        verify(accounts, never()).findByEmail(anyString());
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void neverElevatesMissingAccount() {
        when(accounts.findByEmail("missing@example.test")).thenReturn(Optional.empty());
        new InitialAdminBootstrap(accounts, "missing@example.test").run(null);
        verify(accounts, never()).saveAndFlush(any());
    }

    @Test
    void disabledAccountsCannotBeBootstrapped() {
        StaffAccount member = mock(StaffAccount.class);
        when(member.isEnabled()).thenReturn(false);
        when(accounts.findByEmail("disabled@example.test")).thenReturn(Optional.of(member));
        new InitialAdminBootstrap(accounts, "disabled@example.test").run(null);
        verify(member, never()).changeRole(any());
        verify(accounts, never()).saveAndFlush(any());
    }
}

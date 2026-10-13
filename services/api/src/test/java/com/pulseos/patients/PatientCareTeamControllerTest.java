package com.pulseos.patients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.pulseos.auth.StaffAccount;
import com.pulseos.auth.StaffAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class PatientCareTeamControllerTest {
    private final PatientRepository patients = mock(PatientRepository.class);
    private final PatientCareTeamRepository teams = mock(PatientCareTeamRepository.class);
    private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
    private final PatientAuditRepository audits = mock(PatientAuditRepository.class);
    private final PatientCareTeamController controller =
        new PatientCareTeamController(patients, teams, accounts, audits);
    private final Jwt jwt = mock(Jwt.class);
    private final UUID id = UUID.randomUUID();

    private void owned() {
        when(jwt.getSubject()).thenReturn("owner");
        var patient = mock(PatientRecord.class);
        when(patient.getId()).thenReturn(id);
        when(patients.findByIdAndOwner(id, "owner")).thenReturn(Optional.of(patient));
    }

    private void member(String username, String role, boolean enabled) {
        var account = mock(StaffAccount.class);
        when(account.getUsername()).thenReturn(username);
        when(account.getRole()).thenReturn(role);
        when(account.isEnabled()).thenReturn(enabled);
        when(accounts.findByUsername(username)).thenReturn(Optional.of(account));
    }

    @Test
    void onlyTheOwnerCanReadOrAssignTheCareTeam() {
        when(jwt.getSubject()).thenReturn("other");
        var input = new PatientCareTeamController.AssignmentInput("clinician", "supervisor");
        assertThatThrownBy(() -> controller.get(jwt, id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(t -> assertThat(((ResponseStatusException)t).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> controller.assign(jwt, id, input))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(teams, accounts, audits);
    }

    @Test
    void rejectsDisabledOrIncorrectRoleMembersWithoutWriting() {
        owned();
        member("inactive", "CLINICIAN", false);
        member("notadmin", "CLINICIAN", true);
        var badClinician = new PatientCareTeamController.AssignmentInput("inactive", null);
        var badSupervisor = new PatientCareTeamController.AssignmentInput(null, "notadmin");
        assertThatThrownBy(() -> controller.assign(jwt, id, badClinician))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.assign(jwt, id, badSupervisor))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(teams, audits);
    }

    @Test
    void assignsVerifiedClinicianAndSupervisorAndWritesAudit() {
        owned();
        member("clinician", "CLINICIAN", true);
        member("supervisor", "ADMIN", true);
        when(teams.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var input = new PatientCareTeamController.AssignmentInput(" CLINICIAN ", "SUPERVISOR");
        var response = controller.assign(jwt, id, input);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().clinicianUsername()).isEqualTo("clinician");
        assertThat(response.getBody().supervisorUsername()).isEqualTo("supervisor");
        verify(teams).saveAndFlush(any(PatientCareTeam.class));
        verify(audits).save(any(PatientAudit.class));
    }

    @Test
    void emptyAssignmentsAreAllowedAndDoNotGrantChartAccess() {
        owned();
        when(teams.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        var response = controller.assign(jwt, id,
            new PatientCareTeamController.AssignmentInput(null, "  "));
        assertThat(response.getBody().clinicianUsername()).isNull();
        assertThat(response.getBody().supervisorUsername()).isNull();
        verifyNoInteractions(accounts);
        verify(teams).saveAndFlush(any(PatientCareTeam.class));
    }

    @Test
    void unassignedCareTeamLoadsAsEmptyWithoutWriting() {
        owned();
        var response = controller.get(jwt, id);
        assertThat(response.getBody().patientId()).isEqualTo(id);
        assertThat(response.getBody().clinicianUsername()).isNull();
        verify(teams, never()).saveAndFlush(any());
    }
}

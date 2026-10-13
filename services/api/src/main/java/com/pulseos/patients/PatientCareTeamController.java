package com.pulseos.patients;

import com.pulseos.auth.StaffAccount;
import com.pulseos.auth.StaffAccountRepository;
import com.pulseos.auth.StaffRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Care-team coordination only. Being assigned never confers access to a
 * patient's chart, visit notes or audit history. All operations require owner.
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PatientCareTeamController {
    private final PatientRepository patients;
    private final PatientCareTeamRepository teams;
    private final StaffAccountRepository accounts;
    private final PatientAuditRepository audits;

    public PatientCareTeamController(PatientRepository patients,
                                     PatientCareTeamRepository teams,
                                     StaffAccountRepository accounts,
                                     PatientAuditRepository audits) {
        this.patients = patients;
        this.teams = teams;
        this.accounts = accounts;
        this.audits = audits;
    }

    public record Member(String username, String role) {}
    public record AssignmentInput(
        @Size(max = 80) String clinicianUsername,
        @Size(max = 80) String supervisorUsername
    ) {}
    public record AssignmentView(UUID patientId, String clinicianUsername,
                                 String supervisorUsername, Instant updatedAt) {}

    private PatientRecord owned(UUID id, Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return patients.findByIdAndOwner(id, jwt.getSubject())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
    }

    private static String normalize(String username) {
        if (username == null || username.isBlank()) return null;
        return username.strip().toLowerCase(Locale.ROOT);
    }

    private String verifiedRole(String username, StaffRole role) {
        if (username == null) return null;
        StaffAccount member = accounts.findByUsername(username)
            .filter(StaffAccount::isEnabled)
            .filter(account -> role.name().equals(account.getRole()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Select an active staff member with the required role"));
        return member.getUsername();
    }

    @GetMapping("/care-team/contacts")
    public ResponseEntity<List<Member>> contacts(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .body(accounts.findAllByOrderByCreatedAtDesc().stream()
                .filter(StaffAccount::isEnabled)
                .filter(a -> StaffRole.ADMIN.name().equals(a.getRole()) ||
                             StaffRole.CLINICIAN.name().equals(a.getRole()))
                .map(a -> new Member(a.getUsername(), a.getRole())).toList());
    }

    @GetMapping("/{id}/care-team")
    public ResponseEntity<AssignmentView> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        owned(id, jwt);
        var view = teams.findById(id)
            .map(t -> new AssignmentView(id, t.getClinicianUsername(),
                 t.getSupervisorUsername(), t.getUpdatedAt()))
            .orElseGet(() -> new AssignmentView(id, null, null, null));
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(view);
    }

    @PutMapping("/{id}/care-team")
    @Transactional
    public ResponseEntity<AssignmentView> assign(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                                  @Valid @RequestBody AssignmentInput input) {
        PatientRecord patient = owned(id, jwt);
        String clinician = verifiedRole(normalize(input.clinicianUsername()), StaffRole.CLINICIAN);
        String supervisor = verifiedRole(normalize(input.supervisorUsername()), StaffRole.ADMIN);
        PatientCareTeam team = teams.findById(id).orElseGet(() ->
            new PatientCareTeam(patient.getId(), null, null));
        team.change(clinician, supervisor);
        team = teams.saveAndFlush(team);
        audits.save(new PatientAudit(id, jwt.getSubject(), "CARE_TEAM_UPDATED"));
        return ResponseEntity.ok().header("Cache-Control", "no-store")
            .body(new AssignmentView(id, team.getClinicianUsername(),
                team.getSupervisorUsername(), team.getUpdatedAt()));
    }
}

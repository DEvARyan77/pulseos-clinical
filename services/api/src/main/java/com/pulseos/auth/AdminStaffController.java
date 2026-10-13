package com.pulseos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/staff")
@ConditionalOnProperty(prefix = "pulseos.auth", name = "enabled", havingValue = "true")
public class AdminStaffController {
    private final StaffAccountRepository accounts;

    public AdminStaffController(StaffAccountRepository accounts) {
        this.accounts = accounts;
    }

    public record StaffSummary(UUID id, String username, String email, String role,
                               boolean enabled, Instant createdAt) {
        static StaffSummary of(StaffAccount account) {
            return new StaffSummary(account.getId(), account.getUsername(), account.getEmail(),
                account.getRole(), account.isEnabled(), account.getCreatedAt());
        }
    }

    public record RoleChange(@NotNull StaffRole role) {}
    public record StatusChange(@NotNull Boolean enabled) {}

    private StaffAccount actingAdmin(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in required");
        return accounts.findByUsername(jwt.getSubject())
            .filter(StaffAccount::isEnabled)
            .filter(account -> StaffRole.ADMIN.name().equals(account.getRole()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Administrator permission is required"));
    }

    @GetMapping
    public ResponseEntity<List<StaffSummary>> list(@AuthenticationPrincipal Jwt jwt) {
        actingAdmin(jwt);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(
            accounts.findAllByOrderByCreatedAtDesc().stream().map(StaffSummary::of).toList());
    }

    @PatchMapping("/{id}/role")
    @Transactional
    public ResponseEntity<?> updateRole(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                        @Valid @RequestBody RoleChange request) {
        StaffAccount actor = actingAdmin(jwt);
        StaffAccount target = accounts.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
        if (actor.getId().equals(id) && request.role() != StaffRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You cannot remove your own administrator role."));
        }
        target.changeRole(request.role());
        accounts.saveAndFlush(target);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(StaffSummary.of(target));
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public ResponseEntity<?> updateStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                          @Valid @RequestBody StatusChange request) {
        StaffAccount actor = actingAdmin(jwt);
        StaffAccount target = accounts.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
        if (actor.getId().equals(id) && !request.enabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "You cannot deactivate your own account."));
        }
        target.setEnabled(request.enabled());
        accounts.saveAndFlush(target);
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(StaffSummary.of(target));
    }
}

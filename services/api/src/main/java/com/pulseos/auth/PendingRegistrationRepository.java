package com.pulseos.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, UUID> {
    Optional<PendingRegistration> findByEmail(String email);
    boolean existsByUsername(String username);
    long deleteByExpiresAtBefore(java.time.Instant now);
}

package com.pulseos.auth;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Privileged, opt-in initial role setup from the Render environment.
 * No public API or first-signup admin promotion. Clear the environment
 * variable immediately after a successful bootstrap.
 */
@Component
@ConditionalOnProperty(prefix = "pulseos.auth", name = "enabled", havingValue = "true")
public class InitialAdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);
    private final StaffAccountRepository accounts;
    private final String email;

    public InitialAdminBootstrap(
        StaffAccountRepository accounts,
        @Value("${PULSEOS_BOOTSTRAP_ADMIN_EMAIL:}") String email) {
        this.accounts = accounts;
        this.email = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (email.isBlank()) return;

        // No further elevation on subsequent startups once the first admin exists.
        if (accounts.existsByRole(StaffRole.ADMIN.name())) {
            log.info("Admin bootstrap skipped: an administrator already exists");
            return;
        }

        var target = accounts.findByEmail(email);
        if (target.isEmpty() || !target.get().isEnabled()) {
            log.warn("Admin bootstrap skipped: target verified account not found or disabled");
            return;
        }

        target.get().changeRole(StaffRole.ADMIN);
        accounts.saveAndFlush(target.get());
        // Never print email addresses, credentials, or identifying account data.
        log.info("Admin bootstrap completed for one existing verified account");
    }
}

package com.pulseos.appointments;

import org.springframework.data.jpa.repository.JpaRepository;

/** Stored by account identity so clinicians cannot modify one another's rules. */
public interface ClinicianAvailabilityRepository
    extends JpaRepository<ClinicianAvailability, String> {}

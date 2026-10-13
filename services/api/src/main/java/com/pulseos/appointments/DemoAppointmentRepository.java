package com.pulseos.appointments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoAppointmentRepository extends JpaRepository<DemoAppointment, UUID> {
    List<DemoAppointment> findByCreatedByOrderByScheduledAtAsc(String createdBy);
    List<DemoAppointment> findByPatientIdOrderByScheduledAtAsc(String patientId);
    Optional<DemoAppointment> findByIdAndCreatedBy(UUID id, String createdBy);
}

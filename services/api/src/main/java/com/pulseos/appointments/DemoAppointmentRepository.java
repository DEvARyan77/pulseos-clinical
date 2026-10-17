package com.pulseos.appointments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface DemoAppointmentRepository extends JpaRepository<DemoAppointment, UUID> {
    List<DemoAppointment> findByCreatedByOrderByScheduledAtAsc(String createdBy);
    List<DemoAppointment> findByPatientIdOrderByScheduledAtAsc(String patientId);
    Optional<DemoAppointment> findByIdAndCreatedBy(UUID id, String createdBy);

    List<DemoAppointment> findByCreatedByAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
        String createdBy, Instant start, Instant end);

    // Serialize writes to the same clinician's calendar within a database transaction.
    // Prevent simultaneous create/reschedule requests from bypassing conflict checks.
    // Cast the void-returning PostgreSQL lock function to a boolean expression
    // so JDBC/Hibernate can map the result without an unknown SQL type.
    @Query(value="SELECT pg_advisory_xact_lock(hashtext(:owner)) IS NULL",nativeQuery=true)
    Boolean lockSchedule(@Param("owner") String owner);

    List<DemoAppointment> findByCreatedByAndStatusInAndScheduledAtAfterAndScheduledAtBefore(
        String createdBy, List<DemoAppointment.Status> statuses, Instant after, Instant before);
}

package com.pulseos.patients;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PatientAuditRepository extends JpaRepository<PatientAudit,UUID>{
 List<PatientAudit> findByPatientIdOrderByHappenedAtDesc(UUID patientId);
}

package com.pulseos.patients;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PatientRepository extends JpaRepository<PatientRecord, UUID> {
  List<PatientRecord> findByOwnerOrderByUpdatedAtDesc(String owner);
  List<PatientRecord> findByContactEmailOrderByUpdatedAtDesc(String contactEmail);
  List<PatientRecord> findTop100ByOrderByUpdatedAtDesc();
  Optional<PatientRecord> findByIdAndOwner(UUID id, String owner);
}

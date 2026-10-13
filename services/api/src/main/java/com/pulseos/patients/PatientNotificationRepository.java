package com.pulseos.patients;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PatientNotificationRepository extends JpaRepository<PatientNotification, UUID> {
  List<PatientNotification> findTop50ByPatientIdAndOwnerOrderByCreatedAtDesc(UUID patientId,String owner);
}

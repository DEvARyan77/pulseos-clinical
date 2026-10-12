package com.pulseos.patients;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface VisitNoteRepository extends JpaRepository<VisitNote,UUID>{
 List<VisitNote> findByPatientIdAndOwnerOrderByCreatedAtDesc(UUID patientId,String owner);
}

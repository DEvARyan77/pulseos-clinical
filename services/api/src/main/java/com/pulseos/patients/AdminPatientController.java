package com.pulseos.patients;

import com.pulseos.auth.StaffAccount;
import com.pulseos.auth.StaffAccountRepository;
import com.pulseos.auth.StaffRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Minimal, read-only synthetic patient directory restricted to active admins. */
@RestController
@RequestMapping("/api/v1/admin/patients")
@ConditionalOnProperty(prefix="pulseos.auth",name="enabled",havingValue="true")
public class AdminPatientController {
  private final StaffAccountRepository accounts;
  private final PatientRepository patients;
  public AdminPatientController(StaffAccountRepository accounts,PatientRepository patients){
    this.accounts=accounts;this.patients=patients;
  }
  public record Summary(UUID id,String fullName,int age,String gender,String reason,
                        String room,PatientRecord.Status status,String owner,Instant updatedAt){
    static Summary of(PatientRecord patient){
      return new Summary(patient.getId(),patient.getFullName(),patient.getAge(),patient.getGender(),
        patient.getReason(),patient.getRoom(),patient.getStatus(),patient.getOwner(),patient.getUpdatedAt());
    }
  }
  @GetMapping
  public ResponseEntity<List<Summary>> list(@AuthenticationPrincipal Jwt jwt){
    if(jwt==null||jwt.getSubject()==null)
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Sign in required");
    accounts.findByUsername(jwt.getSubject()).filter(StaffAccount::isEnabled)
      .filter(a->StaffRole.ADMIN.name().equals(a.getRole()))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Administrator access required"));
    return ResponseEntity.ok().header("Cache-Control","no-store")
      .body(patients.findTop100ByOrderByUpdatedAtDesc().stream().map(Summary::of).toList());
  }
}

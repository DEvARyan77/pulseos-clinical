package com.pulseos.patients;

import com.pulseos.auth.VerificationMailSender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/patients/{id}/notifications")
public class PatientCommunicationController {
  public enum Channel { IN_APP, EMAIL }
  public record NotifyRequest(@NotNull Channel channel){}
  private static final String SAFE_MESSAGE =
    "You have a new notification from the PulseOS fictional-data demonstration.";
  private final PatientRepository patients;
  private final PatientNotificationRepository notifications;
  private final PatientAuditRepository audit;
  private final VerificationMailSender mailer;
  private final Set<String> allowed;

  public PatientCommunicationController(PatientRepository patients, PatientNotificationRepository notifications,
      PatientAuditRepository audit, VerificationMailSender mailer,
      @Value("${PULSEOS_DEMO_EMAIL_ALLOWLIST:}") String emails){
    this.patients=patients;this.notifications=notifications;this.audit=audit;this.mailer=mailer;
    this.allowed=Arrays.stream(emails.split(",")).map(s->s.strip().toLowerCase(Locale.ROOT))
      .filter(s->!s.isBlank()).collect(Collectors.toUnmodifiableSet());
  }
  private PatientRecord owned(UUID id,Jwt jwt){
    if(jwt==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return patients.findByIdAndOwner(id,jwt.getSubject())
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Patient record not found"));
  }
  @GetMapping
  public List<PatientNotification> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    owned(id,jwt);
    return notifications.findTop50ByPatientIdAndOwnerOrderByCreatedAtDesc(id,jwt.getSubject());
  }
  @PostMapping
  @Transactional
  public ResponseEntity<PatientNotification> notifyPatient(@AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,@Valid @RequestBody NotifyRequest request){
    PatientRecord patient=owned(id,jwt);
    if(request.channel()==Channel.EMAIL){
      String destination=patient.getContactEmail();
      if(destination==null || !allowed.contains(destination.strip().toLowerCase(Locale.ROOT)))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Patient demo email must be configured and explicitly allowlisted");
      if(!mailer.isConfigured())
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Email service unavailable");
      // Fixed content only: no name, diagnosis, notes, reason, room or other clinical details.
      mailer.sendPatientNotice(destination);
    }
    PatientNotification item=notifications.saveAndFlush(new PatientNotification(
      id,jwt.getSubject(),request.channel().name(),
      request.channel()==Channel.EMAIL?"SENT":"CREATED",SAFE_MESSAGE));
    audit.save(new PatientAudit(id,jwt.getSubject(),
      request.channel()==Channel.EMAIL?"DEMO_EMAIL_SENT":"NOTIFICATION_CREATED"));
    return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control","no-store").body(item);
  }
}

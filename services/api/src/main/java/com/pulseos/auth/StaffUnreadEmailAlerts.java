package com.pulseos.auth;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Sends generic, non-clinical alerts only for messages that remain unread. */
@Component
public class StaffUnreadEmailAlerts {
  private final StaffMessageRepository messages;
  private final StaffAccountRepository accounts;
  private final VerificationMailSender mailer;
  private final boolean enabled;
  public StaffUnreadEmailAlerts(StaffMessageRepository messages,StaffAccountRepository accounts,
      VerificationMailSender mailer,@Value("${PULSEOS_STAFF_CHAT_EMAIL_ALERTS:true}") boolean enabled){
    this.messages=messages;this.accounts=accounts;this.mailer=mailer;this.enabled=enabled;
  }
  @Scheduled(fixedDelay=300000,initialDelay=60000)
  @Transactional
  public void notifyUnread(){
    if(!enabled||!mailer.isConfigured())return;
    var unread=messages.findTop100ByReadAtIsNullAndNotifiedAtIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
      Instant.now().minus(10,ChronoUnit.MINUTES));
    Map<String,List<StaffMessage>> byRecipient=new LinkedHashMap<>();
    for(StaffMessage item:unread)
      byRecipient.computeIfAbsent(item.getRecipient(),ignored->new ArrayList<>()).add(item);
    for(var recipient:byRecipient.entrySet()){
      var account=accounts.findByUsername(recipient.getKey()).filter(StaffAccount::isEnabled);
      if(account.isEmpty())continue;
      try {
        // The existing email template contains no message text, sender name, or patient data.
        mailer.sendPatientNotice(account.get().getEmail());
        recipient.getValue().forEach(StaffMessage::markNotified);
        messages.saveAll(recipient.getValue());
      }catch(Exception ignored){
        // Retry on the next scan without exposing recipient data to logs.
      }
    }
  }
}

package com.pulseos.auth;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface StaffMessageRepository extends JpaRepository<StaffMessage,UUID> {
  List<StaffMessage> findTop100BySenderOrRecipientOrderByCreatedAtDesc(String sender,String recipient);
  Optional<StaffMessage> findByIdAndRecipient(UUID id,String recipient);
}

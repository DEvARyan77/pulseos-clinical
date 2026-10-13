package com.pulseos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/v1/staff-chat")
public class StaffChatController {
  private final StaffAccountRepository accounts;
  private final StaffMessageRepository messages;
  public StaffChatController(StaffAccountRepository accounts,StaffMessageRepository messages){
    this.accounts=accounts;this.messages=messages;
  }
  private StaffAccount caller(Jwt jwt){
    if(jwt==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return accounts.findByUsername(jwt.getSubject()).filter(StaffAccount::isEnabled)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Staff account required"));
  }
  public record Contact(String username,String role){}
  public record SendRequest(@NotBlank String to,@NotBlank @Size(max=1200) String content){}
  @GetMapping("/contacts")
  public List<Contact> contacts(@AuthenticationPrincipal Jwt jwt){
    String me=caller(jwt).getUsername();
    return accounts.findAllByOrderByCreatedAtDesc().stream()
      .filter(StaffAccount::isEnabled).filter(a->!a.getUsername().equals(me))
      .map(a->new Contact(a.getUsername(),a.getRole())).toList();
  }
  @GetMapping("/messages")
  public List<StaffMessage> inbox(@AuthenticationPrincipal Jwt jwt){
    String me=caller(jwt).getUsername();
    return messages.findTop100BySenderOrRecipientOrderByCreatedAtDesc(me,me);
  }
  @PostMapping("/messages")
  @Transactional
  public ResponseEntity<StaffMessage> send(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody SendRequest request){
    String me=caller(jwt).getUsername();
    String recipient=request.to().strip().toLowerCase(Locale.ROOT);
    if(recipient.equals(me))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose another staff member");
    accounts.findByUsername(recipient).filter(StaffAccount::isEnabled)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Recipient not available"));
    StaffMessage item=messages.saveAndFlush(new StaffMessage(me,recipient,request.content().strip()));
    return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control","no-store").body(item);
  }
  @PatchMapping("/messages/{id}/read")
  @Transactional
  public StaffMessage markRead(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    String me=caller(jwt).getUsername();
    StaffMessage item=messages.findByIdAndRecipient(id,me)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Message not found"));
    item.markRead();
    return messages.saveAndFlush(item);
  }
}

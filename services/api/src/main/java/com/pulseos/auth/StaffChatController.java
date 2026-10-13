package com.pulseos.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.MediaType;
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
      .filter(a->StaffRole.ADMIN.name().equals(a.getRole())||StaffRole.CLINICIAN.name().equals(a.getRole()))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Staff account required"));
  }
  public record Contact(String username,String role){}
  public record SendRequest(@NotBlank String to,@Size(max=1200) String content,
     @Size(max=350000) String imageBase64,@Size(max=32) String imageMimeType){
    public SendRequest(String to,String content){this(to,content,null,null);}
  }
  @GetMapping("/contacts")
  public List<Contact> contacts(@AuthenticationPrincipal Jwt jwt){
    String me=caller(jwt).getUsername();
    return accounts.findAllByOrderByCreatedAtDesc().stream()
      .filter(StaffAccount::isEnabled)
      .filter(a->StaffRole.ADMIN.name().equals(a.getRole())||StaffRole.CLINICIAN.name().equals(a.getRole()))
      .filter(a->!a.getUsername().equals(me))
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
      .filter(a->StaffRole.ADMIN.name().equals(a.getRole())||StaffRole.CLINICIAN.name().equals(a.getRole()))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Recipient not available"));
    String content=request.content()==null?"":request.content().strip();
    boolean withImage=request.imageBase64()!=null&&!request.imageBase64().isBlank();
    if(content.isEmpty()&&!withImage)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Message text or image required");
    StaffMessage item=new StaffMessage(me,recipient,content);
    if(withImage){
      byte[] data;
      try{data=Base64.getDecoder().decode(request.imageBase64());}
      catch(IllegalArgumentException error){
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid image encoding");
      }
      if(data.length==0||data.length>262144)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Image must be 256 KB or smaller");
      String mime=request.imageMimeType();
      boolean png="image/png".equals(mime)&&data.length>8&&
        (data[0]&255)==137&&(data[1]&255)==80&&(data[2]&255)==78&&(data[3]&255)==71&&
        (data[4]&255)==13&&(data[5]&255)==10&&(data[6]&255)==26&&(data[7]&255)==10;
      boolean jpeg="image/jpeg".equals(mime)&&data.length>3&&
        (data[0]&255)==255&&(data[1]&255)==216&&(data[2]&255)==255;
      boolean webp="image/webp".equals(mime)&&data.length>12&&
        new String(data,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")&&
        new String(data,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
      if(!png&&!jpeg&&!webp)
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only PNG, JPEG or WebP images are accepted");
      item.attachImage(data,mime);
    }
    item=messages.saveAndFlush(item);
    return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control","no-store").body(item);
  }
  @GetMapping("/messages/{id}/image")
  public ResponseEntity<byte[]> image(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
    String me=caller(jwt).getUsername();
    StaffMessage item=messages.findById(id)
      .filter(m->me.equals(m.getSender())||me.equals(m.getRecipient()))
      .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Image not found"));
    if(!item.isImageAttached())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Image not found");
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(item.imageMimeType()))
      .header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
      .body(item.imageBytes());
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

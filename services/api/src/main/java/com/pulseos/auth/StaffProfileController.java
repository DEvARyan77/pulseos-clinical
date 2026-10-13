package com.pulseos.auth;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/v1/auth/profile")
public class StaffProfileController {
  private final StaffAccountRepository accounts;
  public StaffProfileController(StaffAccountRepository accounts){this.accounts=accounts;}
  public record Profile(String username,String email,String displayName,String role) {
    static Profile of(StaffAccount account){return new Profile(account.getUsername(),account.getEmail(),account.getDisplayName(),account.getRole());}
  }
  public record ProfileUpdate(@NotBlank @Size(min=2,max=80) String displayName) {}
  private StaffAccount current(Jwt jwt){
    if(jwt==null||jwt.getSubject()==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return accounts.findByUsername(jwt.getSubject()).filter(StaffAccount::isEnabled)
      .orElseThrow(()->new ResponseStatusException(HttpStatus.FORBIDDEN,"Verified staff account required"));
  }
  @GetMapping public ResponseEntity<Profile> get(@AuthenticationPrincipal Jwt jwt){
    return ResponseEntity.ok().header("Cache-Control","no-store").body(Profile.of(current(jwt)));
  }
  @PutMapping @Transactional
  public ResponseEntity<Profile> update(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody ProfileUpdate request){
    StaffAccount row=current(jwt);row.updateDisplayName(request.displayName());
    return ResponseEntity.ok().header("Cache-Control","no-store").body(Profile.of(accounts.saveAndFlush(row)));
  }
}

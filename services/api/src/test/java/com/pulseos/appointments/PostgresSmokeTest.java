package com.pulseos.appointments;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import tools.jackson.databind.json.JsonMapper;

/** Uses only the explicitly configured disposable test database. No external mail. */
@EnabledIfEnvironmentVariable(named="PULSEOS_TEST_DATABASE_URL", matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
  "spring.datasource.url=${PULSEOS_TEST_DATABASE_URL}",
  "spring.datasource.username=${PULSEOS_TEST_DATABASE_USERNAME:postgres}",
  "spring.datasource.password=${PULSEOS_TEST_DATABASE_PASSWORD:}",
  "pulseos.auth.enabled=true",
  // Public, test-only signing material. Never use in deployment configuration.
  "PULSEOS_AUTH_JWT_SECRET_BASE64=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
  "PULSEOS_EMAIL_PROVIDER=disabled",
  "PULSEOS_APPOINTMENT_REMINDERS_ENABLED=false",
  "PULSEOS_REGISTRATION_ENABLED=false"
})
class PostgresSmokeTest {
  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JwtEncoder encoder;
  private final HttpClient client=HttpClient.newHttpClient();

  private String account(String role) {
    String name="smoke-"+UUID.randomUUID();
    jdbc.update("INSERT INTO staff_accounts(id,username,email,password_hash,role,enabled) VALUES(?,?,?,?,?,true)",
      UUID.randomUUID(),name,name+"@example.test","not-a-login-password",role);
    return name;
  }
  private String token(String owner) {
    Instant now=Instant.now();
    return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),
      JwtClaimsSet.builder().issuer("pulseos-api").subject(owner).issuedAt(now)
        .expiresAt(now.plusSeconds(300)).claim("roles",List.of("CLINICIAN")).build())).getTokenValue();
  }
  private HttpResponse<String> get(String token) throws Exception {
    var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+
      "/api/v1/demo-appointments/dashboard")).timeout(Duration.ofSeconds(15)).GET();
    if(token!=null)builder.header("Authorization","Bearer "+token);
    return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
  }
  private UUID appointment(String owner,Instant at) {
    UUID id=UUID.randomUUID();
    jdbc.update("""
      INSERT INTO demo_appointments(id,created_by,patient_id,scheduled_at,visit_type,status)
      VALUES(?,?,? ,?,'FOLLOW_UP','SCHEDULED')
      """,id,owner,"DEMO-1001",Timestamp.from(at));
    return id;
  }
  @Test void realSecurityChainRejectsAnonymousAdminPatientAndRevokedAccounts() throws Exception {
    assertThat(get(null).statusCode()).isEqualTo(401);
    for(String role:List.of("ADMIN","PATIENT"))
      assertThat(get(token(account(role))).statusCode()).isEqualTo(403);
    String owner=account("CLINICIAN"), bearer=token(owner);
    assertThat(get(bearer).statusCode()).isEqualTo(200);
    jdbc.update("UPDATE staff_accounts SET enabled=false WHERE username=?",owner);
    assertThat(get(bearer).statusCode()).isEqualTo(403);
  }
  @Test void postgresQueriesScopeBothVisitsAndCompleteReminderStatistics() throws Exception {
    String owner=account("CLINICIAN"), other=account("CLINICIAN");
    Instant now=Instant.now();
    UUID owned=appointment(owner,now), foreign=appointment(other,now);
    for(int i=0;i<65;i++) jdbc.update("""
      INSERT INTO appointment_reminder_attempts
        (id,appointment_id,created_by,scheduled_at,status,attempted_at)
      VALUES(?,?,?,?,'ACCEPTED',?)
      """,UUID.randomUUID(),owned,owner,Timestamp.from(now.minusSeconds(i)),
      Timestamp.from(now.minusSeconds(1)));
    jdbc.update("""
      INSERT INTO appointment_reminder_attempts
        (id,appointment_id,created_by,scheduled_at,status,attempted_at)
      VALUES(?,?,?,?,'FAILED',?)
      """,UUID.randomUUID(),foreign,other,Timestamp.from(now),Timestamp.from(now.minusSeconds(1)));
    var response=get(token(owner));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
    var body=new JsonMapper().readTree(response.body());
    assertThat(body.get("todayVisits").asInt()).isEqualTo(1);
    assertThat(body.get("visits").size()).isEqualTo(1);
    assertThat(body.get("visits").get(0).get("id").asText()).isEqualTo(owned.toString());
    assertThat(body.get("reminderCounts").size()).isEqualTo(1);
    assertThat(body.get("reminderCounts").get(0).get("count").asInt()).isEqualTo(65);
    assertThat(response.body()).doesNotContain("@example.test","not-a-login-password",foreign.toString());
  }
}

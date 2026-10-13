package com.pulseos.appointments;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.pulseos.auth.VerificationMailSender;
import com.pulseos.patients.PatientRecord;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Service;

/** Demo-only email notifications; mail is never sent to unapproved patient addresses. */
@Service
public class AppointmentNoticeService {
  private final String publicUrl,provider,from,resendKey,relayUrl,relayKey;
  private final Set<String> allowed;
  private final ObjectProvider<JavaMailSender> smtp;
  private final VerificationMailSender genericMailer;
  private final HttpClient client=HttpClient.newHttpClient();
  public AppointmentNoticeService(
    @Value("${PULSEOS_PUBLIC_URL:}") String publicUrl,
    @Value("${PULSEOS_EMAIL_PROVIDER:disabled}") String provider,
    @Value("${PULSEOS_EMAIL_FROM:}") String from,
    @Value("${PULSEOS_RESEND_API_KEY:}") String resendKey,
    @Value("${PULSEOS_GOOGLE_SCRIPT_URL:}") String relayUrl,
    @Value("${PULSEOS_GOOGLE_SCRIPT_KEY:}") String relayKey,
    @Value("${PULSEOS_DEMO_EMAIL_ALLOWLIST:}") String allowlist,
    ObjectProvider<JavaMailSender> smtp,VerificationMailSender genericMailer){
    this.publicUrl=publicUrl;this.provider=provider;this.from=from;this.resendKey=resendKey;
    this.relayUrl=relayUrl;this.relayKey=relayKey;this.smtp=smtp;this.genericMailer=genericMailer;
    this.allowed=new HashSet<>();
    for(String email:allowlist.split(","))if(!email.isBlank())allowed.add(email.strip().toLowerCase(Locale.ROOT));
  }
  private static String json(String value){
    return "\"" + value.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n") + "\"";
  }
  private boolean allowed(PatientRecord patient){
    return patient!=null && patient.getContactEmail()!=null &&
      allowed.contains(patient.getContactEmail().strip().toLowerCase(Locale.ROOT));
  }
  public boolean sendCreated(PatientRecord patient){
    if(!allowed(patient)||!genericMailer.isConfigured())return false;
    try{genericMailer.sendPatientNotice(patient.getContactEmail());return true;}
    catch(Exception ignored){return false;}
  }
  public boolean sendAppointment(DemoAppointment appointment,PatientRecord patient){
    if(!allowed(patient)||from.isBlank())return false;
    try{
      URI origin=URI.create(publicUrl);
      if(!"https".equals(origin.getScheme())||origin.getHost()==null||origin.getUserInfo()!=null||
         origin.getRawQuery()!=null||origin.getRawFragment()!=null||
         origin.getPort()!=-1||!"/".equals(origin.getPath()==null||origin.getPath().isEmpty()?"/":origin.getPath()))
         return false;
      String url=publicUrl.replaceAll("/+$","")+"/patient/checkin?appointment="+appointment.getId();
      ByteArrayOutputStream bytes=new ByteArrayOutputStream();
      MatrixToImageWriter.writeToStream(new QRCodeWriter().encode(url,BarcodeFormat.QR_CODE,240,240),"PNG",bytes);
      byte[] qr=bytes.toByteArray();
      if(qr.length>30000)return false;
      String encoded=Base64.getEncoder().encodeToString(qr);
      String doctor=appointment.getCreatedBy();
      String when=appointment.getScheduledAt().toString();
      String visit=appointment.getVisitType().name();
      String stage=appointment.getStatus().name();
      String subject="PulseOS demo appointment · "+stage.replace("_"," ");
      String plain="PulseOS fictional appointment\\nAssigned clinician: "+doctor+
        "\\nAppointment: "+when+"\\nVisit: "+visit+"\\nStatus: "+stage+
        "\\nCheck in using the attached QR or link: "+url+
        "\\nSign in as the verified patient to confirm. No real clinical information is included.";
      String html="<html><body style=\\\"font-family:Arial;padding:22px\\\">"+
        "<h1>PulseOS demo appointment</h1><p>Assigned clinician: "+doctor+"</p>"+
        "<p>Appointment: "+when+"</p><p>Visit: "+visit+"</p><p>Status: "+stage+"</p>"+
        "<p><a href=\\\""+url+"\\\">Open appointment check-in</a></p>"+
        "<img src=\\\"cid:pulseos-qr\\\" width=\\\"240\\\" height=\\\"240\\\" alt=\\\"Check-in QR\\\"/>"+
        "<p>Use your verified patient account. Fictional data only.</p></body></html>";
      String destination=patient.getContactEmail();
      switch(provider.toLowerCase(Locale.ROOT)){
        case "smtp" -> {
          JavaMailSender mail=smtp.getIfAvailable();if(mail==null)return false;
          MimeMessage mime=mail.createMimeMessage();
          MimeMessageHelper helper=new MimeMessageHelper(mime,true,"UTF-8");
          helper.setFrom(from);helper.setTo(destination);helper.setSubject(subject);helper.setText(plain,html);
          helper.addInline("pulseos-qr",new ByteArrayResource(qr),"image/png");
          mail.send(mime);
        }
        case "resend" -> {
          if(resendKey.isBlank())return false;
          String payload="{\"from\":"+json(from)+",\"to\":["+json(destination)+"],\"subject\":"+json(subject)+
             ",\"text\":"+json(plain)+",\"html\":"+json(html.replace("cid:pulseos-qr",""))+
             ",\"attachments\":[{\"filename\":\"pulseos-checkin-qr.png\",\"content\":"+json(encoded)+"}]}";
          HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.resend.com/emails"))
            .timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+resendKey)
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build();
          HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
          if(response.statusCode()<200||response.statusCode()>=300)return false;
        }
        case "google-script" -> {
          if(relayKey.length()<48)return false;
          URI endpoint=URI.create(relayUrl);
          if(!"https".equals(endpoint.getScheme())||!"script.google.com".equals(endpoint.getHost())||
             endpoint.getPort()!=-1||endpoint.getRawQuery()!=null||
             !endpoint.getPath().matches("/macros/s/[^/]+/exec"))return false;
          String payload="{\"relayKey\":"+json(relayKey)+",\"email\":"+json(destination)+
            ",\"code\":\"\",\"kind\":\"appointment\",\"doctor\":"+json(doctor)+
            ",\"scheduledAt\":"+json(when)+",\"visitType\":"+json(visit)+
            ",\"stage\":"+json(stage)+",\"checkinUrl\":"+json(url)+
            ",\"qrBase64\":"+json(encoded)+"}";
          HttpRequest request=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(payload)).build();
          HttpResponse<String> response=client.send(request,HttpResponse.BodyHandlers.ofString());
          if(response.statusCode()!=200||!"OK".equals(response.body().trim()))return false;
        }
        default -> {return false;}
      }
      return true;
    }catch(Exception ignored){return false;}
  }
}

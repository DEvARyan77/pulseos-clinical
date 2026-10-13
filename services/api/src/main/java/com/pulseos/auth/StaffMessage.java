package com.pulseos.auth;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="staff_messages")
public class StaffMessage {
  @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
  @Column(nullable=false,length=80) private String sender;
  @Column(nullable=false,length=80) private String recipient;
  @Column(nullable=false,length=1200) private String content;
  @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
  @Column(name="read_at") private Instant readAt;
  @Column(name="notified_at") private Instant notifiedAt;
  @Column(name="image_data",columnDefinition="bytea") private byte[] imageData;
  @Column(name="image_mime_type",length=32) private String imageMimeType;
  protected StaffMessage(){}
  public StaffMessage(String sender,String recipient,String content){
    this.sender=sender;this.recipient=recipient;this.content=content;
  }
  public UUID getId(){return id;}
  public String getSender(){return sender;}
  public String getRecipient(){return recipient;}
  public String getContent(){return content;}
  public Instant getCreatedAt(){return createdAt;}
  public Instant getReadAt(){return readAt;}
  public void markRead(){if(readAt==null)readAt=Instant.now();}
  public void markNotified(){if(notifiedAt==null)notifiedAt=Instant.now();}
  public Instant getNotifiedAt(){return notifiedAt;}
  public boolean isImageAttached(){return imageData!=null&&imageData.length>0;}
  public byte[] imageBytes(){return imageData;}
  public String imageMimeType(){return imageMimeType;}
  public void attachImage(byte[] bytes,String mime){imageData=bytes.clone();imageMimeType=mime;}
}

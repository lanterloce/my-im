package com.example.im.message;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name="messages")
@Getter @Setter
public class Message {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  private Long id;
  private Long conversationId;
  private Long senderId;
  @Column(columnDefinition="TEXT")
  private String content;
  private LocalDateTime createdAt=LocalDateTime.now();
}

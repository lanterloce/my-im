package com.example.im.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="users")
@Getter @Setter
public class User {
  @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
  private Long id;
  @Column(nullable=false, unique=true, length=64)
  private String username;
  @Column(nullable=false)
  private String password;
  private String displayName;
}

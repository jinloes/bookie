package com.bookie.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outlook_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutlookToken {

  @Id private Long id;

  // Serialized MSAL4J token cache (JSON) — contains access token, refresh token, and account info
  @Column(columnDefinition = "TEXT")
  private String cacheData;
}

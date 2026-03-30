package com.bookie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks how many confirmed expenses have linked a given email keyword (account number, reference
 * code, etc.) to a given payer. Enables payer identification from stable identifiers in the email
 * body even when the sender address or subject line varies.
 */
@Entity
@Table(
    name = "email_keyword_payer_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "payer_name"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailKeywordPayerHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Normalized (lowercase, trimmed) identifier extracted from the email. */
  @Column(nullable = false)
  private String keyword;

  @Column(name = "payer_name", nullable = false)
  private String payerName;

  @Column(nullable = false)
  private int occurrences;
}

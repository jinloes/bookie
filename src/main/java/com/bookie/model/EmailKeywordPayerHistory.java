package com.bookie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
    uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "payer_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailKeywordPayerHistory implements HasOccurrences {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Normalized (lowercase, trimmed) identifier extracted from the email. */
  @Column(nullable = false)
  private String keyword;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "payer_id", nullable = false)
  private Payer payer;

  @Column(nullable = false)
  private int occurrences;
}

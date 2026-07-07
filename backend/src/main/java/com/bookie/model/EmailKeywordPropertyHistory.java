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
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks how many confirmed expenses have linked a given email keyword (account number, reference
 * code, invoice number, service address, etc.) to a given property. Enables property matching even
 * when the payer changes, as long as the identifier in the email remains consistent.
 */
@Entity
@Table(
    name = "email_keyword_property_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "property_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailKeywordPropertyHistory implements HasOccurrences {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Normalized (lowercase, trimmed) identifier extracted from the email. */
  @Column(nullable = false)
  private String keyword;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  @Column(nullable = false)
  private int occurrences;

  @Version private Long version;
}

package com.bookie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks how many confirmed expenses have linked a given email keyword to a given expense category.
 * Enables direct category resolution from stable identifiers in the email body, bypassing the
 * two-hop keyword → payer → category lookup.
 */
@Entity
@Table(
    name = "email_keyword_category_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"keyword", "category"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailKeywordCategoryHistory implements HasOccurrences {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Normalized (lowercase, trimmed) identifier extracted from the email. */
  @Column(nullable = false)
  private String keyword;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ExpenseCategory category;

  @Column(nullable = false)
  private int occurrences;

  @Version private Long version;
}

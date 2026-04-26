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
 * Temporarily stores keywords extracted from an email at parse time. Consumed (and deleted) when
 * the resulting expense is saved, at which point the keywords are recorded in {@link
 * EmailKeywordHistory}.
 */
@Entity
@Table(
    name = "parsed_email_keywords",
    uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "keyword"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedEmailKeywords {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** The Outlook message ID the keywords were extracted from. */
  @Column(name = "source_id", nullable = false)
  private String sourceId;

  /** Normalized (lowercase, trimmed) keyword extracted from the email. */
  @Column(nullable = false)
  private String keyword;
}

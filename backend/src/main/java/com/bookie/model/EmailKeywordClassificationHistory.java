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
 * Records confirmed activity/category classifications for stable identifiers extracted from an
 * intake item. Category history is scoped by activity so a shared vendor or keyword cannot leak a
 * rental classification into employment or self-employment.
 */
@Entity
@Table(
    name = "email_keyword_classification_history",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"keyword", "activity_id", "financial_category_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailKeywordClassificationHistory implements HasOccurrences {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String keyword;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "activity_id", nullable = false)
  private FinancialActivity activity;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "financial_category_id", nullable = false)
  private FinancialCategory financialCategory;

  @Column(nullable = false)
  private int occurrences;

  @Version private Long version;
}

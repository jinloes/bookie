package com.bookie.catalog.classification.domain;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
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
        @UniqueConstraint(columnNames = {"keyword", "activity_id", "neutral_category_id"}))
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
  @JoinColumn(name = "neutral_category_id", nullable = false)
  private NeutralCategory neutralCategory;

  @Column(nullable = false)
  private int occurrences;

  @Version private Long version;
}

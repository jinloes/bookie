package com.bookie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Tracks how many confirmed expenses have linked a given payer to a given expense category. Used to
 * suggest the most likely category when parsing a new email from a known payer.
 */
@Entity
@Table(
    name = "payer_category_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"payer_id", "category"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayerCategoryHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "payer_id", nullable = false)
  private Payer payer;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ExpenseCategory category;

  @Column(nullable = false)
  private int occurrences;
}

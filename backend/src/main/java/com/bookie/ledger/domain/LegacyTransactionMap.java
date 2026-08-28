package com.bookie.ledger.domain;

import com.bookie.model.ExpenseCategory;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "legacy_transaction_map")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyTransactionMap {

  @EmbeddedId private LegacyTransactionKey key;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "transaction_id", nullable = false, unique = true)
  private FinancialTransaction transaction;

  @Enumerated(EnumType.STRING)
  @Column(name = "legacy_expense_category", length = 50)
  private ExpenseCategory legacyExpenseCategory;

  @Column(name = "canonical_hash", nullable = false, length = 64)
  private String canonicalHash;

  @Column(name = "mapped_at", nullable = false)
  private LocalDateTime mappedAt;
}

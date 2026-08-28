package com.bookie.model;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.domain.Property;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Positive
  @Column(nullable = false)
  private BigDecimal amount;

  @NotBlank
  @Column(nullable = false)
  private String description;

  @NotNull
  @Column(nullable = false)
  private LocalDate date;

  @Enumerated(EnumType.STRING)
  private ExpenseCategory category;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "property_id")
  private Property property;

  @Enumerated(EnumType.STRING)
  private ExpenseSource sourceType;

  private String sourceId;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "payer_id")
  private Counterparty payer;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "activity_id", nullable = false)
  private FinancialActivity activity;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private FinancialCategory financialCategory;

  private String receiptOneDriveId;

  private String receiptFileName;
}

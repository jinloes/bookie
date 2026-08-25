package com.bookie.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pending_incomes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingIncome {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private String sourceId;

  @Enumerated(EnumType.STRING)
  private ExpenseSource sourceType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PendingIncomeStatus status;

  private String source;
  private BigDecimal amount;
  private String description;
  private LocalDate date;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "property_id")
  private Property property;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "payer_id")
  private Payer payer;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "activity_id", nullable = false)
  private FinancialActivity activity;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "category_id", nullable = false)
  private FinancialCategory financialCategory;

  @Column(nullable = false)
  @Builder.Default
  private boolean classificationAmbiguous = true;

  private String receiptOneDriveId;
  private String receiptFileName;

  @Column(length = 2000)
  private String errorMessage;

  @Column(nullable = false)
  private LocalDateTime createdAt;
}

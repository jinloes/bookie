package com.bookie.model;

import jakarta.persistence.*;
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

  private String receiptOneDriveId;
  private String receiptFileName;

  @Column(length = 2000)
  private String errorMessage;

  @Column(nullable = false)
  private LocalDateTime createdAt;
}

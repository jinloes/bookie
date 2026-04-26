package com.bookie.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pending_expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingExpense {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true)
  private String sourceId;

  @Enumerated(EnumType.STRING)
  private ExpenseSource sourceType;

  @Enumerated(EnumType.STRING)
  private EmailType emailType;

  private String subject;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PendingExpenseStatus status;

  private BigDecimal amount;
  private String description;
  private LocalDate date;
  private String category;
  private String propertyName;
  private String payerName;

  @Column(length = 2000)
  private String errorMessage;

  @ElementCollection
  @CollectionTable(
      name = "pending_expense_aliases",
      joinColumns = @JoinColumn(name = "pending_expense_id"))
  @Column(name = "alias")
  @Builder.Default
  private List<String> unrecognizedAliases = new ArrayList<>();

  @Column(nullable = false)
  private LocalDateTime createdAt;
}

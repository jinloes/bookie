package com.bookie.model;

import jakarta.persistence.*;
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

  @Column(nullable = false)
  private BigDecimal amount;

  @Column(nullable = false)
  private String description;

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
  private Payer payer;
}

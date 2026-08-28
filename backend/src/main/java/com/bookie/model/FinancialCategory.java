package com.bookie.model;

import com.bookie.catalog.activity.domain.TaxTreatment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "financial_categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialCategory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(name = "category_key", nullable = false, unique = true)
  private String key;

  @NotBlank
  @Column(nullable = false)
  private String label;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TransactionDirection direction;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TaxTreatment taxTreatment;

  private String taxLine;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(nullable = false)
  @Builder.Default
  private boolean system = true;
}

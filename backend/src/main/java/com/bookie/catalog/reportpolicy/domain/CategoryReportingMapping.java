package com.bookie.catalog.reportpolicy.domain;

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
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "category_reporting_mappings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryReportingMapping {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "neutral_category_id", nullable = false)
  private NeutralCategory neutralCategory;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "reporting_profile_id", nullable = false)
  private ReportingProfile reportingProfile;

  @NotNull
  @Column(name = "legacy_category_id", nullable = false, unique = true)
  private Long legacyCategoryId;

  @Column(name = "report_line")
  private String reportLine;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;
}

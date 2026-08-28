package com.bookie.catalog.reportpolicy.domain;

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
@Table(name = "reporting_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportingProfile {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(name = "profile_key", nullable = false, unique = true)
  private ReportingProfileKey key;

  @NotBlank
  @Column(nullable = false)
  private String label;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;
}

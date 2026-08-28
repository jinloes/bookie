package com.bookie.catalog.category.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "legacy_category_map")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyCategoryMap {

  @Id
  @Column(name = "legacy_category_id")
  private Long legacyCategoryId;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "neutral_category_id", nullable = false)
  private NeutralCategory neutralCategory;

  @NotBlank
  @Column(name = "legacy_category_key", nullable = false, unique = true)
  private String legacyCategoryKey;
}

package com.bookie.catalog.activity.domain;

import com.bookie.catalog.household.domain.HouseholdMember;
import com.bookie.catalog.property.domain.Property;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
@Table(name = "financial_activities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialActivity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotBlank
  @Column(nullable = false, unique = true)
  private String name;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ActivityType activityType;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TaxTreatment taxTreatment;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "owner_id", nullable = false)
  private HouseholdMember owner;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "property_id")
  private Property property;

  @Column(nullable = false)
  @Builder.Default
  private boolean active = true;

  @Column(unique = true)
  private String systemKey;
}

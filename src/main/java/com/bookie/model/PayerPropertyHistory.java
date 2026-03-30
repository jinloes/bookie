package com.bookie.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tracks how many confirmed expenses have linked a given payer to a given property. A payer may
 * appear in multiple rows (one per property they service), ranked by frequency so the AI can weigh
 * the most likely match when multiple properties share the same payer.
 */
@Entity
@Table(
    name = "payer_property_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"payer_id", "property_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayerPropertyHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "payer_id", nullable = false)
  private Payer payer;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "property_id", nullable = false)
  private Property property;

  private int occurrences;
}

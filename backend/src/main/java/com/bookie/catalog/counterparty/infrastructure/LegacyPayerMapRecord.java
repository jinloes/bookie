package com.bookie.catalog.counterparty.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "legacy_payer_map")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class LegacyPayerMapRecord {

  @Id
  @Column(name = "payer_id")
  private Long payerId;

  @Column(name = "counterparty_id", nullable = false, unique = true)
  private Long counterpartyId;
}

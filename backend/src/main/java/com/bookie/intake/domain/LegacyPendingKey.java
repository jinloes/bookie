package com.bookie.intake.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class LegacyPendingKey implements Serializable {

  @Enumerated(EnumType.STRING)
  @Column(name = "legacy_table", nullable = false, length = 30)
  private LegacyPendingTable table;

  @Column(name = "legacy_id", nullable = false)
  private Long id;
}

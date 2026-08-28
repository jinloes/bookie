package com.bookie.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LegacyTransactionKey implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Enumerated(EnumType.STRING)
  @Column(name = "legacy_table", nullable = false, length = 20)
  private LegacyTransactionTable table;

  @Column(name = "legacy_id", nullable = false)
  private Long id;

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof LegacyTransactionKey that)) {
      return false;
    }
    return table == that.table && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(table, id);
  }
}

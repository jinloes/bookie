package com.bookie.integrations.venmo;

import java.util.List;
import lombok.Builder;

@Builder
public record VenmoStatement(int totalRows, int invalidRows, List<VenmoTransaction> transactions) {

  public VenmoStatement {
    transactions = transactions == null ? List.of() : List.copyOf(transactions);
  }
}

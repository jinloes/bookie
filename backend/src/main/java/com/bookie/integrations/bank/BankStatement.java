package com.bookie.integrations.bank;

import java.util.List;
import lombok.Builder;

@Builder
public record BankStatement(int totalRows, int invalidRows, List<BankTransaction> transactions) {

  public BankStatement {
    transactions = transactions == null ? List.of() : List.copyOf(transactions);
  }
}

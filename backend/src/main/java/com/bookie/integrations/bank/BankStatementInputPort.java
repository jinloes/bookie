package com.bookie.integrations.bank;

public interface BankStatementInputPort {

  BankStatement parse(byte[] content);
}

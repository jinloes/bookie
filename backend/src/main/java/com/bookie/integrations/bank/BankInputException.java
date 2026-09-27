package com.bookie.integrations.bank;

public class BankInputException extends IllegalArgumentException {

  public BankInputException(String message) {
    super(message);
  }
}

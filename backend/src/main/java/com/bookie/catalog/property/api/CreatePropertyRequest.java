package com.bookie.catalog.property.api;

import com.bookie.catalog.property.application.CreatePropertyCommand;
import com.bookie.catalog.property.domain.PropertyType;
import java.util.Set;

public record CreatePropertyRequest(
    String name, String address, PropertyType type, String notes, Set<String> accounts) {

  CreatePropertyCommand toCommand() {
    return new CreatePropertyCommand(name, address, type, notes, accounts);
  }
}

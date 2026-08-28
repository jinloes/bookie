package com.bookie.catalog.property.api;

import com.bookie.catalog.property.application.UpdatePropertyCommand;
import com.bookie.catalog.property.domain.PropertyType;
import java.util.Set;

public record UpdatePropertyRequest(
    String name, String address, PropertyType type, String notes, Set<String> accounts) {

  UpdatePropertyCommand toCommand() {
    return new UpdatePropertyCommand(name, address, type, notes, accounts);
  }
}

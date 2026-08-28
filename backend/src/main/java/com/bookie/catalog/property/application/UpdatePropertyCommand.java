package com.bookie.catalog.property.application;

import com.bookie.catalog.property.domain.PropertyType;
import java.util.Set;

public record UpdatePropertyCommand(
    String name, String address, PropertyType type, String notes, Set<String> accounts) {}

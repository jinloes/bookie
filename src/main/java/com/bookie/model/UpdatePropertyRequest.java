package com.bookie.model;

import java.util.Set;

public record UpdatePropertyRequest(
    String name, String address, PropertyType type, String notes, Set<String> accounts) {}

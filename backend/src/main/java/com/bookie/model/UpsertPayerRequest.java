package com.bookie.model;

import java.util.List;
import java.util.Set;

public record UpsertPayerRequest(
    String name, PayerType type, List<String> aliases, Set<String> accounts) {}

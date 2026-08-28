package com.bookie.catalog.counterparty.application;

import com.bookie.catalog.counterparty.domain.CounterpartyType;
import java.util.List;
import java.util.Set;

public record UpsertCounterpartyCommand(
    String name, CounterpartyType type, List<String> aliases, Set<String> accounts) {}

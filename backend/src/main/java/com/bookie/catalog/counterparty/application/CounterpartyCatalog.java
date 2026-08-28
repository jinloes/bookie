package com.bookie.catalog.counterparty.application;

import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.List;
import java.util.Optional;

public interface CounterpartyCatalog {

  List<Counterparty> findAll();

  Optional<Counterparty> findByName(String name);

  Optional<Counterparty> findByAlias(String alias);

  Counterparty findById(Long id);

  Optional<Counterparty> findOptionalById(Long id);

  List<Counterparty> findByAccounts(List<String> accounts);

  Counterparty create(UpsertCounterpartyCommand command);

  Counterparty update(Long id, UpsertCounterpartyCommand command);

  void delete(Long id);

  void addAliasIfAbsent(String counterpartyName, String alias);
}

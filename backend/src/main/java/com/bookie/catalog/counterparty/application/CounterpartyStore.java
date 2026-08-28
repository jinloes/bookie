package com.bookie.catalog.counterparty.application;

import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.List;
import java.util.Optional;

public interface CounterpartyStore {

  List<Counterparty> findAll();

  Optional<Counterparty> findById(Long id);

  Optional<Counterparty> findByNameIgnoreCase(String name);

  Optional<Counterparty> findByAliasIgnoreCase(String alias);

  List<Counterparty> findByAccountIn(List<String> accounts);

  Counterparty saveAndFlush(Counterparty counterparty);

  void deleteById(Long id);

  void flush();
}

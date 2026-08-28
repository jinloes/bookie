package com.bookie.catalog.counterparty.infrastructure;

import com.bookie.catalog.counterparty.application.CounterpartyStore;
import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaCounterpartyStore implements CounterpartyStore {

  private final CounterpartyRepository counterpartyRepository;

  @Override
  public List<Counterparty> findAll() {
    return counterpartyRepository.findAll();
  }

  @Override
  public Optional<Counterparty> findById(Long id) {
    return counterpartyRepository.findById(id);
  }

  @Override
  public Optional<Counterparty> findByNameIgnoreCase(String name) {
    return counterpartyRepository.findByNameIgnoreCase(name);
  }

  @Override
  public Optional<Counterparty> findByAliasIgnoreCase(String alias) {
    return counterpartyRepository.findByAliasIgnoreCase(alias);
  }

  @Override
  public List<Counterparty> findByAccountIn(List<String> accounts) {
    return counterpartyRepository.findByAccountIn(accounts);
  }

  @Override
  public Counterparty saveAndFlush(Counterparty counterparty) {
    return counterpartyRepository.saveAndFlush(counterparty);
  }

  @Override
  public void deleteById(Long id) {
    counterpartyRepository.deleteById(id);
  }

  @Override
  public void flush() {
    counterpartyRepository.flush();
  }
}

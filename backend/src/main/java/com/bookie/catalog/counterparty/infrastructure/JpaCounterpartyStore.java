package com.bookie.catalog.counterparty.infrastructure;

import com.bookie.catalog.counterparty.application.CounterpartyStore;
import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
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
    return unique("name", counterpartyRepository.findAllByNameIgnoreCase(name));
  }

  @Override
  public Optional<Counterparty> findByAliasIgnoreCase(String alias) {
    return unique("alias", counterpartyRepository.findAllByAliasIgnoreCase(alias));
  }

  @Override
  public boolean aliasBelongsToAnotherCounterparty(String alias, Long counterpartyId) {
    return counterpartyRepository.countOtherAliasOwners(alias, counterpartyId) > 0;
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

  // Names and aliases are not unique, so several matches are reported as unresolved rather than
  // letting a single-result query throw.
  private static Optional<Counterparty> unique(String kind, List<Counterparty> matches) {
    if (matches.isEmpty()) {
      return Optional.empty();
    }
    if (matches.size() == 1) {
      return Optional.of(matches.getFirst());
    }
    log.warn("Ambiguous counterparty {} lookup matched {} counterparties", kind, matches.size());
    return Optional.empty();
  }
}

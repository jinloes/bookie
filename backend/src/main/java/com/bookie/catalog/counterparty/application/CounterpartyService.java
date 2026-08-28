package com.bookie.catalog.counterparty.application;

import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CounterpartyService implements CounterpartyCatalog {

  private final CounterpartyStore counterpartyStore;
  private final CounterpartyMirror counterpartyMirror;
  private final CounterpartyLedgerReferences ledgerReferences;
  private final CounterpartyIntakeReferences intakeReferences;
  private final CounterpartyClassificationReferences classificationReferences;

  @Override
  public List<Counterparty> findAll() {
    return counterpartyStore.findAll();
  }

  @Override
  public Optional<Counterparty> findByName(String name) {
    return counterpartyStore.findByNameIgnoreCase(name);
  }

  @Override
  public Optional<Counterparty> findByAlias(String alias) {
    return counterpartyStore.findByAliasIgnoreCase(alias);
  }

  @Override
  public Counterparty findById(Long id) {
    return counterpartyStore
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payer not found: " + id));
  }

  @Override
  public Optional<Counterparty> findOptionalById(Long id) {
    return counterpartyStore.findById(id);
  }

  @Override
  public List<Counterparty> findByAccounts(List<String> accounts) {
    return counterpartyStore.findByAccountIn(accounts);
  }

  @Transactional
  @Override
  public Counterparty create(UpsertCounterpartyCommand command) {
    Counterparty counterparty =
        Counterparty.builder()
            .name(command.name())
            .type(command.type())
            .aliases(
                command.aliases() != null ? new ArrayList<>(command.aliases()) : new ArrayList<>())
            .accounts(
                command.accounts() != null ? new HashSet<>(command.accounts()) : new HashSet<>())
            .build();
    Counterparty saved = counterpartyStore.saveAndFlush(counterparty);
    counterpartyMirror.synchronize(saved);
    return saved;
  }

  @Transactional
  @Override
  public Counterparty update(Long id, UpsertCounterpartyCommand command) {
    Counterparty existing = findById(id);
    existing.setName(command.name());
    existing.setType(command.type());
    existing.setAliases(
        command.aliases() != null ? new ArrayList<>(command.aliases()) : new ArrayList<>());
    existing.setAccounts(
        command.accounts() != null ? new HashSet<>(command.accounts()) : new HashSet<>());
    Counterparty saved = counterpartyStore.saveAndFlush(existing);
    counterpartyMirror.synchronize(saved);
    return saved;
  }

  @Transactional
  @Override
  public void delete(Long id) {
    findById(id);
    ledgerReferences.detachCounterparty(id);
    intakeReferences.detachCounterparty(id);
    classificationReferences.removeCounterpartyReferences(id);
    ledgerReferences.requireNoReferences(id);
    intakeReferences.requireNoReferences(id);
    classificationReferences.requireNoCounterpartyReferences(id);
    counterpartyMirror.delete(id);
    counterpartyStore.deleteById(id);
    counterpartyStore.flush();
  }

  @Transactional
  @Override
  public void addAliasIfAbsent(String counterpartyName, String alias) {
    counterpartyStore
        .findByNameIgnoreCase(counterpartyName)
        .ifPresent(
            counterparty -> {
              boolean alreadyPresent =
                  counterparty.getName().equalsIgnoreCase(alias)
                      || counterparty.getAliases().stream()
                          .anyMatch(existing -> existing.equalsIgnoreCase(alias));
              if (!alreadyPresent) {
                counterparty.getAliases().add(alias);
                Counterparty saved = counterpartyStore.saveAndFlush(counterparty);
                counterpartyMirror.synchronize(saved);
                log.info("Auto-saved alias '{}' for counterparty '{}'", alias, counterpartyName);
              }
            });
  }
}

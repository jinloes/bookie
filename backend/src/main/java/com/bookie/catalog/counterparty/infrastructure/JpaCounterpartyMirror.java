package com.bookie.catalog.counterparty.infrastructure;

import com.bookie.catalog.counterparty.application.CounterpartyMirror;
import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.ArrayList;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaCounterpartyMirror implements CounterpartyMirror {

  private final NormalizedCounterpartyRepository counterpartyRepository;
  private final LegacyPayerMapRepository legacyPayerMapRepository;

  @Override
  public void synchronize(Counterparty counterparty) {
    LegacyPayerMapRecord map =
        legacyPayerMapRepository
            .findById(counterparty.getId())
            .orElseGet(
                () ->
                    LegacyPayerMapRecord.builder()
                        .payerId(counterparty.getId())
                        .counterpartyId(counterparty.getId())
                        .build());
    if (!counterparty.getId().equals(map.getCounterpartyId())) {
      throw new IllegalStateException(
          "Legacy payer maps to a different counterparty: " + counterparty.getId());
    }

    NormalizedCounterpartyRecord normalized =
        counterpartyRepository
            .findById(counterparty.getId())
            .orElseGet(
                () -> NormalizedCounterpartyRecord.builder().id(counterparty.getId()).build());
    normalized.setName(counterparty.getName());
    normalized.setType(counterparty.getType());
    normalized.setAliases(new ArrayList<>(counterparty.getAliases()));
    normalized.setAccounts(new HashSet<>(counterparty.getAccounts()));
    counterpartyRepository.saveAndFlush(normalized);
    legacyPayerMapRepository.saveAndFlush(map);
  }

  @Override
  public void delete(Long counterpartyId) {
    legacyPayerMapRepository
        .findByCounterpartyId(counterpartyId)
        .ifPresent(legacyPayerMapRepository::delete);
    legacyPayerMapRepository.flush();
    counterpartyRepository.deleteById(counterpartyId);
    counterpartyRepository.flush();
  }
}

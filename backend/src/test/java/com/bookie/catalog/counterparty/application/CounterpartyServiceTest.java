package com.bookie.catalog.counterparty.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CounterpartyServiceTest {

  @Mock private CounterpartyStore counterpartyStore;
  @Mock private CounterpartyMirror counterpartyMirror;
  @Mock private CounterpartyLedgerReferences ledgerReferences;
  @Mock private CounterpartyIntakeReferences intakeReferences;
  @Mock private CounterpartyClassificationReferences classificationReferences;

  @InjectMocks private CounterpartyService counterpartyService;

  private static Counterparty counterparty(String name, String... existingAliases) {
    return Counterparty.builder()
        .id(1L)
        .name(name)
        .type(CounterpartyType.COMPANY)
        .aliases(new ArrayList<>(List.of(existingAliases)))
        .accounts(new HashSet<>())
        .build();
  }

  @Test
  void delegatesNameAliasAndAccountLookupsToTheCatalogStore() {
    Counterparty counterparty = counterparty("Example Utility", "Utility Alias");
    when(counterpartyStore.findByNameIgnoreCase("Example Utility"))
        .thenReturn(Optional.of(counterparty));
    when(counterpartyStore.findByAliasIgnoreCase("Utility Alias"))
        .thenReturn(Optional.of(counterparty));
    when(counterpartyStore.findByAccountIn(List.of("account-001")))
        .thenReturn(List.of(counterparty));

    assertThat(counterpartyService.findByName("Example Utility")).contains(counterparty);
    assertThat(counterpartyService.findByAlias("Utility Alias")).contains(counterparty);
    assertThat(counterpartyService.findByAccounts(List.of("account-001")))
        .containsExactly(counterparty);
  }

  @Nested
  class Create {

    @Test
    void savesLegacyCompatibleCounterpartyAndNormalizedMirror() {
      UpsertCounterpartyCommand command =
          new UpsertCounterpartyCommand(
              "Acme Corp", CounterpartyType.COMPANY, List.of("Acme"), Set.of("ACC-001"));
      Counterparty saved = counterparty("Acme Corp");
      when(counterpartyStore.saveAndFlush(any())).thenReturn(saved);

      assertThat(counterpartyService.create(command)).isEqualTo(saved);

      verify(counterpartyMirror).synchronize(saved);
    }

    @Test
    void nullAliasesAndAccountsBecomeEmptyCollections() {
      UpsertCounterpartyCommand command =
          new UpsertCounterpartyCommand("Acme Corp", CounterpartyType.COMPANY, null, null);
      Counterparty saved = counterparty("Acme Corp");
      when(counterpartyStore.saveAndFlush(any())).thenReturn(saved);

      counterpartyService.create(command);

      ArgumentCaptor<Counterparty> captor = ArgumentCaptor.forClass(Counterparty.class);
      verify(counterpartyStore).saveAndFlush(captor.capture());
      assertThat(captor.getValue().getAliases()).isEmpty();
      assertThat(captor.getValue().getAccounts()).isEmpty();
    }
  }

  @Nested
  class Update {

    @Test
    void updatesCounterpartyAndMirror() {
      Counterparty existing = counterparty("Old Name", "alias1");
      UpsertCounterpartyCommand command =
          new UpsertCounterpartyCommand(
              "New Name", CounterpartyType.PERSON, List.of("alias2"), Set.of("ACC-002"));
      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(existing));
      when(counterpartyStore.saveAndFlush(existing)).thenReturn(existing);

      counterpartyService.update(1L, command);

      assertThat(existing.getName()).isEqualTo("New Name");
      assertThat(existing.getType()).isEqualTo(CounterpartyType.PERSON);
      assertThat(existing.getAliases()).containsExactly("alias2");
      assertThat(existing.getAccounts()).containsExactly("ACC-002");
      verify(counterpartyMirror).synchronize(existing);
    }
  }

  @Nested
  class Delete {

    @Test
    void detachesReferencesWithoutDeletingFinancialRecords() {
      when(counterpartyStore.findById(8L)).thenReturn(Optional.of(counterparty("Acme")));

      counterpartyService.delete(8L);

      verify(ledgerReferences).detachCounterparty(8L);
      verify(intakeReferences).detachCounterparty(8L);
      verify(classificationReferences).removeCounterpartyReferences(8L);
      verify(ledgerReferences).requireNoReferences(8L);
      verify(intakeReferences).requireNoReferences(8L);
      verify(classificationReferences).requireNoCounterpartyReferences(8L);
      verify(counterpartyMirror).delete(8L);
      verify(counterpartyStore).deleteById(8L);
      verify(counterpartyStore).flush();
    }
  }

  @Nested
  class AddAliasIfAbsent {

    @Test
    void newAliasIsSavedAndMirrored() {
      Counterparty existing = counterparty("Alameda County Water District");
      when(counterpartyStore.findByNameIgnoreCase("Alameda County Water District"))
          .thenReturn(Optional.of(existing));
      when(counterpartyStore.saveAndFlush(existing)).thenReturn(existing);

      counterpartyService.addAliasIfAbsent("Alameda County Water District", "ACWD");

      assertThat(existing.getAliases()).contains("ACWD");
      verify(counterpartyMirror).synchronize(existing);
    }

    @Test
    void duplicateAliasIsNotSaved() {
      Counterparty existing = counterparty("Alameda County Water District", "ACWD");
      when(counterpartyStore.findByNameIgnoreCase("Alameda County Water District"))
          .thenReturn(Optional.of(existing));

      counterpartyService.addAliasIfAbsent("Alameda County Water District", "acwd");

      verify(counterpartyStore, never()).saveAndFlush(any());
      verify(counterpartyMirror, never()).synchronize(any());
    }
  }
}

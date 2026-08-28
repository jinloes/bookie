package com.bookie.catalog.counterparty.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaCounterpartyMirrorTest {

  @Mock private NormalizedCounterpartyRepository counterpartyRepository;
  @Mock private LegacyPayerMapRepository legacyPayerMapRepository;

  @InjectMocks private JpaCounterpartyMirror mirror;

  @Test
  void createsAnIdentityMapAndAnExactNormalizedCopy() {
    Counterparty counterparty =
        Counterparty.builder()
            .id(7L)
            .name("Example Utility")
            .type(CounterpartyType.COMPANY)
            .aliases(new ArrayList<>(List.of("Utility Alias")))
            .accounts(new HashSet<>(Set.of("account-001")))
            .build();
    when(legacyPayerMapRepository.findById(7L)).thenReturn(Optional.empty());
    when(counterpartyRepository.findById(7L)).thenReturn(Optional.empty());

    mirror.synchronize(counterparty);

    ArgumentCaptor<NormalizedCounterpartyRecord> normalized =
        ArgumentCaptor.forClass(NormalizedCounterpartyRecord.class);
    ArgumentCaptor<LegacyPayerMapRecord> mapping =
        ArgumentCaptor.forClass(LegacyPayerMapRecord.class);
    verify(counterpartyRepository).saveAndFlush(normalized.capture());
    verify(legacyPayerMapRepository).saveAndFlush(mapping.capture());
    assertThat(normalized.getValue().getId()).isEqualTo(7L);
    assertThat(normalized.getValue().getName()).isEqualTo("Example Utility");
    assertThat(normalized.getValue().getType()).isEqualTo(CounterpartyType.COMPANY);
    assertThat(normalized.getValue().getAliases()).containsExactly("Utility Alias");
    assertThat(normalized.getValue().getAccounts()).containsExactly("account-001");
    assertThat(mapping.getValue().getPayerId()).isEqualTo(7L);
    assertThat(mapping.getValue().getCounterpartyId()).isEqualTo(7L);
  }

  @Test
  void rejectsAConflictingLegacyMapWithoutChangingNormalizedRows() {
    Counterparty counterparty = Counterparty.builder().id(7L).name("Example Utility").build();
    LegacyPayerMapRecord conflicting =
        LegacyPayerMapRecord.builder().payerId(7L).counterpartyId(9L).build();
    when(legacyPayerMapRepository.findById(7L)).thenReturn(Optional.of(conflicting));

    assertThatThrownBy(() -> mirror.synchronize(counterparty))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("7");

    verify(counterpartyRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void removesTheMapBeforeTheNormalizedCounterparty() {
    LegacyPayerMapRecord mapping =
        LegacyPayerMapRecord.builder().payerId(7L).counterpartyId(7L).build();
    when(legacyPayerMapRepository.findByCounterpartyId(7L)).thenReturn(Optional.of(mapping));

    mirror.delete(7L);

    var order = inOrder(legacyPayerMapRepository, counterpartyRepository);
    order.verify(legacyPayerMapRepository).delete(mapping);
    order.verify(legacyPayerMapRepository).flush();
    order.verify(counterpartyRepository).deleteById(7L);
    order.verify(counterpartyRepository).flush();
  }
}

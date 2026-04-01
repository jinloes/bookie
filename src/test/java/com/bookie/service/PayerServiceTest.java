package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.repository.PayerRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayerServiceTest {

  @Mock private PayerRepository payerRepository;

  @InjectMocks private PayerService payerService;

  private static Payer payer(String name, String... existingAliases) {
    return Payer.builder()
        .id(1L)
        .name(name)
        .type(PayerType.COMPANY)
        .aliases(new ArrayList<>(List.of(existingAliases)))
        .build();
  }

  @Nested
  class AddAliasIfAbsent {

    @Test
    void newAlias_savedToPayer() {
      Payer p = payer("Alameda County Water District");
      when(payerRepository.findByNameIgnoreCase("Alameda County Water District"))
          .thenReturn(Optional.of(p));

      payerService.addAliasIfAbsent("Alameda County Water District", "ACWD");

      ArgumentCaptor<Payer> captor = ArgumentCaptor.forClass(Payer.class);
      verify(payerRepository).save(captor.capture());
      assertThat(captor.getValue().getAliases()).contains("ACWD");
    }

    @Test
    void aliasMatchesPayerName_notSaved() {
      Payer p = payer("Alameda County Water District");
      when(payerRepository.findByNameIgnoreCase("Alameda County Water District"))
          .thenReturn(Optional.of(p));

      payerService.addAliasIfAbsent(
          "Alameda County Water District", "Alameda County Water District");

      verify(payerRepository, never()).save(any());
    }

    @Test
    void aliasMatchesPayerNameCaseInsensitive_notSaved() {
      Payer p = payer("Alameda County Water District");
      when(payerRepository.findByNameIgnoreCase("Alameda County Water District"))
          .thenReturn(Optional.of(p));

      payerService.addAliasIfAbsent(
          "Alameda County Water District", "alameda county water district");

      verify(payerRepository, never()).save(any());
    }

    @Test
    void aliasAlreadyExists_notSaved() {
      Payer p = payer("Alameda County Water District", "ACWD");
      when(payerRepository.findByNameIgnoreCase("Alameda County Water District"))
          .thenReturn(Optional.of(p));

      payerService.addAliasIfAbsent("Alameda County Water District", "ACWD");

      verify(payerRepository, never()).save(any());
    }

    @Test
    void aliasAlreadyExistsCaseInsensitive_notSaved() {
      Payer p = payer("Alameda County Water District", "ACWD");
      when(payerRepository.findByNameIgnoreCase("Alameda County Water District"))
          .thenReturn(Optional.of(p));

      payerService.addAliasIfAbsent("Alameda County Water District", "acwd");

      verify(payerRepository, never()).save(any());
    }

    @Test
    void payerNotFound_noSave() {
      when(payerRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());

      payerService.addAliasIfAbsent("Unknown", "UNK");

      verify(payerRepository, never()).save(any());
    }
  }
}

package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailParserToolsTest {

  @Mock private PayerRepository payerRepository;
  @Mock private PropertyRepository propertyRepository;
  @Mock private PropertyHistoryService propertyHistoryService;
  @Mock private ParseSessionContext parseSessionContext;

  @InjectMocks private EmailParserTools tools;

  private static Payer payer(long id, String name) {
    return Payer.builder().id(id).name(name).type(PayerType.COMPANY).build();
  }

  @Nested
  class FindPayerByAccountNumber {

    @Test
    void matchingAccount_returnsPayerName() {
      when(payerRepository.findByAccountIn(List.of("41091091")))
          .thenReturn(List.of(payer(1L, "Alameda County Water District")));

      List<String> result = tools.findPayerByAccountNumber(List.of("41091091"));

      assertThat(result).containsExactly("Alameda County Water District");
    }

    @Test
    void normalizesInputBeforeQuery() {
      when(payerRepository.findByAccountIn(List.of("41091091")))
          .thenReturn(List.of(payer(1L, "Alameda County Water District")));

      List<String> result = tools.findPayerByAccountNumber(List.of("  41091091  "));

      assertThat(result).containsExactly("Alameda County Water District");
    }

    @Test
    void stripsMaskedAccountNumbers() {
      when(payerRepository.findByAccountIn(List.of("4191-6")))
          .thenReturn(List.of(payer(1L, "Pacific Gas and Electric Company")));

      List<String> result = tools.findPayerByAccountNumber(List.of("******4191-6"));

      assertThat(result).containsExactly("Pacific Gas and Electric Company");
    }

    @Test
    void noMatch_returnsEmpty() {
      when(payerRepository.findByAccountIn(List.of("unknown"))).thenReturn(List.of());

      assertThat(tools.findPayerByAccountNumber(List.of("unknown"))).isEmpty();
    }

    @Test
    void nullInput_returnsEmpty() {
      assertThat(tools.findPayerByAccountNumber(null)).isEmpty();
    }

    @Test
    void emptyInput_returnsEmpty() {
      assertThat(tools.findPayerByAccountNumber(List.of())).isEmpty();
    }
  }

  @Nested
  class FindPayerByAlias {

    @Test
    void matchingAlias_returnsPayerName() {
      when(payerRepository.findByAliasIgnoreCase("ACWD"))
          .thenReturn(Optional.of(payer(1L, "Alameda County Water District")));

      List<String> result = tools.findPayerByAlias(List.of("ACWD"));

      assertThat(result).containsExactly("Alameda County Water District");
    }

    @Test
    void noMatch_returnsEmptyAndRecordsUnrecognizedAlias() {
      when(payerRepository.findByAliasIgnoreCase("ACWD")).thenReturn(Optional.empty());

      List<String> result = tools.findPayerByAlias(List.of("ACWD"));

      assertThat(result).isEmpty();
      verify(parseSessionContext).addUnrecognizedAlias("ACWD");
    }

    @Test
    void anyMatch_nothingRecordedAsUnrecognized() {
      when(payerRepository.findByAliasIgnoreCase("ACWD"))
          .thenReturn(Optional.of(payer(1L, "Alameda County Water District")));
      when(payerRepository.findByAliasIgnoreCase("UNKNOWN")).thenReturn(Optional.empty());

      List<String> result = tools.findPayerByAlias(List.of("ACWD", "UNKNOWN"));

      assertThat(result).containsExactly("Alameda County Water District");
      verify(parseSessionContext, never()).addUnrecognizedAlias(any());
    }
  }
}

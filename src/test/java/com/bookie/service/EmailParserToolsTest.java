package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import java.util.List;
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
}

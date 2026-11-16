package com.bookie.intake.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.HistoryHint;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.catalog.property.domain.Property;
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

  @Mock private CounterpartyCatalog counterpartyCatalog;
  @Mock private PropertyCatalog propertyCatalog;
  @Mock private ClassificationHistory classificationHistory;

  @InjectMocks private EmailParserTools tools;

  private static Counterparty payer(long id, String name) {
    return Counterparty.builder().id(id).name(name).type(CounterpartyType.COMPANY).build();
  }

  @Nested
  class FindPayerByAccountNumber {

    @Test
    void matchingAccount_returnsPayerName() {
      when(counterpartyCatalog.findByAccounts(List.of("41091091")))
          .thenReturn(List.of(payer(1L, "Alameda County Water District")));

      List<String> result = tools.findPayerByAccountNumber(List.of("41091091"));

      assertThat(result).containsExactly("Alameda County Water District");
    }

    @Test
    void normalizesInputBeforeQuery() {
      when(counterpartyCatalog.findByAccounts(List.of("41091091")))
          .thenReturn(List.of(payer(1L, "Alameda County Water District")));

      List<String> result = tools.findPayerByAccountNumber(List.of("  41091091  "));

      assertThat(result).containsExactly("Alameda County Water District");
    }

    @Test
    void stripsMaskedAccountNumbers() {
      when(counterpartyCatalog.findByAccounts(List.of("4191-6")))
          .thenReturn(List.of(payer(1L, "Pacific Gas and Electric Company")));

      List<String> result = tools.findPayerByAccountNumber(List.of("******4191-6"));

      assertThat(result).containsExactly("Pacific Gas and Electric Company");
    }

    @Test
    void noMatch_returnsEmpty() {
      when(counterpartyCatalog.findByAccounts(List.of("unknown"))).thenReturn(List.of());

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
  class FindPropertyAndHistory {

    @Test
    void findsPropertyByNormalizedAccountNumber() {
      when(propertyCatalog.findByAccounts(List.of("41091091")))
          .thenReturn(List.of(Property.builder().id(1L).name("Wild Indigo").build()));

      assertThat(tools.findPropertyByAccount(List.of(" 41091091 "))).containsExactly("Wild Indigo");
      assertThat(tools.findPropertyByAccount(List.of())).isEmpty();
    }

    @Test
    void delegatesPayerAndPropertyHints() {
      HistoryHint payerHint = new HistoryHint("ACWD", 3, "keyword-history");
      HistoryHint propHint = new HistoryHint("Wild Indigo", 4, "payer-history");
      when(classificationHistory.getPayerHints(List.of("water"))).thenReturn(List.of(payerHint));
      when(classificationHistory.getPropertyHints("ACWD", List.of("water")))
          .thenReturn(List.of(propHint));

      assertThat(tools.getPayerHints(List.of("water"))).containsExactly(payerHint);
      assertThat(tools.getPropertyHints("ACWD", List.of("water"))).containsExactly(propHint);
    }
  }

  @Nested
  class FindPayerByAlias {

    @Test
    void matchingAlias_returnsPayerNameWithNoUnrecognizedAliases() {
      when(counterpartyCatalog.findByAlias("ACWD"))
          .thenReturn(Optional.of(payer(1L, "Alameda County Water District")));

      EmailParserTools.AliasLookupResult result = tools.findPayerByAlias(List.of("ACWD"));

      assertThat(result.matches()).containsExactly("Alameda County Water District");
      assertThat(result.unrecognizedAliases()).isEmpty();
    }

    @Test
    void noMatch_returnsEmptyAndRecordsUnrecognizedAlias() {
      when(counterpartyCatalog.findByAlias("ACWD")).thenReturn(Optional.empty());

      EmailParserTools.AliasLookupResult result = tools.findPayerByAlias(List.of("ACWD"));

      assertThat(result.matches()).isEmpty();
      assertThat(result.unrecognizedAliases()).containsExactly("ACWD");
    }

    @Test
    void anyMatch_nothingReturnedAsUnrecognized() {
      when(counterpartyCatalog.findByAlias("ACWD"))
          .thenReturn(Optional.of(payer(1L, "Alameda County Water District")));
      when(counterpartyCatalog.findByAlias("UNKNOWN")).thenReturn(Optional.empty());

      EmailParserTools.AliasLookupResult result =
          tools.findPayerByAlias(List.of("ACWD", "UNKNOWN"));

      assertThat(result.matches()).containsExactly("Alameda County Water District");
      assertThat(result.unrecognizedAliases()).isEmpty();
    }

    @Test
    void emptyOrNullInput_returnsEmptyLists() {
      assertThat(tools.findPayerByAlias(null))
          .isEqualTo(new EmailParserTools.AliasLookupResult(List.of(), List.of()));
      assertThat(tools.findPayerByAlias(List.of()))
          .isEqualTo(new EmailParserTools.AliasLookupResult(List.of(), List.of()));
    }
  }
}

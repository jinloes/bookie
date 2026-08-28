package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.property.application.PropertyCatalog;
import com.bookie.model.HistoryHint;
import com.bookie.model.TransactionDirection;
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
  @Mock private ParseSessionContext parseSessionContext;

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
  class GetCategoryForPayer {

    @Test
    void matchingPayer_returnsCategories() {
      when(classificationHistory.getCategoryForPayer("Bridgepointe HOA"))
          .thenReturn(List.of(new HistoryHint("MANAGEMENT_FEES", 5, "payer-category-history")));

      List<HistoryHint> result = tools.getCategoryForPayer(List.of("Bridgepointe HOA"));

      assertThat(result).extracting(HistoryHint::value).containsExactly("MANAGEMENT_FEES");
    }

    @Nested
    class ActivityAwareHistory {

      @Test
      void delegatesActivityHintLookup() {
        HistoryHint hint = new HistoryHint("Teaching", 4, "activity-keyword-history");
        when(classificationHistory.getActivityHints(List.of("pay-demo-001")))
            .thenReturn(List.of(hint));

        assertThat(tools.getActivityHints(List.of("pay-demo-001"))).containsExactly(hint);
      }

      @Test
      void delegatesCategoryLookupWithActivityAndDirection() {
        HistoryHint hint =
            new HistoryHint("EDUCATOR_EXPENSES", 3, "activity-category-keyword-history");
        when(classificationHistory.getFinancialCategoryHints(
                42L, TransactionDirection.EXPENSE, List.of("edu-demo-001")))
            .thenReturn(List.of(hint));

        assertThat(
                tools.getFinancialCategoryHints(
                    42L, TransactionDirection.EXPENSE, List.of("edu-demo-001")))
            .containsExactly(hint);
      }
    }

    @Test
    void nullInput_returnsEmpty() {
      assertThat(tools.getCategoryForPayer(null)).isEmpty();
    }

    @Test
    void emptyInput_returnsEmpty() {
      assertThat(tools.getCategoryForPayer(List.of())).isEmpty();
    }

    @Test
    void onlyFirstElementUsed() {
      when(classificationHistory.getCategoryForPayer("First Payer"))
          .thenReturn(List.of(new HistoryHint("UTILITIES", 3, "payer-category-history")));

      List<HistoryHint> result = tools.getCategoryForPayer(List.of("First Payer", "Second Payer"));

      assertThat(result).extracting(HistoryHint::value).containsExactly("UTILITIES");
    }
  }

  @Nested
  class FindPayerByAlias {

    @Test
    void matchingAlias_returnsPayerName() {
      when(counterpartyCatalog.findByAlias("ACWD"))
          .thenReturn(Optional.of(payer(1L, "Alameda County Water District")));

      List<String> result = tools.findPayerByAlias(List.of("ACWD"));

      assertThat(result).containsExactly("Alameda County Water District");
    }

    @Test
    void noMatch_returnsEmptyAndRecordsUnrecognizedAlias() {
      when(counterpartyCatalog.findByAlias("ACWD")).thenReturn(Optional.empty());

      List<String> result = tools.findPayerByAlias(List.of("ACWD"));

      assertThat(result).isEmpty();
      verify(parseSessionContext).addUnrecognizedAlias("ACWD");
    }

    @Test
    void anyMatch_nothingRecordedAsUnrecognized() {
      when(counterpartyCatalog.findByAlias("ACWD"))
          .thenReturn(Optional.of(payer(1L, "Alameda County Water District")));
      when(counterpartyCatalog.findByAlias("UNKNOWN")).thenReturn(Optional.empty());

      List<String> result = tools.findPayerByAlias(List.of("ACWD", "UNKNOWN"));

      assertThat(result).containsExactly("Alameda County Water District");
      verify(parseSessionContext, never()).addUnrecognizedAlias(any());
    }
  }
}

package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.HistoryHint;
import com.bookie.model.TransactionDirection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailParserToolDefinitionsTest {

  @Mock private EmailParserTools tools;

  @Nested
  class CreateTools {

    @Test
    void returnsExpectedToolSet() {
      EmailParserToolDefinitions definitions = new EmailParserToolDefinitions(tools);

      var toolNames = definitions.createTools().stream().map(t -> t.name()).toList();

      assertThat(toolNames)
          .containsExactly(
              "getActivityHints",
              "getFinancialCategoryHints",
              "findPayerByAccountNumber",
              "findPayerByAlias",
              "getPayerHints",
              "findPropertyByAccount",
              "getPropertyHints",
              "getCategoryHints",
              "getCategoryForPayer");
    }

    @Test
    void financialCategoryHints_handlerScopesLookupByActivityAndDirection() throws Exception {
      EmailParserToolDefinitions definitions = new EmailParserToolDefinitions(tools);
      HistoryHint hint =
          new HistoryHint("EDUCATOR_EXPENSES", 3, "activity-category-keyword-history");
      when(tools.getFinancialCategoryHints(
              42L, TransactionDirection.EXPENSE, List.of("edu-demo-001")))
          .thenReturn(List.of(hint));
      var tool =
          definitions.createTools().stream()
              .filter(t -> t.name().equals("getFinancialCategoryHints"))
              .findFirst()
              .orElseThrow();

      Object result =
          tool.handler()
              .apply(
                  Map.of(
                      "activityId",
                      42,
                      "direction",
                      "EXPENSE",
                      "keywords",
                      List.of("edu-demo-001")));

      assertThat(result).isEqualTo(Map.of("hints", List.of(hint)));
      verify(tools)
          .getFinancialCategoryHints(42L, TransactionDirection.EXPENSE, List.of("edu-demo-001"));
    }

    @Test
    void findPayerByAccountNumber_handlerUsesToolMethodAndReturnsMatches() throws Exception {
      EmailParserToolDefinitions definitions = new EmailParserToolDefinitions(tools);
      when(tools.findPayerByAccountNumber(List.of("41091091"))).thenReturn(List.of("ACWD"));
      var tool =
          definitions.createTools().stream()
              .filter(t -> t.name().equals("findPayerByAccountNumber"))
              .findFirst()
              .orElseThrow();

      Object result = tool.handler().apply(Map.of("accountNumbers", List.of("41091091")));

      assertThat(result).isEqualTo(Map.of("matches", List.of("ACWD")));
      verify(tools).findPayerByAccountNumber(List.of("41091091"));
    }

    @Test
    void getCategoryForPayer_withNullPayerNameUsesEmptyList() throws Exception {
      EmailParserToolDefinitions definitions = new EmailParserToolDefinitions(tools);
      when(tools.getCategoryForPayer(List.of()))
          .thenReturn(List.of(new HistoryHint("UTILITIES", 3, "payer-category-history")));
      var tool =
          definitions.createTools().stream()
              .filter(t -> t.name().equals("getCategoryForPayer"))
              .findFirst()
              .orElseThrow();

      Map<String, Object> argsWithNullPayer = new HashMap<>();
      argsWithNullPayer.put("payerName", null);
      Object result = tool.handler().apply(argsWithNullPayer);

      assertThat(result)
          .isEqualTo(
              Map.of("hints", List.of(new HistoryHint("UTILITIES", 3, "payer-category-history"))));
      verify(tools).getCategoryForPayer(List.of());
    }
  }
}

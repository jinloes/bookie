package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.HistoryHint;
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
              "findPayerByAccountNumber",
              "findPayerByAlias",
              "getPayerHints",
              "findPropertyByAccount",
              "getPropertyHints",
              "getCategoryHints",
              "getCategoryForPayer");
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

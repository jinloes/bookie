package com.bookie.service;

import com.bookie.model.TransactionDirection;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailParserToolDefinitions {

  private final EmailParserTools tools;

  public List<LlmToolDefinition> createTools() {
    return List.of(
        LlmToolDefinition.builder()
            .name("getActivityHints")
            .description(
                "Use this when no financial activity was supplied by the import context and stable email keywords may match confirmed activity history; use a result only when it is unambiguous.")
            .parameters(stringArrayParamSchema("keywords"))
            .handler(args -> Map.of("hints", tools.getActivityHints(castList(args, "keywords"))))
            .build(),
        LlmToolDefinition.builder()
            .name("getFinancialCategoryHints")
            .description(
                "Use this after a financial activity and direction are known to retrieve category history scoped to that activity; use the exact returned category key.")
            .parameters(financialCategoryHintsParamSchema())
            .handler(
                args ->
                    Map.of(
                        "hints",
                        tools.getFinancialCategoryHints(
                            castLong(args, "activityId"),
                            castDirection(args, "direction"),
                            castList(args, "keywords"))))
            .build(),
        LlmToolDefinition.builder()
            .name("findPayerByAccountNumber")
            .description(
                "Use this when you have account numbers and need a canonical payer name before other payer lookups.")
            .parameters(stringArrayParamSchema("accountNumbers"))
            .handler(
                args ->
                    Map.of(
                        "matches",
                        tools.findPayerByAccountNumber(castList(args, "accountNumbers"))))
            .build(),
        LlmToolDefinition.builder()
            .name("findPayerByAlias")
            .description(
                "Use this when account-number and exact-name payer lookups failed; pass possible aliases from the email.")
            .parameters(stringArrayParamSchema("aliases"))
            .handler(args -> Map.of("matches", tools.findPayerByAlias(castList(args, "aliases"))))
            .build(),
        LlmToolDefinition.builder()
            .name("getPayerHints")
            .description(
                "Use this when payer account and alias lookups return empty and you need history-based payer candidates from keywords.")
            .parameters(stringArrayParamSchema("keywords"))
            .handler(args -> Map.of("hints", tools.getPayerHints(castList(args, "keywords"))))
            .build(),
        LlmToolDefinition.builder()
            .name("findPropertyByAccount")
            .description(
                "Use this when you have account numbers and need a canonical property name before using property history.")
            .parameters(stringArrayParamSchema("accountNumbers"))
            .handler(
                args ->
                    Map.of(
                        "matches", tools.findPropertyByAccount(castList(args, "accountNumbers"))))
            .build(),
        LlmToolDefinition.builder()
            .name("getPropertyHints")
            .description(
                "Use this when property account-number lookup is empty; provide payerName and keywords for history-based property candidates.")
            .parameters(propertyHintsParamSchema())
            .handler(
                args ->
                    Map.of(
                        "hints",
                        tools.getPropertyHints(
                            castString(args, "payerName"), castList(args, "keywords"))))
            .build(),
        LlmToolDefinition.builder()
            .name("getCategoryHints")
            .description(
                "Use this when you need category candidates from keyword history before falling back to your own guess.")
            .parameters(stringArrayParamSchema("keywords"))
            .handler(args -> Map.of("hints", tools.getCategoryHints(castList(args, "keywords"))))
            .build(),
        LlmToolDefinition.builder()
            .name("getCategoryForPayer")
            .description(
                "Use this when keyword category hints are empty; pass the canonical payer name to get strong payer-category history.")
            .parameters(stringParamSchema("payerName"))
            .handler(
                args ->
                    Map.of(
                        "hints",
                        tools.getCategoryForPayer(
                            castString(args, "payerName") == null
                                ? List.of()
                                : List.of(castString(args, "payerName")))))
            .build());
  }

  private static Map<String, Object> stringArrayParamSchema(String fieldName) {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(fieldName, Map.of("type", "array", "items", Map.of("type", "string"))),
        "required",
        List.of(fieldName));
  }

  private static Map<String, Object> stringParamSchema(String fieldName) {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(fieldName, Map.of("type", "string")),
        "required",
        List.of(fieldName));
  }

  private static Map<String, Object> propertyHintsParamSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "payerName",
            Map.of("type", "string"),
            "keywords",
            Map.of("type", "array", "items", Map.of("type", "string"))),
        "required",
        List.of("keywords"));
  }

  private static Map<String, Object> financialCategoryHintsParamSchema() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "activityId",
            Map.of("type", "integer", "format", "int64"),
            "direction",
            Map.of("type", "string", "enum", List.of("INCOME", "EXPENSE")),
            "keywords",
            Map.of("type", "array", "items", Map.of("type", "string"))),
        "required",
        List.of("activityId", "direction", "keywords"));
  }

  @SuppressWarnings("unchecked")
  private static List<String> castList(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value instanceof List<?> list ? (List<String>) list : List.of();
  }

  private static String castString(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value instanceof String text ? text : null;
  }

  private static Long castLong(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value instanceof Number number ? number.longValue() : null;
  }

  private static TransactionDirection castDirection(Map<String, Object> args, String key) {
    String value = castString(args, key);
    if (value == null) {
      return null;
    }
    try {
      return TransactionDirection.valueOf(value);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }
}

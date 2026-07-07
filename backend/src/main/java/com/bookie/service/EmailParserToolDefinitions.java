package com.bookie.service;

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

  @SuppressWarnings("unchecked")
  private static List<String> castList(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value instanceof List<?> list ? (List<String>) list : List.of();
  }

  private static String castString(Map<String, Object> args, String key) {
    Object value = args.get(key);
    return value instanceof String text ? text : null;
  }
}

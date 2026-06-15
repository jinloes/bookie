package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookie.model.EmailType;
import com.bookie.model.Payer;
import com.bookie.model.PayerType;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@Tag("llm")
@EnabledIfEnvironmentVariable(named = "BOOKIE_LLM_TESTS", matches = "true")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:bookie-llm;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "ai.model.chat=gpt-5-mini",
      "ai.tools.email-parser.enabled=true",
      "ai.tools.trace-events=true"
    })
@Transactional
class EmailParserLlmTest {

  @Autowired private EmailParserService emailParserService;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private PayerRepository payerRepository;
  @Autowired private CopilotToolEventTrace toolEventTrace;

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  void parsesExpenseEmailWithRealLlm() {
    toolEventTrace.clear();
    propertyRepository.save(
        Property.builder()
            .name("Test Property")
            .address("123 Test Ave, Testville, CA 90000")
            .type(PropertyType.SINGLE_FAMILY)
            .build());
    payerRepository.save(
        Payer.builder().name("Test Water Company").type(PayerType.COMPANY).build());

    String subject = "Test Water Company bill for 123 Test Ave";
    String body =
        """
        Account Number: 12345678
        Service Address: 123 Test Ave, Testville, CA 90000
        Amount Due: $157.64
        Thank you for your payment.
        """;

    var suggestion = emailParserService.suggestFromEmail(subject, body, "2026-03-17");

    assertThat(suggestion.emailType()).isEqualTo(EmailType.EXPENSE);
    assertThat(suggestion.payerName()).isEqualTo("Test Water Company");
    assertThat(suggestion.propertyName()).isEqualTo("Test Property");
    assertThat(suggestion.amount()).isNotNull();
    assertThat(suggestion.amount()).isGreaterThan(0);
    assertToolWasInvoked();
  }

  @Test
  @Timeout(value = 2, unit = TimeUnit.MINUTES)
  void parsesIncomeRentReceiptWithRealLlm() {
    toolEventTrace.clear();
    propertyRepository.save(
        Property.builder()
            .name("Test Property")
            .address("100 Main St, Testville, CA 90000")
            .type(PropertyType.SINGLE_FAMILY)
            .build());

    String subject = "123 Test Ave June rent receipt 2026";
    String body =
        """
        Tenant: Jordan Smith
        Rent payment received for June 2026
        Amount: $2500.00
        """;

    var suggestion = emailParserService.suggestFromEmail(subject, body, "2026-06-13");

    assertThat(suggestion.emailType()).isEqualTo(EmailType.INCOME);
    assertThat(suggestion.payerName()).isEqualTo("Jordan Smith");
    assertThat(suggestion.propertyName()).isEqualTo("Test Property");
    assertThat(suggestion.category()).isNull();
    assertThat(suggestion.amount()).isNotNull();
    assertThat(suggestion.amount()).isGreaterThan(0);
  }

  private void assertToolWasInvoked() {
    Set<String> expectedTools =
        Set.of(
            "findPayerByAccountNumber",
            "findPayerByAlias",
            "getPayerHints",
            "findPropertyByAccount",
            "getPropertyHints",
            "getCategoryHints",
            "getCategoryForPayer");
    assertThat(toolEventTrace.snapshotToolStarts()).anyMatch(expectedTools::contains);
  }
}

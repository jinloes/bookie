package com.bookie.catalog.classification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.application.ConfirmedClassification;
import com.bookie.catalog.classification.application.HistoryHint;
import com.bookie.catalog.classification.domain.EmailKeywordCategoryHistory;
import com.bookie.catalog.classification.domain.EmailKeywordClassificationHistory;
import com.bookie.catalog.classification.domain.EmailKeywordPayerHistory;
import com.bookie.catalog.classification.domain.EmailKeywordPropertyHistory;
import com.bookie.catalog.classification.domain.ExpenseCategory;
import com.bookie.catalog.classification.domain.ParsedEmailKeywords;
import com.bookie.catalog.classification.domain.PayerCategoryHistory;
import com.bookie.catalog.classification.domain.PayerPropertyHistory;
import com.bookie.catalog.counterparty.application.CounterpartyStore;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.counterparty.domain.CounterpartyType;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.catalog.property.application.PropertyStore;
import com.bookie.catalog.property.domain.Property;
import com.bookie.catalog.property.domain.PropertyType;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class JpaClassificationHistoryTest {

  @Mock private PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  @Mock private PayerCategoryHistoryRepository payerCategoryHistoryRepo;
  @Mock private EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  @Mock private EmailKeywordPayerHistoryRepository keywordPayerHistoryRepo;
  @Mock private EmailKeywordCategoryHistoryRepository keywordCategoryHistoryRepo;
  @Mock private EmailKeywordClassificationHistoryRepository keywordClassificationHistoryRepo;
  @Mock private ParsedEmailKeywordsRepository parsedKeywordsRepo;
  @Mock private CounterpartyStore counterpartyStore;
  @Mock private PropertyStore propertyStore;
  @Mock private EntityManager entityManager;

  @InjectMocks private JpaClassificationHistory service;

  /** Mutable expense fixture recorded as the entity-free port input. */
  private static final class ConfirmedExpense {
    private Counterparty payer;
    private Property property;
    private ExpenseCategory category;
    private String sourceId;

    void setPayer(Counterparty payer) {
      this.payer = payer;
    }

    void setProperty(Property property) {
      this.property = property;
    }

    void setCategory(ExpenseCategory category) {
      this.category = category;
    }

    void setSourceId(String sourceId) {
      this.sourceId = sourceId;
    }

    ConfirmedClassification toClassification() {
      return ConfirmedClassification.builder()
          .kind(ConfirmedClassification.Kind.EXPENSE)
          .sourceId(sourceId)
          .property(property)
          .counterparty(payer)
          .legacyExpenseCategory(category == null ? null : category.name())
          .build();
    }
  }

  private static Counterparty payer(long id, String name) {
    return Counterparty.builder().id(id).name(name).type(CounterpartyType.COMPANY).build();
  }

  private static Property property(long id, String name) {
    return Property.builder()
        .id(id)
        .name(name)
        .address(name)
        .type(PropertyType.SINGLE_FAMILY)
        .build();
  }

  @Nested
  class StoreKeywords {

    @Test
    void savesNormalizedKeywords() {
      service.storeKeywords("msg1", List.of("ACC-7891", " INV-001 "));

      ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
      verify(parsedKeywordsRepo).saveAll(captor.capture());
      assertThat(captor.getValue())
          .extracting("keyword")
          .containsExactlyInAnyOrder("acc-7891", "inv-001");
    }

    @Test
    void nullKeywords_skipped() {
      service.storeKeywords("msg1", null);
      verify(parsedKeywordsRepo, never()).save(any());
    }

    @Test
    void blankKeywords_filtered() {
      service.storeKeywords("msg1", List.of("  ", ""));
      verify(parsedKeywordsRepo, never()).save(any());
    }

    @Test
    void stripsMaskedAccountNumbers() {
      service.storeKeywords("msg1", List.of("******4191-6", "****4431"));

      ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
      verify(parsedKeywordsRepo).saveAll(captor.capture());
      assertThat(captor.getValue())
          .extracting("keyword")
          .containsExactlyInAnyOrder("4191-6", "4431");
    }
  }

  @Nested
  class Record {

    @Test
    void noProperty_skipsRecording() {
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setProperty(null);

      service.record(expense.toClassification());

      verify(payerPropertyHistoryRepo, never()).save(any());
    }

    @Test
    void withPayer_upsertsNewPayerPropertyEntry() {
      Counterparty payer = payer(1L, "Bob's Plumbing");
      Property prop = property(10L, "123 Main St");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setPayer(payer);
      expense.setProperty(prop);

      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());

      service.record(expense.toClassification());

      ArgumentCaptor<PayerPropertyHistory> captor =
          ArgumentCaptor.forClass(PayerPropertyHistory.class);
      verify(payerPropertyHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getOccurrences()).isEqualTo(1);
      assertThat(captor.getValue().getProperty()).isEqualTo(prop);
    }

    @Test
    void withPayer_incrementsExistingPayerPropertyEntry() {
      Counterparty payer = payer(1L, "Bob's Plumbing");
      Property prop = property(10L, "123 Main St");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setPayer(payer);
      expense.setProperty(prop);

      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      PayerPropertyHistory existing =
          PayerPropertyHistory.builder().id(1L).payer(payer).property(prop).occurrences(3).build();
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.of(existing));

      service.record(expense.toClassification());

      assertThat(existing.getOccurrences()).isEqualTo(4);
      verify(payerPropertyHistoryRepo).save(existing);
    }

    @Test
    void withPayerAndCategory_upsertsNewPayerCategoryEntry() {
      Counterparty payer = payer(1L, "ACWD");
      Property prop = property(10L, "123 Main St");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setCategory(ExpenseCategory.UTILITIES);

      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());
      when(payerCategoryHistoryRepo.findByPayerAndCategory(payer, ExpenseCategory.UTILITIES))
          .thenReturn(Optional.empty());

      service.record(expense.toClassification());

      ArgumentCaptor<PayerCategoryHistory> captor =
          ArgumentCaptor.forClass(PayerCategoryHistory.class);
      verify(payerCategoryHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getPayer()).isEqualTo(payer);
      assertThat(captor.getValue().getCategory()).isEqualTo(ExpenseCategory.UTILITIES);
      assertThat(captor.getValue().getOccurrences()).isEqualTo(1);
    }

    @Test
    void withPayerAndCategory_incrementsExistingPayerCategoryEntry() {
      Counterparty payer = payer(1L, "ACWD");
      Property prop = property(10L, "123 Main St");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setCategory(ExpenseCategory.UTILITIES);

      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());
      PayerCategoryHistory existing =
          PayerCategoryHistory.builder()
              .id(1L)
              .payer(payer)
              .category(ExpenseCategory.UTILITIES)
              .occurrences(5)
              .build();
      when(payerCategoryHistoryRepo.findByPayerAndCategory(payer, ExpenseCategory.UTILITIES))
          .thenReturn(Optional.of(existing));

      service.record(expense.toClassification());

      assertThat(existing.getOccurrences()).isEqualTo(6);
      verify(payerCategoryHistoryRepo).save(existing);
    }

    @Test
    void withoutCategory_skipsPayerCategoryRecording() {
      Counterparty payer = payer(1L, "ACWD");
      Property prop = property(10L, "123 Main St");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setCategory(null);

      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());

      service.record(expense.toClassification());

      verify(payerCategoryHistoryRepo, never()).save(any());
    }

    @Test
    void outlookEmail_recordsKeywordPropertyAndPayerAssociations() {
      Counterparty payer = payer(1L, "National Grid");
      Property prop = property(10L, "456 Oak Ave");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setSourceId("msg1");

      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(any(), any()))
          .thenReturn(Optional.empty());
      when(parsedKeywordsRepo.findBySourceId("msg1"))
          .thenReturn(
              List.of(
                  ParsedEmailKeywords.builder().id(1L).sourceId("msg1").keyword("acc-7891").build(),
                  ParsedEmailKeywords.builder()
                      .id(2L)
                      .sourceId("msg1")
                      .keyword("inv-001")
                      .build()));
      when(keywordPropertyHistoryRepo.findByKeywordInAndPropertyId(any(), any()))
          .thenReturn(List.of());
      when(keywordPayerHistoryRepo.findByKeywordInAndPayer(any(), any())).thenReturn(List.of());

      service.record(expense.toClassification());

      verify(keywordPropertyHistoryRepo, org.mockito.Mockito.times(2)).save(any());
      verify(keywordPayerHistoryRepo, org.mockito.Mockito.times(2)).save(any());
      verify(keywordCategoryHistoryRepo, never()).save(any());
      verify(parsedKeywordsRepo).deleteBySourceId("msg1");
    }

    @Test
    void outlookEmail_recordsKeywordCategoryWhenCategoryPresent() {
      Counterparty payer = payer(1L, "Republic Services");
      Property prop = property(10L, "123 Main St");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setCategory(ExpenseCategory.UTILITIES);
      expense.setSourceId("msg1");

      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(any(), any()))
          .thenReturn(Optional.empty());
      when(payerCategoryHistoryRepo.findByPayerAndCategory(any(), any()))
          .thenReturn(Optional.empty());
      when(parsedKeywordsRepo.findBySourceId("msg1"))
          .thenReturn(
              List.of(
                  ParsedEmailKeywords.builder()
                      .id(1L)
                      .sourceId("msg1")
                      .keyword("acc-123")
                      .build()));
      when(keywordPropertyHistoryRepo.findByKeywordInAndPropertyId(any(), any()))
          .thenReturn(List.of());
      when(keywordPayerHistoryRepo.findByKeywordInAndPayer(any(), any())).thenReturn(List.of());
      when(keywordCategoryHistoryRepo.findByKeywordInAndCategory(any(), any()))
          .thenReturn(List.of());

      service.record(expense.toClassification());

      ArgumentCaptor<EmailKeywordCategoryHistory> captor =
          ArgumentCaptor.forClass(EmailKeywordCategoryHistory.class);
      verify(keywordCategoryHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getKeyword()).isEqualTo("acc-123");
      assertThat(captor.getValue().getCategory()).isEqualTo(ExpenseCategory.UTILITIES);
      assertThat(captor.getValue().getOccurrences()).isEqualTo(1);
    }

    @Test
    void outlookEmail_deletesStoredKeywordsAfterRecording() {
      Property prop = property(10L, "123 Main St");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setProperty(prop);
      expense.setSourceId("msg1");

      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(parsedKeywordsRepo.findBySourceId("msg1")).thenReturn(List.of());

      service.record(expense.toClassification());

      verify(parsedKeywordsRepo).deleteBySourceId("msg1");
    }

    @Test
    void nonRentalIncomeRecordsActivityScopedClassificationHistory() {
      FinancialActivity tutoring =
          FinancialActivity.builder()
              .id(50L)
              .name("Tutoring")
              .taxTreatment(TaxTreatment.SCHEDULE_C)
              .active(true)
              .build();
      FinancialCategory tutoringIncome =
          FinancialCategory.builder()
              .id(51L)
              .key("OTHER_INCOME")
              .direction(TransactionDirection.INCOME)
              .taxTreatment(TaxTreatment.SCHEDULE_C)
              .active(true)
              .build();
      ConfirmedClassification income =
          ConfirmedClassification.builder()
              .kind(ConfirmedClassification.Kind.INCOME)
              .sourceId("msg-tutor")
              .activity(tutoring)
              .financialCategoryId(tutoringIncome.getId())
              .build();
      when(parsedKeywordsRepo.findBySourceId("msg-tutor"))
          .thenReturn(
              List.of(
                  ParsedEmailKeywords.builder()
                      .sourceId("msg-tutor")
                      .keyword("tutor-demo-001")
                      .build()));
      when(keywordClassificationHistoryRepo.findByKeywordInAndActivityIdAndFinancialCategoryId(
              List.of("tutor-demo-001"), 50L, 51L))
          .thenReturn(List.of());
      when(entityManager.getReference(FinancialCategory.class, 51L)).thenReturn(tutoringIncome);

      service.record(income);

      ArgumentCaptor<EmailKeywordClassificationHistory> captor =
          ArgumentCaptor.forClass(EmailKeywordClassificationHistory.class);
      verify(keywordClassificationHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getKeyword()).isEqualTo("tutor-demo-001");
      assertThat(captor.getValue().getActivity()).isEqualTo(tutoring);
      assertThat(captor.getValue().getFinancialCategory()).isEqualTo(tutoringIncome);
      assertThat(captor.getValue().getOccurrences()).isEqualTo(1);
      verify(parsedKeywordsRepo).deleteBySourceId("msg-tutor");
    }
  }

  @Nested
  class ConcurrentInsertConflicts {

    private ConfirmedExpense newPayerPropertyEntry() {
      Counterparty payer = payer(1L, "Bob's Plumbing");
      Property prop = property(10L, "123 Main St");
      ConfirmedExpense expense = new ConfirmedExpense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());
      return expense;
    }

    private static DataIntegrityViolationException integrityFailure(String sqlState, int code) {
      SQLException root = new SQLException("conflict", sqlState, code);
      return new DataIntegrityViolationException(
          "conflict",
          new RuntimeException(
              "wrapped",
              new org.hibernate.exception.ConstraintViolationException("conflict", root, "uk")));
    }

    @ParameterizedTest
    @CsvSource({"23505, 0", "23000, 2627", "23000, 2601"})
    void skipsAUniqueViolationFromAConcurrentFirstInsert(String sqlState, int errorCode) {
      ConfirmedExpense expense = newPayerPropertyEntry();
      when(payerPropertyHistoryRepo.save(any())).thenThrow(integrityFailure(sqlState, errorCode));

      assertThatCode(() -> service.record(expense.toClassification())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"23000, 547", "23502, 0"})
    void rethrowsOtherIntegrityViolations(String sqlState, int errorCode) {
      ConfirmedExpense expense = newPayerPropertyEntry();
      DataIntegrityViolationException failure = integrityFailure(sqlState, errorCode);
      when(payerPropertyHistoryRepo.save(any())).thenThrow(failure);

      assertThatThrownBy(() -> service.record(expense.toClassification())).isSameAs(failure);
    }

    @Test
    void rethrowsAnIntegrityViolationWithoutAConstraintCause() {
      ConfirmedExpense expense = newPayerPropertyEntry();
      DataIntegrityViolationException failure = new DataIntegrityViolationException("unknown");
      when(payerPropertyHistoryRepo.save(any())).thenThrow(failure);

      assertThatThrownBy(() -> service.record(expense.toClassification())).isSameAs(failure);
    }
  }

  @Nested
  class RecordConfirmedClassification {

    private final Counterparty payer = payer(1L, "ACWD");
    private final Property prop = property(10L, "123 Main St");

    private ConfirmedClassification expense(Long financialCategoryId) {
      return ConfirmedClassification.builder()
          .kind(ConfirmedClassification.Kind.EXPENSE)
          .sourceId("receipt-1")
          .financialCategoryId(financialCategoryId)
          .property(prop)
          .counterparty(payer)
          .build();
    }

    private void stubAssociations() {
      when(counterpartyStore.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyStore.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());
    }

    @Test
    void derivesTheLegacyCategoryFromTheFinancialCategoryKey() {
      stubAssociations();
      when(entityManager.find(FinancialCategory.class, 20L))
          .thenReturn(FinancialCategory.builder().id(20L).key("UTILITIES").build());
      when(payerCategoryHistoryRepo.findByPayerAndCategory(payer, ExpenseCategory.UTILITIES))
          .thenReturn(Optional.empty());

      service.record(expense(20L));

      ArgumentCaptor<PayerCategoryHistory> captor =
          ArgumentCaptor.forClass(PayerCategoryHistory.class);
      verify(payerCategoryHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getCategory()).isEqualTo(ExpenseCategory.UTILITIES);
      assertThat(captor.getValue().getOccurrences()).isEqualTo(1);
    }

    @Test
    void fallsBackToOtherWhenTheKeyHasNoLegacyCategory() {
      stubAssociations();
      when(entityManager.find(FinancialCategory.class, 21L))
          .thenReturn(FinancialCategory.builder().id(21L).key("SOFTWARE").build());
      when(payerCategoryHistoryRepo.findByPayerAndCategory(payer, ExpenseCategory.OTHER))
          .thenReturn(Optional.empty());

      service.record(expense(21L));

      ArgumentCaptor<PayerCategoryHistory> captor =
          ArgumentCaptor.forClass(PayerCategoryHistory.class);
      verify(payerCategoryHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getCategory()).isEqualTo(ExpenseCategory.OTHER);
    }

    @Test
    void skipsCategoryHistoryWhenTheFinancialCategoryIsUnknown() {
      stubAssociations();
      when(entityManager.find(FinancialCategory.class, 22L)).thenReturn(null);

      service.record(expense(22L));

      verify(payerCategoryHistoryRepo, never()).save(any());
      verify(payerPropertyHistoryRepo).save(any());
    }

    @Test
    void anExplicitLegacyCategoryWinsOverTheDerivedOne() {
      stubAssociations();
      when(payerCategoryHistoryRepo.findByPayerAndCategory(payer, ExpenseCategory.REPAIRS))
          .thenReturn(Optional.empty());

      service.record(
          ConfirmedClassification.builder()
              .kind(ConfirmedClassification.Kind.EXPENSE)
              .sourceId("receipt-1")
              .financialCategoryId(20L)
              .property(prop)
              .counterparty(payer)
              .legacyExpenseCategory("REPAIRS")
              .build());

      verify(entityManager, never()).find(FinancialCategory.class, 20L);
      ArgumentCaptor<PayerCategoryHistory> captor =
          ArgumentCaptor.forClass(PayerCategoryHistory.class);
      verify(payerCategoryHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getCategory()).isEqualTo(ExpenseCategory.REPAIRS);
    }
  }

  @Nested
  class GetActivityAwareHints {

    @Test
    void activityHintsAggregateConfirmedCategoriesWithinOneActivity() {
      FinancialActivity teaching =
          FinancialActivity.builder().id(60L).name("Teaching").active(true).build();
      FinancialCategory wages =
          FinancialCategory.builder()
              .id(61L)
              .key("WAGES")
              .direction(TransactionDirection.INCOME)
              .active(true)
              .build();
      FinancialCategory reimbursement =
          FinancialCategory.builder()
              .id(62L)
              .key("REIMBURSEMENTS")
              .direction(TransactionDirection.INCOME)
              .active(true)
              .build();
      when(keywordClassificationHistoryRepo.findByKeywordInOrderByOccurrencesDesc(
              List.of("pay-demo-001")))
          .thenReturn(
              List.of(
                  classificationHistory("pay-demo-001", teaching, wages, 4),
                  classificationHistory("pay-demo-001", teaching, reimbursement, 2)));

      assertThat(service.getActivityHints(List.of("PAY-DEMO-001")))
          .containsExactly(new HistoryHint("Teaching", 6, "activity-keyword-history"));
    }

    @Test
    void categoryHintsAreScopedToActivityAndDirection() {
      FinancialActivity teaching =
          FinancialActivity.builder().id(70L).name("Teaching").active(true).build();
      FinancialCategory wages =
          FinancialCategory.builder()
              .id(71L)
              .key("WAGES")
              .direction(TransactionDirection.INCOME)
              .active(true)
              .build();
      FinancialCategory educatorExpenses =
          FinancialCategory.builder()
              .id(72L)
              .key("EDUCATOR_EXPENSES")
              .direction(TransactionDirection.EXPENSE)
              .active(true)
              .build();
      when(keywordClassificationHistoryRepo.findByKeywordInAndActivityIdOrderByOccurrencesDesc(
              List.of("edu-demo-001"), 70L))
          .thenReturn(
              List.of(
                  classificationHistory("edu-demo-001", teaching, wages, 9),
                  classificationHistory("edu-demo-001", teaching, educatorExpenses, 3)));

      assertThat(
              service.getFinancialCategoryHints(
                  70L, TransactionDirection.EXPENSE, List.of("EDU-DEMO-001")))
          .containsExactly(
              new HistoryHint("EDUCATOR_EXPENSES", 3, "activity-category-keyword-history"));
    }
  }

  @Nested
  class GetCategoryForPayer {

    @Test
    void returnsFormattedCategoryHints() {
      Counterparty payer = payer(1L, "ACWD");
      when(counterpartyStore.findByNameIgnoreCase("ACWD")).thenReturn(Optional.of(payer));
      when(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerCategoryHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .category(ExpenseCategory.UTILITIES)
                      .occurrences(5)
                      .build()));

      List<HistoryHint> hints = service.getCategoryForPayer("ACWD");

      assertThat(hints).containsExactly(new HistoryHint("UTILITIES", 5, "payer-category-history"));
    }

    @Test
    void resolvesByAlias() {
      Counterparty payer = payer(1L, "Pacific Gas and Electric Company");
      when(counterpartyStore.findByNameIgnoreCase("PG&E")).thenReturn(Optional.empty());
      when(counterpartyStore.findByAliasIgnoreCase("PG&E")).thenReturn(Optional.of(payer));
      when(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerCategoryHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .category(ExpenseCategory.UTILITIES)
                      .occurrences(3)
                      .build()));

      List<HistoryHint> hints = service.getCategoryForPayer("PG&E");

      assertThat(hints).containsExactly(new HistoryHint("UTILITIES", 3, "payer-category-history"));
    }

    @Test
    void unknownPayer_returnsEmpty() {
      when(counterpartyStore.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());
      when(counterpartyStore.findByAliasIgnoreCase("Unknown")).thenReturn(Optional.empty());

      assertThat(service.getCategoryForPayer("Unknown")).isEmpty();
    }

    @Test
    void returnsEmptyWhenTotalUsesFewerThanThree() {
      Counterparty payer = payer(1L, "Amazon");
      when(counterpartyStore.findByNameIgnoreCase("Amazon")).thenReturn(Optional.of(payer));
      when(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerCategoryHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .category(ExpenseCategory.CLEANING_AND_MAINTENANCE)
                      .occurrences(2)
                      .build()));

      assertThat(service.getCategoryForPayer("Amazon")).isEmpty();
    }

    @Test
    void returnsEmptyWhenTopCategoryBelowNinetyPercentThreshold() {
      Counterparty payer = payer(1L, "Amazon");
      when(counterpartyStore.findByNameIgnoreCase("Amazon")).thenReturn(Optional.of(payer));
      // 3 REPAIRS + 1 SUPPLIES = 75% — below the 90% threshold
      when(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerCategoryHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .category(ExpenseCategory.REPAIRS)
                      .occurrences(3)
                      .build(),
                  PayerCategoryHistory.builder()
                      .id(2L)
                      .payer(payer)
                      .category(ExpenseCategory.SUPPLIES)
                      .occurrences(1)
                      .build()));

      assertThat(service.getCategoryForPayer("Amazon")).isEmpty();
    }

    @Test
    void returnsHintWhenTopCategoryMeetsThreshold() {
      Counterparty payer = payer(1L, "PG&E");
      when(counterpartyStore.findByNameIgnoreCase("PG&E")).thenReturn(Optional.of(payer));
      // 9 UTILITIES + 1 TAXES = 90% — exactly at threshold
      when(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerCategoryHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .category(ExpenseCategory.UTILITIES)
                      .occurrences(9)
                      .build(),
                  PayerCategoryHistory.builder()
                      .id(2L)
                      .payer(payer)
                      .category(ExpenseCategory.TAXES)
                      .occurrences(1)
                      .build()));

      assertThat(service.getCategoryForPayer("PG&E"))
          .containsExactly(new HistoryHint("UTILITIES", 9, "payer-category-history"));
    }
  }

  @Nested
  class GetPropertyHints {

    @Test
    void returnsPayerHints() {
      Counterparty payer = payer(1L, "Bob's Plumbing");
      Property prop1 = property(10L, "123 Main St");
      Property prop2 = property(20L, "456 Oak Ave");
      when(counterpartyStore.findByNameIgnoreCase("Bob's Plumbing")).thenReturn(Optional.of(payer));
      when(payerPropertyHistoryRepo.findByPayerIdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerPropertyHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .property(prop1)
                      .occurrences(4)
                      .build(),
                  PayerPropertyHistory.builder()
                      .id(2L)
                      .payer(payer)
                      .property(prop2)
                      .occurrences(1)
                      .build()));

      List<HistoryHint> hints = service.getPropertyHints("Bob's Plumbing", null);

      assertThat(hints)
          .containsExactly(
              new HistoryHint("123 Main St", 4, "payer-history"),
              new HistoryHint("456 Oak Ave", 1, "payer-history"));
    }

    @Test
    void resolvesByAlias() {
      Counterparty payer = payer(1L, "Pacific Gas and Electric Company");
      Property prop = property(10L, "Wild Indigo");
      when(counterpartyStore.findByNameIgnoreCase("PG&E")).thenReturn(Optional.empty());
      when(counterpartyStore.findByAliasIgnoreCase("PG&E")).thenReturn(Optional.of(payer));
      when(payerPropertyHistoryRepo.findByPayerIdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerPropertyHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .property(prop)
                      .occurrences(2)
                      .build()));

      List<HistoryHint> hints = service.getPropertyHints("PG&E", null);

      assertThat(hints).containsExactly(new HistoryHint("Wild Indigo", 2, "payer-history"));
    }

    @Test
    void returnsKeywordHints() {
      Property prop = property(10L, "456 Oak Ave");
      when(keywordPropertyHistoryRepo.findByKeywordInOrderByOccurrencesDesc(List.of("acc-7891")))
          .thenReturn(
              List.of(
                  EmailKeywordPropertyHistory.builder()
                      .id(1L)
                      .keyword("acc-7891")
                      .property(prop)
                      .occurrences(3)
                      .build()));

      List<HistoryHint> hints = service.getPropertyHints(null, List.of("ACC-7891"));

      assertThat(hints).containsExactly(new HistoryHint("456 Oak Ave", 3, "keyword-history"));
    }

    @Test
    void nullPayerAndKeywords_returnsEmpty() {
      assertThat(service.getPropertyHints(null, null)).isEmpty();
    }
  }

  @Nested
  class GetPayerHints {

    @Test
    void returnsFormattedHints() {
      Counterparty payer = payer(1L, "National Grid");
      when(keywordPayerHistoryRepo.findByKeywordInOrderByOccurrencesDesc(List.of("acc-7891")))
          .thenReturn(
              List.of(
                  EmailKeywordPayerHistory.builder()
                      .id(1L)
                      .keyword("acc-7891")
                      .payer(payer)
                      .occurrences(3)
                      .build()));

      List<HistoryHint> hints = service.getPayerHints(List.of("ACC-7891"));

      assertThat(hints).containsExactly(new HistoryHint("National Grid", 3, "keyword-history"));
    }

    @Test
    void emptyKeywords_returnsEmpty() {
      assertThat(service.getPayerHints(List.of())).isEmpty();
    }
  }

  @Nested
  class GetCategoryHints {

    @Test
    void returnsFormattedHints() {
      when(keywordCategoryHistoryRepo.findByKeywordInOrderByOccurrencesDesc(List.of("acc-123")))
          .thenReturn(
              List.of(
                  EmailKeywordCategoryHistory.builder()
                      .id(1L)
                      .keyword("acc-123")
                      .category(ExpenseCategory.UTILITIES)
                      .occurrences(4)
                      .build()));

      List<HistoryHint> hints = service.getCategoryHints(List.of("ACC-123"));

      assertThat(hints).containsExactly(new HistoryHint("UTILITIES", 4, "keyword-history"));
    }

    @Test
    void emptyKeywords_returnsEmpty() {
      assertThat(service.getCategoryHints(List.of())).isEmpty();
    }
  }

  private EmailKeywordClassificationHistory classificationHistory(
      String keyword, FinancialActivity activity, FinancialCategory category, int occurrences) {
    return EmailKeywordClassificationHistory.builder()
        .keyword(keyword)
        .activity(activity)
        .financialCategory(category)
        .occurrences(occurrences)
        .build();
  }
}

package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.model.EmailKeywordPayerHistory;
import com.bookie.model.EmailKeywordPropertyHistory;
import com.bookie.model.Expense;
import com.bookie.model.ExpenseCategory;
import com.bookie.model.ExpenseSource;
import com.bookie.model.ParsedEmailKeywords;
import com.bookie.model.Payer;
import com.bookie.model.PayerCategoryHistory;
import com.bookie.model.PayerPropertyHistory;
import com.bookie.model.PayerType;
import com.bookie.model.Property;
import com.bookie.model.PropertyType;
import com.bookie.repository.EmailKeywordPayerHistoryRepository;
import com.bookie.repository.EmailKeywordPropertyHistoryRepository;
import com.bookie.repository.ParsedEmailKeywordsRepository;
import com.bookie.repository.PayerCategoryHistoryRepository;
import com.bookie.repository.PayerPropertyHistoryRepository;
import com.bookie.repository.PayerRepository;
import com.bookie.repository.PropertyRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertyHistoryServiceTest {

  @Mock private PayerPropertyHistoryRepository payerPropertyHistoryRepo;
  @Mock private PayerCategoryHistoryRepository payerCategoryHistoryRepo;
  @Mock private EmailKeywordPropertyHistoryRepository keywordPropertyHistoryRepo;
  @Mock private EmailKeywordPayerHistoryRepository keywordPayerHistoryRepo;
  @Mock private ParsedEmailKeywordsRepository parsedKeywordsRepo;
  @Mock private PayerRepository payerRepository;
  @Mock private PropertyRepository propertyRepository;

  @InjectMocks private PropertyHistoryService service;

  private static Payer payer(long id, String name) {
    return Payer.builder().id(id).name(name).type(PayerType.COMPANY).build();
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
  }

  @Nested
  class Record {

    @Test
    void noProperty_skipsRecording() {
      Expense expense = new Expense();
      expense.setProperty(null);

      service.record(expense);

      verify(payerPropertyHistoryRepo, never()).save(any());
    }

    @Test
    void withPayer_upsertsNewPayerPropertyEntry() {
      Payer payer = payer(1L, "Bob's Plumbing");
      Property prop = property(10L, "123 Main St");
      Expense expense = new Expense();
      expense.setPayer(payer);
      expense.setProperty(prop);

      when(payerRepository.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyRepository.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());

      service.record(expense);

      ArgumentCaptor<PayerPropertyHistory> captor =
          ArgumentCaptor.forClass(PayerPropertyHistory.class);
      verify(payerPropertyHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getOccurrences()).isEqualTo(1);
      assertThat(captor.getValue().getProperty()).isEqualTo(prop);
    }

    @Test
    void withPayer_incrementsExistingPayerPropertyEntry() {
      Payer payer = payer(1L, "Bob's Plumbing");
      Property prop = property(10L, "123 Main St");
      Expense expense = new Expense();
      expense.setPayer(payer);
      expense.setProperty(prop);

      when(payerRepository.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyRepository.findById(10L)).thenReturn(Optional.of(prop));
      PayerPropertyHistory existing =
          PayerPropertyHistory.builder().id(1L).payer(payer).property(prop).occurrences(3).build();
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.of(existing));

      service.record(expense);

      assertThat(existing.getOccurrences()).isEqualTo(4);
      verify(payerPropertyHistoryRepo).save(existing);
    }

    @Test
    void withPayerAndCategory_upsertsNewPayerCategoryEntry() {
      Payer payer = payer(1L, "ACWD");
      Property prop = property(10L, "123 Main St");
      Expense expense = new Expense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setCategory(ExpenseCategory.UTILITIES);

      when(payerRepository.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyRepository.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());
      when(payerCategoryHistoryRepo.findByPayerAndCategory(payer, ExpenseCategory.UTILITIES))
          .thenReturn(Optional.empty());

      service.record(expense);

      ArgumentCaptor<PayerCategoryHistory> captor =
          ArgumentCaptor.forClass(PayerCategoryHistory.class);
      verify(payerCategoryHistoryRepo).save(captor.capture());
      assertThat(captor.getValue().getPayer()).isEqualTo(payer);
      assertThat(captor.getValue().getCategory()).isEqualTo(ExpenseCategory.UTILITIES);
      assertThat(captor.getValue().getOccurrences()).isEqualTo(1);
    }

    @Test
    void withPayerAndCategory_incrementsExistingPayerCategoryEntry() {
      Payer payer = payer(1L, "ACWD");
      Property prop = property(10L, "123 Main St");
      Expense expense = new Expense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setCategory(ExpenseCategory.UTILITIES);

      when(payerRepository.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyRepository.findById(10L)).thenReturn(Optional.of(prop));
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

      service.record(expense);

      assertThat(existing.getOccurrences()).isEqualTo(6);
      verify(payerCategoryHistoryRepo).save(existing);
    }

    @Test
    void withoutCategory_skipsPayerCategoryRecording() {
      Payer payer = payer(1L, "ACWD");
      Property prop = property(10L, "123 Main St");
      Expense expense = new Expense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setCategory(null);

      when(payerRepository.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyRepository.findById(10L)).thenReturn(Optional.of(prop));
      when(payerPropertyHistoryRepo.findByPayerIdAndPropertyId(1L, 10L))
          .thenReturn(Optional.empty());

      service.record(expense);

      verify(payerCategoryHistoryRepo, never()).save(any());
    }

    @Test
    void outlookEmail_recordsKeywordPropertyAndPayerAssociations() {
      Payer payer = payer(1L, "National Grid");
      Property prop = property(10L, "456 Oak Ave");
      Expense expense = new Expense();
      expense.setPayer(payer);
      expense.setProperty(prop);
      expense.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      expense.setSourceId("msg1");

      when(payerRepository.findById(1L)).thenReturn(Optional.of(payer));
      when(propertyRepository.findById(10L)).thenReturn(Optional.of(prop));
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
      when(keywordPropertyHistoryRepo.findByKeywordAndPropertyId(any(), any()))
          .thenReturn(Optional.empty());
      when(keywordPayerHistoryRepo.findByKeywordAndPayer(any(), any()))
          .thenReturn(Optional.empty());

      service.record(expense);

      verify(keywordPropertyHistoryRepo, org.mockito.Mockito.times(2)).save(any());
      verify(keywordPayerHistoryRepo, org.mockito.Mockito.times(2)).save(any());
      verify(parsedKeywordsRepo).deleteBySourceId("msg1");
    }

    @Test
    void outlookEmail_deletesStoredKeywordsAfterRecording() {
      Property prop = property(10L, "123 Main St");
      Expense expense = new Expense();
      expense.setProperty(prop);
      expense.setSourceType(ExpenseSource.OUTLOOK_EMAIL);
      expense.setSourceId("msg1");

      when(propertyRepository.findById(10L)).thenReturn(Optional.of(prop));
      when(parsedKeywordsRepo.findBySourceId("msg1")).thenReturn(List.of());

      service.record(expense);

      verify(parsedKeywordsRepo).deleteBySourceId("msg1");
    }
  }

  @Nested
  class GetCategoryForPayer {

    @Test
    void returnsFormattedCategoryHints() {
      Payer payer = payer(1L, "ACWD");
      when(payerRepository.findByNameIgnoreCase("ACWD")).thenReturn(Optional.of(payer));
      when(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerCategoryHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .category(ExpenseCategory.UTILITIES)
                      .occurrences(5)
                      .build()));

      List<String> hints = service.getCategoryForPayer("ACWD");

      assertThat(hints).containsExactly("UTILITIES (5 times)");
    }

    @Test
    void resolvesByAlias() {
      Payer payer = payer(1L, "Pacific Gas and Electric Company");
      when(payerRepository.findByNameIgnoreCase("PG&E")).thenReturn(Optional.empty());
      when(payerRepository.findByAliasIgnoreCase("PG&E")).thenReturn(Optional.of(payer));
      when(payerCategoryHistoryRepo.findByPayer_IdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerCategoryHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .category(ExpenseCategory.UTILITIES)
                      .occurrences(3)
                      .build()));

      List<String> hints = service.getCategoryForPayer("PG&E");

      assertThat(hints).containsExactly("UTILITIES (3 times)");
    }

    @Test
    void unknownPayer_returnsEmpty() {
      when(payerRepository.findByNameIgnoreCase("Unknown")).thenReturn(Optional.empty());
      when(payerRepository.findByAliasIgnoreCase("Unknown")).thenReturn(Optional.empty());

      assertThat(service.getCategoryForPayer("Unknown")).isEmpty();
    }
  }

  @Nested
  class GetPropertyHints {

    @Test
    void returnsPayerHints() {
      Payer payer = payer(1L, "Bob's Plumbing");
      Property prop1 = property(10L, "123 Main St");
      Property prop2 = property(20L, "456 Oak Ave");
      when(payerRepository.findByNameIgnoreCase("Bob's Plumbing")).thenReturn(Optional.of(payer));
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

      List<String> hints = service.getPropertyHints("Bob's Plumbing", null);

      assertThat(hints)
          .containsExactly(
              "Bob's Plumbing → 123 Main St (4 times)", "Bob's Plumbing → 456 Oak Ave (1 times)");
    }

    @Test
    void resolvesByAlias() {
      Payer payer = payer(1L, "Pacific Gas and Electric Company");
      Property prop = property(10L, "Wild Indigo");
      when(payerRepository.findByNameIgnoreCase("PG&E")).thenReturn(Optional.empty());
      when(payerRepository.findByAliasIgnoreCase("PG&E")).thenReturn(Optional.of(payer));
      when(payerPropertyHistoryRepo.findByPayerIdOrderByOccurrencesDesc(1L))
          .thenReturn(
              List.of(
                  PayerPropertyHistory.builder()
                      .id(1L)
                      .payer(payer)
                      .property(prop)
                      .occurrences(2)
                      .build()));

      List<String> hints = service.getPropertyHints("PG&E", null);

      assertThat(hints).containsExactly("Pacific Gas and Electric Company → Wild Indigo (2 times)");
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

      List<String> hints = service.getPropertyHints(null, List.of("ACC-7891"));

      assertThat(hints).containsExactly("Keyword 'acc-7891' → 456 Oak Ave (3 times)");
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
      Payer payer = payer(1L, "National Grid");
      when(keywordPayerHistoryRepo.findByKeywordInOrderByOccurrencesDesc(List.of("acc-7891")))
          .thenReturn(
              List.of(
                  EmailKeywordPayerHistory.builder()
                      .id(1L)
                      .keyword("acc-7891")
                      .payer(payer)
                      .occurrences(3)
                      .build()));

      List<String> hints = service.getPayerHints(List.of("ACC-7891"));

      assertThat(hints).containsExactly("Keyword 'acc-7891' → National Grid (3 times)");
    }

    @Test
    void emptyKeywords_returnsEmpty() {
      assertThat(service.getPayerHints(List.of())).isEmpty();
    }
  }
}

package com.bookie.ledger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.domain.ActivityType;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.activity.domain.TaxTreatment;
import com.bookie.catalog.category.domain.NeutralCategory;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.classification.application.ClassificationHistory;
import com.bookie.catalog.classification.application.ConfirmedClassification;
import com.bookie.catalog.counterparty.application.CounterpartyCatalog;
import com.bookie.catalog.counterparty.domain.Counterparty;
import com.bookie.catalog.property.domain.Property;
import com.bookie.ledger.domain.FinancialTransaction;
import com.bookie.ledger.domain.TransactionImportReference;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LedgerTransactionServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-09-22T19:00:00Z"), ZoneOffset.UTC);
  private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

  @Mock private LedgerTransactionStore transactionStore;
  @Mock private LedgerReferenceResolver referenceResolver;
  @Mock private ClassificationHistory classificationHistory;
  @Mock private CounterpartyCatalog counterpartyCatalog;
  @Mock private ObjectProvider<CounterpartyCatalog> counterpartyCatalogProvider;
  @Mock private ApplicationEventPublisher events;

  private LedgerTransactionService transactionService;
  private FinancialActivity activity;
  private NeutralCategory category;
  private Counterparty counterparty;
  private LedgerTransactionInput input;

  @BeforeEach
  void setUp() {
    transactionService =
        new LedgerTransactionService(
            CLOCK,
            transactionStore,
            referenceResolver,
            classificationHistory,
            counterpartyCatalogProvider,
            events);
    lenient().when(counterpartyCatalogProvider.getObject()).thenReturn(counterpartyCatalog);
    activity =
        FinancialActivity.builder()
            .id(4L)
            .name("Oak rental")
            .activityType(ActivityType.RENTAL)
            .taxTreatment(TaxTreatment.SCHEDULE_E)
            .property(Property.builder().id(5L).name("Oak").build())
            .active(true)
            .build();
    category =
        NeutralCategory.builder()
            .id(7L)
            .key("RENTAL_INCOME")
            .label("Rental income")
            .direction(TransactionDirection.INCOME)
            .active(true)
            .system(true)
            .build();
    counterparty = Counterparty.builder().id(20L).name("Tenant").build();
    input =
        LedgerTransactionInput.builder()
            .amount(new BigDecimal("100.00"))
            .direction(TransactionDirection.INCOME)
            .date(DATE)
            .description(" Rent ")
            .activityId(4L)
            .neutralCategoryId(7L)
            .counterpartyId(20L)
            .origin("VENMO")
            .externalId(" venmo-1 ")
            .sourceLabel(" Tenant ")
            .attachmentExternalId("file-1")
            .attachmentFileName("statement.pdf")
            .build();
  }

  @Nested
  class Create {

    @Test
    void writesOneNormalizedTransactionAndRecordsItsClassification() {
      stubResolution();
      stubSave();
      when(transactionStore.findBySourceIdentity("VENMO", "venmo-1")).thenReturn(Optional.empty());
      when(counterpartyCatalog.findOptionalById(20L)).thenReturn(Optional.of(counterparty));

      FinancialTransaction created = transactionService.create(input);

      assertThat(created.getId()).isEqualTo(31L);
      assertThat(created.getAmount()).isEqualByComparingTo("100.00");
      assertThat(created.getDescription()).isEqualTo("Rent");
      assertThat(created.getActivity()).isSameAs(activity);
      assertThat(created.getNeutralCategory()).isSameAs(category);
      assertThat(created.getCounterpartyId()).isEqualTo(20L);
      assertThat(created.getImportReferences())
          .singleElement()
          .satisfies(
              reference -> {
                assertThat(reference.getOrigin()).isEqualTo("VENMO");
                assertThat(reference.getExternalId()).isEqualTo("venmo-1");
                assertThat(reference.getSourceLabel()).isEqualTo("Tenant");
                assertThat(reference.getParentExternalId()).isNull();
              });
      assertThat(created.getAttachments())
          .singleElement()
          .satisfies(
              attachment -> {
                assertThat(attachment.getStorageProvider()).isEqualTo("ONEDRIVE");
                assertThat(attachment.getExternalId()).isEqualTo("file-1");
                assertThat(attachment.getFileName()).isEqualTo("statement.pdf");
              });
      ArgumentCaptor<ConfirmedClassification> classification =
          ArgumentCaptor.forClass(ConfirmedClassification.class);
      verify(classificationHistory).record(classification.capture());
      assertThat(classification.getValue())
          .isEqualTo(
              ConfirmedClassification.builder()
                  .kind(ConfirmedClassification.Kind.INCOME)
                  .sourceId("venmo-1")
                  .activity(activity)
                  .financialCategoryId(17L)
                  .property(activity.getProperty())
                  .counterparty(counterparty)
                  .build());
      // An attached non-RECEIPT transaction publishes nothing to file.
      verifyNoInteractions(events);
    }

    @Test
    void keepsTheParentIdentityAnInboxSaveSupplies() {
      stubResolution();
      stubSave();
      when(transactionStore.findBySourceIdentity("VENMO", "venmo-1")).thenReturn(Optional.empty());
      when(counterpartyCatalog.findOptionalById(20L)).thenReturn(Optional.empty());

      FinancialTransaction created =
          transactionService.create(with(builder -> builder.parentExternalId(" parent-1 ")));

      assertThat(parentOf(created)).isEqualTo("parent-1");
    }

    @Test
    void recordsAnExpenseWithoutCounterpartyOrSourceIdentity() {
      NeutralCategory expense =
          NeutralCategory.builder()
              .id(8L)
              .direction(TransactionDirection.EXPENSE)
              .active(true)
              .build();
      when(referenceResolver.resolve(4L, 8L, null, DATE))
          .thenReturn(new ResolvedTransactionReferences(activity, expense, 18L, null));
      stubSave();

      FinancialTransaction created =
          transactionService.create(
              LedgerTransactionInput.builder()
                  .amount(new BigDecimal("5"))
                  .direction(TransactionDirection.EXPENSE)
                  .date(DATE)
                  .description("Paint")
                  .activityId(4L)
                  .neutralCategoryId(8L)
                  .build());

      assertThat(created.getImportReferences()).isEmpty();
      assertThat(created.getAttachments()).isEmpty();
      ArgumentCaptor<ConfirmedClassification> classification =
          ArgumentCaptor.forClass(ConfirmedClassification.class);
      verify(classificationHistory).record(classification.capture());
      assertThat(classification.getValue().kind()).isEqualTo(ConfirmedClassification.Kind.EXPENSE);
      assertThat(classification.getValue().counterparty()).isNull();
      assertThat(classification.getValue().sourceId()).isNull();
      verify(transactionStore, never()).findBySourceIdentity(any(), any());
      verifyNoInteractions(counterpartyCatalog);
    }

    @Test
    void defaultsAnOriginlessSourceLabelToAManualImportReference() {
      stubResolution();
      stubSave();
      when(counterpartyCatalog.findOptionalById(20L)).thenReturn(Optional.empty());

      FinancialTransaction created =
          transactionService.create(
              with(
                  builder ->
                      builder
                          .origin(null)
                          .externalId(null)
                          .attachmentExternalId(null)
                          .attachmentFileName(null)));

      assertThat(created.getImportReferences())
          .singleElement()
          .satisfies(reference -> assertThat(reference.getOrigin()).isEqualTo("MANUAL"));
    }

    @Test
    void rejectsASourceIdentityOwnedByAnotherTransaction() {
      stubResolution();
      when(transactionStore.findBySourceIdentity("VENMO", "venmo-1"))
          .thenReturn(Optional.of(transaction(99L)));

      assertThatThrownBy(() -> transactionService.create(input))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
      verify(transactionStore, never()).save(any());
    }

    @Test
    void rejectsACategoryForTheOtherDirection() {
      when(referenceResolver.resolve(4L, 7L, 20L, DATE))
          .thenReturn(
              new ResolvedTransactionReferences(
                  activity,
                  NeutralCategory.builder()
                      .id(7L)
                      .direction(TransactionDirection.EXPENSE)
                      .active(true)
                      .build(),
                  17L,
                  20L));

      assertThatThrownBy(() -> transactionService.create(input))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("Category direction");
    }

    @Test
    void rejectsAnInactiveCategory() {
      category.setActive(false);
      stubResolution();

      assertThatThrownBy(() -> transactionService.create(input))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("inactive");
    }

    @ParameterizedTest
    @MethodSource("com.bookie.ledger.application.LedgerTransactionServiceTest#invalidInputs")
    void rejectsInvalidInputBeforeResolvingReferences(
        UnaryOperator<LedgerTransactionInput.LedgerTransactionInputBuilder> change,
        String message) {
      assertThatThrownBy(() -> transactionService.create(with(change)))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              failure -> {
                assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(failure.getReason()).contains(message);
              });
      verifyNoInteractions(referenceResolver, transactionStore);
    }

    @Test
    void rejectsAMissingInput() {
      assertThatThrownBy(() -> transactionService.create(null))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("incomplete");
    }
  }

  static List<Object[]> invalidInputs() {
    return List.of(
        invalid(builder -> builder.amount(null), "incomplete"),
        invalid(builder -> builder.direction(null), "incomplete"),
        invalid(builder -> builder.date(null), "incomplete"),
        invalid(builder -> builder.activityId(null), "incomplete"),
        invalid(builder -> builder.neutralCategoryId(null), "incomplete"),
        invalid(builder -> builder.description(" "), "incomplete"),
        invalid(builder -> builder.amount(BigDecimal.ZERO), "positive"),
        invalid(builder -> builder.amount(new BigDecimal("1.234")), "two decimal places"),
        invalid(builder -> builder.attachmentSha256("ABC"), "SHA-256"),
        invalid(builder -> builder.attachmentStorageProvider("S3"), "ONEDRIVE"),
        invalid(
            builder -> builder.direction(TransactionDirection.EXPENSE).sourceLabel("Label"),
            "source labels"));
  }

  private static Object[] invalid(
      UnaryOperator<LedgerTransactionInput.LedgerTransactionInputBuilder> change, String message) {
    return new Object[] {change, message};
  }

  @Nested
  class ReceiptFiling {

    @Test
    void publishesOneRecordedReceiptForFilingAfterCommit() {
      LedgerTransactionInput receipt = receipt("file-1");
      stubResolution();
      stubSave();
      when(transactionStore.findBySourceIdentity("RECEIPT", "receipt-1"))
          .thenReturn(Optional.empty());
      when(counterpartyCatalog.findOptionalById(20L)).thenReturn(Optional.empty());

      transactionService.create(receipt);

      verify(events).publishEvent(new TransactionRecorded(31L, "RECEIPT", "file-1", DATE));
    }

    @Test
    void skipsFilingForAReceiptWithoutAnAttachment() {
      LedgerTransactionInput receipt = receipt(null);
      stubResolution();
      stubSave();
      when(transactionStore.findBySourceIdentity("RECEIPT", "receipt-1"))
          .thenReturn(Optional.empty());
      when(counterpartyCatalog.findOptionalById(20L)).thenReturn(Optional.empty());

      transactionService.create(receipt);

      verifyNoInteractions(events);
    }

    @Test
    void neverFilesTheReceiptOfAnInboxSave() {
      LedgerTransactionInput receipt = receipt("file-1");
      stubResolution();
      stubSave();
      when(transactionStore.findBySourceIdentity("RECEIPT", "receipt-1"))
          .thenReturn(Optional.empty());
      when(counterpartyCatalog.findOptionalById(20L)).thenReturn(Optional.empty());

      FinancialTransaction created = transactionService.createFromInbox(receipt);

      assertThat(created.getId()).isEqualTo(31L);
      assertThat(created.getAttachments())
          .singleElement()
          .satisfies(attachment -> assertThat(attachment.getExternalId()).isEqualTo("file-1"));
      verify(classificationHistory).record(any());
      verifyNoInteractions(events);
    }

    @Test
    void validatesAnInboxSaveLikeAManualCreate() {
      assertThatThrownBy(() -> transactionService.createFromInbox(null))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("incomplete");
      verifyNoInteractions(referenceResolver, transactionStore, events);
    }

    private LedgerTransactionInput receipt(String attachmentExternalId) {
      return with(
          builder ->
              builder
                  .origin("RECEIPT")
                  .externalId("receipt-1")
                  .attachmentExternalId(attachmentExternalId)
                  .attachmentFileName(attachmentExternalId == null ? null : "receipt.pdf"));
    }
  }

  @Nested
  class Update {

    private FinancialTransaction existing;

    @BeforeEach
    void setUpExisting() {
      existing = transaction(31L);
      existing.setVersion(2L);
      existing.replaceImportReference("OUTLOOK_EMAIL", "message-1:attachment-1", null, "message-1");
    }

    @Test
    void rejectsAStaleVersionBeforeResolvingReferences() {
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> transactionService.update(31L, 1L, input))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
      verifyNoInteractions(referenceResolver);
    }

    @Test
    void rejectsAMissingTransaction() {
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> transactionService.update(31L, 2L, input))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectsADirectionChange() {
      existing.setDirection(TransactionDirection.EXPENSE);
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.of(existing));
      stubResolution();

      assertThatThrownBy(() -> transactionService.update(31L, 2L, input))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("direction cannot be changed");
    }

    @Test
    void keepsTheParentWhileTheSourceIdentityIsUnchanged() {
      FinancialTransaction updated =
          update(builder -> builder.origin("OUTLOOK_EMAIL").externalId("message-1:attachment-1"));

      assertThat(parentOf(updated)).isEqualTo("message-1");
      verify(classificationHistory).record(any());
    }

    @Test
    void clearsTheParentWhenTheExternalIdChanges() {
      FinancialTransaction updated =
          update(builder -> builder.origin("OUTLOOK_EMAIL").externalId("message-1:attachment-2"));

      assertThat(parentOf(updated)).isNull();
    }

    @Test
    void clearsTheParentWhenTheOriginChanges() {
      FinancialTransaction updated =
          update(builder -> builder.origin("MANUAL").externalId("message-1:attachment-1"));

      assertThat(parentOf(updated)).isNull();
    }

    @Test
    void treatsAMissingOriginAsManualWhenComparingIdentity() {
      existing.replaceImportReference("MANUAL", "manual-1", null, "parent-1");

      FinancialTransaction updated =
          update(builder -> builder.origin(null).externalId("manual-1").sourceLabel(null));

      assertThat(parentOf(updated)).isEqualTo("parent-1");
    }

    @Test
    void leavesTheParentEmptyWhenTheTransactionHadNoImportReference() {
      existing.replaceImportReference(null, null, null, null);

      FinancialTransaction updated =
          update(builder -> builder.origin("OUTLOOK_EMAIL").externalId("message-2"));

      assertThat(parentOf(updated)).isNull();
    }

    private FinancialTransaction update(
        UnaryOperator<LedgerTransactionInput.LedgerTransactionInputBuilder> change) {
      LedgerTransactionInput request = with(change);
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.of(existing));
      stubResolution();
      if (request.getExternalId() != null) {
        when(transactionStore.findBySourceIdentity(any(), any())).thenReturn(Optional.of(existing));
      }
      when(counterpartyCatalog.findOptionalById(20L)).thenReturn(Optional.of(counterparty));
      when(transactionStore.save(existing)).thenReturn(existing);
      return transactionService.update(31L, 2L, request);
    }
  }

  @Nested
  class Delete {

    @Test
    void tombstonesAtTheClockTimeWithoutRecordingClassification() {
      FinancialTransaction existing = transaction(31L);
      existing.setVersion(2L);
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.of(existing));

      transactionService.delete(31L, 2L);

      assertThat(existing.getDeletedAt()).isEqualTo(LocalDateTime.of(2026, 9, 22, 19, 0));
      verify(transactionStore).save(existing);
      verifyNoInteractions(classificationHistory);
    }

    @Test
    void rejectsAStaleVersion() {
      FinancialTransaction existing = transaction(31L);
      existing.setVersion(2L);
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> transactionService.delete(31L, 1L))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("modified");
      assertThat(existing.getDeletedAt()).isNull();
    }
  }

  @Nested
  class Reads {

    @Test
    void delegatesActiveReadsToTheStore() {
      FinancialTransaction transaction = transaction(31L);
      when(transactionStore.findAllActive()).thenReturn(List.of(transaction));
      when(transactionStore.findAllActiveByDirection(TransactionDirection.INCOME))
          .thenReturn(List.of(transaction));
      when(transactionStore.findActiveById(31L)).thenReturn(Optional.of(transaction));

      assertThat(transactionService.findAll()).containsExactly(transaction);
      assertThat(transactionService.findAll(TransactionDirection.INCOME))
          .containsExactly(transaction);
      assertThat(transactionService.findById(31L)).isSameAs(transaction);
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void reassignsActivityAndDirectionSpecificCategoryWithoutDeletingRecords() {
      FinancialTransaction income = transaction(31L);
      FinancialTransaction expense = transaction(32L);
      expense.setDirection(TransactionDirection.EXPENSE);
      FinancialActivity replacement = FinancialActivity.builder().id(9L).active(true).build();
      NeutralCategory incomeCategory = NeutralCategory.builder().id(70L).build();
      NeutralCategory expenseCategory = NeutralCategory.builder().id(71L).build();
      when(transactionStore.findAllActiveByActivityId(4L)).thenReturn(List.of(income, expense));
      when(referenceResolver.resolveFinancialCategory(9L, 50L))
          .thenReturn(new ResolvedTransactionReferences(replacement, incomeCategory, 50L, null));
      when(referenceResolver.resolveFinancialCategory(9L, 51L))
          .thenReturn(new ResolvedTransactionReferences(replacement, expenseCategory, 51L, null));

      transactionService.reassignActivity(4L, 9L, 50L, 51L);

      assertThat(income.getActivity()).isSameAs(replacement);
      assertThat(income.getNeutralCategory()).isSameAs(incomeCategory);
      assertThat(expense.getNeutralCategory()).isSameAs(expenseCategory);
      assertThat(income.getDeletedAt()).isNull();
      verify(transactionStore).save(income);
      verify(transactionStore).save(expense);
    }

    @Test
    void detachesACounterpartyFromItsActiveTransactions() {
      FinancialTransaction transaction = transaction(31L);
      when(transactionStore.findAllActiveByCounterpartyId(20L)).thenReturn(List.of(transaction));

      transactionService.detachCounterparty(20L);

      assertThat(transaction.getCounterpartyId()).isNull();
      verify(transactionStore).save(transaction);
    }

    @Test
    void countsActiveReferences() {
      when(transactionStore.countActiveByActivityId(4L)).thenReturn(3L);
      when(transactionStore.countActiveByCounterpartyId(20L)).thenReturn(2L);

      assertThat(transactionService.countActivityReferences(4L)).isEqualTo(3L);
      assertThat(transactionService.countCounterpartyReferences(20L)).isEqualTo(2L);
    }
  }

  private LedgerTransactionInput with(
      UnaryOperator<LedgerTransactionInput.LedgerTransactionInputBuilder> change) {
    return change
        .apply(
            LedgerTransactionInput.builder()
                .amount(input.getAmount())
                .direction(input.getDirection())
                .date(input.getDate())
                .description(input.getDescription())
                .activityId(input.getActivityId())
                .neutralCategoryId(input.getNeutralCategoryId())
                .counterpartyId(input.getCounterpartyId())
                .origin(input.getOrigin())
                .externalId(input.getExternalId())
                .sourceLabel(input.getSourceLabel())
                .attachmentExternalId(input.getAttachmentExternalId())
                .attachmentFileName(input.getAttachmentFileName()))
        .build();
  }

  private void stubResolution() {
    when(referenceResolver.resolve(4L, 7L, 20L, DATE))
        .thenReturn(new ResolvedTransactionReferences(activity, category, 17L, 20L));
  }

  private void stubSave() {
    when(transactionStore.save(any()))
        .thenAnswer(
            invocation -> {
              FinancialTransaction transaction = invocation.getArgument(0);
              transaction.setId(31L);
              transaction.setVersion(0L);
              return transaction;
            });
  }

  private FinancialTransaction transaction(Long id) {
    return FinancialTransaction.builder()
        .id(id)
        .amount(new BigDecimal("100.00"))
        .direction(TransactionDirection.INCOME)
        .date(DATE)
        .description("Rent")
        .activity(activity)
        .neutralCategory(category)
        .counterpartyId(20L)
        .createdAt(LocalDateTime.of(2026, 9, 22, 19, 0))
        .updatedAt(LocalDateTime.of(2026, 9, 22, 19, 0))
        .version(0L)
        .build();
  }

  private static String parentOf(FinancialTransaction transaction) {
    return transaction.getImportReferences().stream()
        .findFirst()
        .map(TransactionImportReference::getParentExternalId)
        .orElse(null);
  }
}

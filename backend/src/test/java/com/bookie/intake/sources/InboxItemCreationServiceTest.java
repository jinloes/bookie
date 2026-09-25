package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.TransactionDirection;
import com.bookie.catalog.financialcategory.application.FinancialCategoryService;
import com.bookie.catalog.financialcategory.domain.FinancialCategory;
import com.bookie.intake.application.BackgroundJobService;
import com.bookie.intake.application.InboxItemQueued;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.BackgroundJobType;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.intake.extraction.EmailSuggestion;
import com.bookie.intake.extraction.EmailType;
import com.bookie.intake.sources.InboxItemCreationService.FindOrCreateResult;
import com.bookie.intake.sources.InboxItemCreationService.IntakeSource;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.ExpenseSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InboxItemCreationServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-04-01T12:00:00Z"), ZoneOffset.UTC);
  private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

  @Mock private InboxItemStore inboxItemStore;
  @Mock private BackgroundJobService backgroundJobService;
  @Mock private ActivityCatalog activityCatalog;
  @Mock private FinancialCategoryService financialCategoryService;
  @Mock private LedgerImportReferenceQuery ledgerImportReferenceQuery;
  @Mock private ReceiptHashRepository receiptHashRepository;
  @Mock private PlatformTransactionManager transactionManager;
  @Mock private ApplicationEventPublisher events;

  private final FinancialActivity needsClassification =
      FinancialActivity.builder()
          .id(1L)
          .systemKey(ActivityCatalog.NEEDS_CLASSIFICATION_KEY)
          .build();
  private final FinancialActivity rental = FinancialActivity.builder().id(42L).build();
  private final FinancialCategory defaultCategory = FinancialCategory.builder().id(7L).build();

  private InboxItemCreationService service;

  @BeforeEach
  void setUp() {
    service =
        new InboxItemCreationService(
            CLOCK,
            inboxItemStore,
            backgroundJobService,
            activityCatalog,
            financialCategoryService,
            ledgerImportReferenceQuery,
            receiptHashRepository,
            transactionManager,
            events);
    lenient()
        .when(transactionManager.getTransaction(any()))
        .thenReturn(mock(TransactionStatus.class));
    lenient().when(activityCatalog.getNeedsClassification()).thenReturn(needsClassification);
    lenient().when(activityCatalog.findActiveById(42L)).thenReturn(rental);
    lenient()
        .when(financialCategoryService.defaultFor(any(), eq(TransactionDirection.EXPENSE)))
        .thenReturn(defaultCategory);
    lenient()
        .when(inboxItemStore.save(any()))
        .thenAnswer(
            invocation -> {
              InboxItem item = invocation.getArgument(0);
              if (item.getId() == null) {
                item.setId(100L);
              }
              return item;
            });
  }

  @Nested
  class FindOrCreate {

    @Test
    void createsAQueuedReceiptItemWithItsArtifactAndParseJob() {
      when(receiptHashRepository.findByDriveItemId("drive-1"))
          .thenReturn(Optional.of(ReceiptHash.builder().sha256("abc").build()));

      FindOrCreateResult result =
          service.findOrCreate(IntakeSource.of("drive-1", ExpenseSource.RECEIPT, "receipt.pdf"));

      InboxItem item = result.item();
      assertThat(result.alreadyProcessing()).isFalse();
      assertThat(item.getId()).isEqualTo(100L);
      assertThat(item.getState()).isEqualTo(InboxState.QUEUED);
      assertThat(item.getOrigin()).isEqualTo(ExpenseSource.RECEIPT);
      assertThat(item.getLegacySourceId()).isEqualTo("drive-1");
      assertThat(item.getRawStatus()).isEqualTo("PROCESSING");
      assertThat(item.getCreatedAt()).isEqualTo(NOW);
      assertThat(item.getLegacyActivityId()).isEqualTo(1L);
      assertThat(item.getLegacyCategoryId()).isEqualTo(7L);
      assertThat(item.isClassificationAmbiguous()).isTrue();
      assertThat(item.getReceiptExternalId()).isEqualTo("drive-1");
      assertThat(item.getReceiptFileName()).isEqualTo("receipt.pdf");
      assertThat(item.getArtifacts())
          .singleElement()
          .satisfies(
              artifact -> {
                assertThat(artifact.getType()).isEqualTo(InboxArtifactType.RECEIPT);
                assertThat(artifact.getExternalId()).isEqualTo("drive-1");
                assertThat(artifact.getSha256()).isEqualTo("abc");
              });
      verify(backgroundJobService).schedule(item, BackgroundJobType.PARSE_RECEIPT, null);
      verify(backgroundJobService, never()).scheduleIfAbsent(any(), any());
      verify(events).publishEvent(new InboxItemQueued(100L));
    }

    @Test
    void createsAnOutlookAttachmentItemAndSchedulesIdTranslation() {
      FindOrCreateResult result =
          service.findOrCreate(
              new IntakeSource(
                  "outlook-attachment:sha",
                  ExpenseSource.OUTLOOK_EMAIL,
                  "Receipts - one.pdf",
                  42L,
                  "message-1",
                  "attachment-1",
                  "one.pdf"));

      InboxItem item = result.item();
      assertThat(item.getConfiguredActivityId()).isEqualTo(42L);
      assertThat(item.getLegacyActivityId()).isEqualTo(42L);
      assertThat(item.getReceiptExternalId()).isNull();
      assertThat(item.getArtifacts())
          .singleElement()
          .satisfies(
              artifact -> {
                assertThat(artifact.getType()).isEqualTo(InboxArtifactType.OUTLOOK_EMAIL);
                assertThat(artifact.getExternalId()).isEqualTo("message-1");
                assertThat(artifact.getTextValue()).isEqualTo("attachment-1");
                assertThat(artifact.getFileName()).isEqualTo("one.pdf");
              });
      verify(backgroundJobService).schedule(item, BackgroundJobType.PARSE_OUTLOOK, null);
      verify(backgroundJobService).scheduleIfAbsent(item, BackgroundJobType.TRANSLATE_OUTLOOK_ID);
      verify(events).publishEvent(new InboxItemQueued(item.getId()));
    }

    @Test
    void doesNotTranslateAnOutlookItemWhoseImmutableIdIsKnown() {
      InboxItem failed = item(5L, ExpenseSource.OUTLOOK_EMAIL, InboxState.FAILED);
      failed.setImmutableSourceId("immutable-1");
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.OUTLOOK_EMAIL, "message-1"))
          .thenReturn(Optional.of(failed));

      service.findOrCreate(IntakeSource.of("message-1", ExpenseSource.OUTLOOK_EMAIL, "Rent"));

      verify(backgroundJobService, never()).scheduleIfAbsent(any(), any());
    }

    @Test
    void rejectsASourceAlreadySavedToTheLedger() {
      when(ledgerImportReferenceQuery.existsActive("RECEIPT", "drive-1")).thenReturn(true);

      assertConflict(
          () ->
              service.findOrCreate(
                  IntakeSource.of("drive-1", ExpenseSource.RECEIPT, "receipt.pdf")));
      verify(inboxItemStore, never()).save(any());
      verifyNoInteractions(events);
    }

    @Test
    void rejectsASavedInboxItem() {
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "drive-1"))
          .thenReturn(Optional.of(item(5L, ExpenseSource.RECEIPT, InboxState.SAVED)));

      assertConflict(
          () ->
              service.findOrCreate(
                  IntakeSource.of("drive-1", ExpenseSource.RECEIPT, "receipt.pdf")));
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"RECEIVED", "QUEUED", "PROCESSING", "SAVE_PENDING"})
    void returnsAnInFlightItemUnchanged(InboxState state) {
      InboxItem existing = item(5L, ExpenseSource.RECEIPT, state);
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "drive-1"))
          .thenReturn(Optional.of(existing));

      FindOrCreateResult result =
          service.findOrCreate(IntakeSource.of("drive-1", ExpenseSource.RECEIPT, "receipt.pdf"));

      assertThat(result.item()).isSameAs(existing);
      assertThat(result.alreadyProcessing()).isTrue();
      verify(inboxItemStore, never()).save(any());
      verify(backgroundJobService, never()).schedule(any(), any(), any());
      verifyNoInteractions(events);
    }

    @ParameterizedTest
    @EnumSource(
        value = InboxState.class,
        names = {"READY", "FAILED"})
    void requeuesAStaleItemInPlaceAndClearsItsProposal(InboxState state) {
      InboxItem existing = item(5L, ExpenseSource.RECEIPT, state);
      existing.setCreatedAt(NOW.minusDays(3));
      existing.setProposedAmount(BigDecimal.TEN);
      existing.setErrorMessage("boom");
      existing.addArtifact(
          InboxArtifact.builder()
              .type(InboxArtifactType.UNRECOGNIZED_ALIAS)
              .textValue("alias")
              .build());
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "drive-1"))
          .thenReturn(Optional.of(existing));

      FindOrCreateResult result =
          service.findOrCreate(IntakeSource.of("drive-1", ExpenseSource.RECEIPT, "receipt.pdf"));

      assertThat(result.alreadyProcessing()).isFalse();
      assertThat(result.item().getId()).isEqualTo(5L);
      assertThat(result.item().getState()).isEqualTo(InboxState.QUEUED);
      assertThat(result.item().getCreatedAt()).isEqualTo(NOW.minusDays(3));
      assertThat(result.item().getProposedAmount()).isNull();
      assertThat(result.item().getErrorMessage()).isNull();
      assertThat(result.item().getArtifacts())
          .extracting(InboxArtifact::getType)
          .containsExactly(InboxArtifactType.RECEIPT);
      verify(backgroundJobService).schedule(existing, BackgroundJobType.PARSE_RECEIPT, null);
      verify(events).publishEvent(new InboxItemQueued(5L));
    }

    @Test
    void aDismissedSourceThatArrivesAgainIsANewIntakeEvent() {
      InboxItem existing = item(5L, ExpenseSource.RECEIPT, InboxState.DISMISSED);
      existing.setCreatedAt(NOW.minusDays(3));
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "drive-1"))
          .thenReturn(Optional.of(existing));

      FindOrCreateResult result =
          service.findOrCreate(IntakeSource.of("drive-1", ExpenseSource.RECEIPT, "receipt.pdf"));

      assertThat(result.item().getState()).isEqualTo(InboxState.QUEUED);
      assertThat(result.item().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void returnsTheConcurrentlyCreatedItemAfterAUniqueIdentityConflict() {
      InboxItem concurrent = item(9L, ExpenseSource.RECEIPT, InboxState.QUEUED);
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "drive-1"))
          .thenReturn(Optional.empty(), Optional.of(concurrent));
      doThrow(new DataIntegrityViolationException("uk_inbox_items_origin_identity"))
          .when(inboxItemStore)
          .save(any());

      FindOrCreateResult result =
          service.findOrCreate(IntakeSource.of("drive-1", ExpenseSource.RECEIPT, "receipt.pdf"));

      assertThat(result.item()).isSameAs(concurrent);
      assertThat(result.alreadyProcessing()).isTrue();
      verifyNoInteractions(events);
    }

    @Test
    void failsLoudlyWhenAConflictLeavesNoItem() {
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.RECEIPT, "drive-1"))
          .thenReturn(Optional.empty());
      doThrow(new DataIntegrityViolationException("uk_inbox_items_origin_identity"))
          .when(inboxItemStore)
          .save(any());

      assertThatThrownBy(
              () ->
                  service.findOrCreate(
                      IntakeSource.of("drive-1", ExpenseSource.RECEIPT, "receipt.pdf")))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("drive-1")
          .hasCauseInstanceOf(DataIntegrityViolationException.class);
    }
  }

  @Nested
  class MarkReady {

    @Test
    void recordsTheParsedProposalAndAliases() {
      InboxItem item = item(5L, ExpenseSource.OUTLOOK_EMAIL, InboxState.PROCESSING);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(activityCatalog.resolveForSuggestedProperty("Main St")).thenReturn(rental);
      FinancialCategory repairs = FinancialCategory.builder().id(11L).build();
      when(financialCategoryService.resolve(
              null, "REPAIRS", TransactionDirection.EXPENSE, rental, LocalDate.of(2026, 3, 2)))
          .thenReturn(repairs);

      service.markReady(5L, suggestion(EmailType.EXPENSE, null, "REPAIRS"), List.of("acct-9"));

      verify(backgroundJobService).requireCurrentParse(5L);
      assertThat(item.getState()).isEqualTo(InboxState.READY);
      assertThat(item.getRawStatus()).isEqualTo("READY");
      assertThat(item.getProposedDirection()).isEqualTo(TransactionDirection.EXPENSE);
      assertThat(item.getProposedAmount()).isEqualByComparingTo("12.5");
      assertThat(item.getProposedDate()).isEqualTo(LocalDate.of(2026, 3, 2));
      assertThat(item.getProposedCounterpartyName()).isEqualTo("Plumber");
      assertThat(item.getLegacyActivityId()).isEqualTo(42L);
      assertThat(item.getLegacyCategoryId()).isEqualTo(11L);
      assertThat(item.isClassificationAmbiguous()).isFalse();
      assertThat(item.getArtifacts())
          .singleElement()
          .satisfies(
              artifact -> {
                assertThat(artifact.getType()).isEqualTo(InboxArtifactType.UNRECOGNIZED_ALIAS);
                assertThat(artifact.getTextValue()).isEqualTo("acct-9");
              });
      verify(inboxItemStore).save(item);
    }

    @Test
    void fallsBackToTheDefaultCategoryWhenTheSuggestionIsIncompatible() {
      InboxItem item = item(5L, ExpenseSource.RECEIPT, InboxState.PROCESSING);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(activityCatalog.findActiveById(42L)).thenReturn(rental);
      when(financialCategoryService.resolve(
              eq(3L), isNull(), eq(TransactionDirection.INCOME), eq(rental), any()))
          .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "incompatible"));
      when(financialCategoryService.defaultFor(
              rental, TransactionDirection.INCOME, LocalDate.of(2026, 3, 2)))
          .thenReturn(defaultCategory);

      EmailSuggestion suggestion =
          new EmailSuggestion(
              EmailType.INCOME,
              12.5,
              "Rent",
              "2026-03-02",
              "RENT",
              null,
              null,
              List.of(),
              List.of(),
              42L,
              3L,
              false);
      service.markReady(5L, suggestion, null);

      assertThat(item.getProposedDirection()).isEqualTo(TransactionDirection.INCOME);
      assertThat(item.getLegacyCategoryId()).isEqualTo(7L);
      assertThat(item.isClassificationAmbiguous()).isTrue();
      assertThat(item.getArtifacts()).isEmpty();
    }

    @Test
    void leavesTheDirectionOpenWhenTheParserCouldNotTellAndNoDateOrAmount() {
      InboxItem item = item(5L, ExpenseSource.RECEIPT, InboxState.PROCESSING);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      when(activityCatalog.resolveForSuggestedProperty(null)).thenReturn(needsClassification);
      when(financialCategoryService.resolve(
              null, null, TransactionDirection.EXPENSE, needsClassification, null))
          .thenReturn(defaultCategory);

      service.markReady(
          5L,
          new EmailSuggestion(
              null, null, null, null, null, null, null, null, null, null, null, true),
          List.of());

      assertThat(item.getProposedDirection()).isNull();
      assertThat(item.getProposedAmount()).isNull();
      assertThat(item.getProposedDate()).isNull();
      assertThat(item.isClassificationAmbiguous()).isTrue();
    }

    @Test
    void rejectsAnUnknownItem() {
      when(inboxItemStore.findById(5L)).thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> service.markReady(5L, suggestion(EmailType.EXPENSE, null, null), List.of()))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aSupersededParseDoesNotOverwriteTheItem() {
      InboxItem item = item(5L, ExpenseSource.RECEIPT, InboxState.PROCESSING);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));
      doThrow(new IllegalStateException("superseded"))
          .when(backgroundJobService)
          .requireCurrentParse(5L);

      assertThatThrownBy(
              () -> service.markReady(5L, suggestion(EmailType.EXPENSE, null, null), List.of()))
          .hasMessage("superseded");
      assertThat(item.getState()).isEqualTo(InboxState.PROCESSING);
      verify(inboxItemStore, never()).save(any());
    }
  }

  @Nested
  class MarkFailed {

    @Test
    void recordsTheParseError() {
      InboxItem item = item(5L, ExpenseSource.RECEIPT, InboxState.PROCESSING);
      when(inboxItemStore.findById(5L)).thenReturn(Optional.of(item));

      service.markFailed(5L, "Could not read receipt");

      verify(backgroundJobService).requireCurrentParse(5L);
      assertThat(item.getState()).isEqualTo(InboxState.FAILED);
      assertThat(item.getRawStatus()).isEqualTo("FAILED");
      assertThat(item.getErrorMessage()).isEqualTo("Could not read receipt");
      verify(inboxItemStore).save(item);
    }

    @Test
    void ignoresAMissingItem() {
      when(inboxItemStore.findById(5L)).thenReturn(Optional.empty());

      assertThatCode(() -> service.markFailed(5L, "error")).doesNotThrowAnyException();
      verify(inboxItemStore, never()).save(any());
    }
  }

  @Nested
  class RequeueForRetry {

    @Test
    void queuesAnotherParseAndKeepsTheProposal() {
      InboxItem item = item(5L, ExpenseSource.OUTLOOK_EMAIL, InboxState.FAILED);
      item.setRawStatus("FAILED");
      item.setErrorMessage("boom");
      item.setProposedAmount(BigDecimal.ONE);

      InboxItem requeued = service.requeueForRetry(item);

      assertThat(requeued.getState()).isEqualTo(InboxState.QUEUED);
      assertThat(requeued.getRawStatus()).isEqualTo("PROCESSING");
      assertThat(requeued.getErrorMessage()).isNull();
      assertThat(requeued.getProposedAmount()).isEqualByComparingTo("1");
      verify(backgroundJobService).schedule(item, BackgroundJobType.PARSE_OUTLOOK, null);
      // The caller (reparse) publishes the kick, so a retry is never announced twice.
      verifyNoInteractions(events);
    }

    @Test
    void rejectsAnItemThatIsAlreadyProcessing() {
      InboxItem item = item(5L, ExpenseSource.RECEIPT, InboxState.READY);
      item.setRawStatus("PROCESSING");

      assertConflict(() -> service.requeueForRetry(item));
      verify(backgroundJobService, never()).schedule(any(), any(), any());
    }
  }

  private static EmailSuggestion suggestion(EmailType type, Long categoryId, String category) {
    return new EmailSuggestion(
        type,
        12.5,
        "Faucet repair",
        "2026-03-02",
        category,
        "Main St",
        "Plumber",
        List.of(),
        List.of(),
        null,
        categoryId,
        false);
  }

  private static InboxItem item(Long id, ExpenseSource origin, InboxState state) {
    return InboxItem.builder().id(id).origin(origin).state(state).build();
  }

  private static void assertConflict(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }
}

package com.bookie.intake.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.integrations.documents.DocumentTextExtractor;
import com.bookie.integrations.outlook.MicrosoftGraphOutlookAdapter;
import com.bookie.integrations.outlook.OutlookAttachment;
import com.bookie.integrations.outlook.OutlookMailPort;
import com.bookie.integrations.outlook.OutlookMessage;
import com.bookie.integrations.outlook.OutlookMessageIdentity;
import com.bookie.integrations.outlook.OutlookMoveResult;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.ExpenseSource;
import com.microsoft.graph.models.ConvertIdResult;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.MailFolder;
import com.microsoft.graph.models.MailFolderCollectionResponse;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.mailfolders.item.messages.MessagesRequestBuilder;
import com.microsoft.graph.users.item.translateexchangeids.TranslateExchangeIdsPostRequestBody;
import com.microsoft.graph.users.item.translateexchangeids.TranslateExchangeIdsPostResponse;
import com.microsoft.kiota.RequestAdapter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutlookServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private GraphServiceClient graphClient;

  @Mock private DocumentTextExtractor pdfExtractorService;
  @Mock private OutlookMailPort outlookMail;
  @Mock private LedgerImportReferenceQuery ledgerImportReferenceQuery;
  @Mock private OutlookSettingsRepository outlookSettingsRepository;
  @Mock private ActivityCatalog financialActivityService;
  @Mock private InboxItemStore inboxItemStore;

  private OutlookService outlookService;
  private OutlookService attachmentService;

  private static final int YEAR = 2025;

  @BeforeEach
  void setUp() {
    lenient()
        .when(graphClient.me().translateExchangeIds().post(any(), any()))
        .thenAnswer(
            invocation -> {
              TranslateExchangeIdsPostRequestBody body = invocation.getArgument(0);
              TranslateExchangeIdsPostResponse response = new TranslateExchangeIdsPostResponse();
              response.setValue(
                  body.getInputIds().stream()
                      .map(
                          id -> {
                            ConvertIdResult result = new ConvertIdResult();
                            result.setSourceId(id);
                            result.setTargetId(id);
                            return result;
                          })
                      .toList());
              return response;
            });
    outlookService =
        new OutlookService(
            new MicrosoftGraphOutlookAdapter(graphClient),
            pdfExtractorService,
            ledgerImportReferenceQuery,
            outlookSettingsRepository,
            financialActivityService,
            inboxItemStore);
    attachmentService =
        new OutlookService(
            outlookMail,
            pdfExtractorService,
            ledgerImportReferenceQuery,
            outlookSettingsRepository,
            financialActivityService,
            inboxItemStore);
  }

  @Nested
  class GetRentalEmails {

    @BeforeEach
    void useDefaultFolders() {
      // No settings saved → falls back to hardcoded filter path
      when(outlookSettingsRepository.findById(1L)).thenReturn(Optional.empty());
    }

    @Test
    void nullFolderResponse_returnsEmptyPage() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(null);

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).isEmpty();
      assertThat(result.hasMore()).isFalse();
    }

    @Test
    void noMessages_returnsEmptyPage() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse());

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).isEmpty();
      assertThat(result.hasMore()).isFalse();
    }

    @Test
    void withMessages_returnsCorrectFields() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent Payment", "John Doe", now())));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
      assertThat(result.emails().get(0).id()).isEqualTo("msg1");
      assertThat(result.emails().get(0).subject()).isEqualTo("Rent Payment");
      assertThat(result.emails().get(0).sender()).isEqualTo("John Doe");
    }

    @Test
    void emailsWithSavedExpense_areFilteredOut() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(
              messageResponse(
                  message("msg1", "Rent saved", "A", date(2025, 6, 1)),
                  message("msg2", "Rent unsaved", "B", date(2025, 5, 1))));
      when(ledgerImportReferenceQuery.findSavedParentExternalIds(
              "OUTLOOK_EMAIL", List.of("msg1", "msg2")))
          .thenReturn(Set.of("msg1"));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
      assertThat(result.emails().get(0).id()).isEqualTo("msg2");
    }

    @Test
    void savedEmailWithRemainingPendingItem_staysIncluded() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent", "A", now())));
      when(ledgerImportReferenceQuery.findSavedParentExternalIds("OUTLOOK_EMAIL", List.of("msg1")))
          .thenReturn(Set.of("msg1"));
      when(inboxItemStore.findActiveForOutlookMessages(List.of("msg1")))
          .thenReturn(List.of(outlookItem(8L, InboxState.READY, "msg1:attachment-2", "msg1")));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).extracting(OutlookEmail::id).containsExactly("msg1");
    }

    @Test
    void enrichesWithInboxItemAndTransientState_whenActiveInboxItemExists() {
      stubPendingInbox(InboxState.QUEUED);

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails().get(0).inboxItemId()).isEqualTo(70L);
      assertThat(result.emails().get(0).inboxState()).isEqualTo("PROCESSING");
      assertThat(result.emails().get(0).pendingCount()).isEqualTo(1);
    }

    @Test
    void reportsFailedInboxState_whenEveryInboxItemFailed() {
      stubPendingInbox(InboxState.FAILED);

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails().get(0).inboxState()).isEqualTo("FAILED");
    }

    @Test
    void reportsReadyInboxState_whenNoInboxItemIsTransientOrAllFailed() {
      stubPendingInbox(InboxState.READY);

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails().get(0).inboxState()).isEqualTo("READY");
    }

    @Test
    void omitsInboxStateWhenTheEmailHasNoActiveInboxItem() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent", "A", now())));
      when(inboxItemStore.findActiveForOutlookMessages(List.of("msg1"))).thenReturn(List.of());

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails().get(0).inboxItemId()).isNull();
      assertThat(result.emails().get(0).inboxState()).isNull();
      assertThat(result.emails().get(0).pendingCount()).isZero();
    }

    private void stubPendingInbox(InboxState state) {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent", "A", now())));
      when(inboxItemStore.findActiveForOutlookMessages(List.of("msg1")))
          .thenReturn(List.of(outlookItem(70L, state, "msg1", "msg1")));
    }

    @Test
    void groupsLegacySourceItemsWithoutAnArtifactByTheirSourceId() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent", "A", now())));
      when(inboxItemStore.findActiveForOutlookMessages(List.of("msg1")))
          .thenReturn(
              List.of(
                  InboxItem.builder()
                      .id(71L)
                      .origin(ExpenseSource.OUTLOOK_EMAIL)
                      .legacySourceId("msg1")
                      .state(InboxState.READY)
                      .build()));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails().get(0).inboxItemId()).isEqualTo(71L);
      assertThat(result.emails().get(0).pendingCount()).isOne();
    }

    @Test
    void emailsWithoutSavedExpense_areIncluded() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent", "A", now())));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
      assertThat(result.emails().get(0).id()).isEqualTo("msg1");
    }

    @Test
    void sortsNewestFirst() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(
              messageResponse(
                  message("old", "Old", "A", date(2025, 1, 1)),
                  message("new", "New", "B", date(2025, 6, 1))));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails().get(0).id()).isEqualTo("new");
      assertThat(result.emails().get(1).id()).isEqualTo("old");
    }

    @Test
    void mergesMessagesAcrossFolders() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1", "f2"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "From f1", "A", date(2025, 1, 1))));
      when(graphClient.me().mailFolders().byMailFolderId("f2").messages().get(any()))
          .thenReturn(messageResponse(message("msg2", "From f2", "B", date(2025, 2, 1))));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(2);
      assertThat(result.emails().get(0).id()).isEqualTo("msg2");
      assertThat(result.emails().get(1).id()).isEqualTo("msg1");
    }

    @Test
    void pageOutOfBounds_returnsEmptyPage() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent", "A", now())));

      OutlookEmailsPage result = outlookService.getRentalEmails(5, YEAR);

      assertThat(result.emails()).isEmpty();
      assertThat(result.hasMore()).isFalse();
      assertThat(result.page()).isEqualTo(5);
    }

    @Test
    void taxesFolder_includesMessagesFromChildFolders() {
      MailFolder taxes = folder("taxes-id", "Taxes");
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse(taxes));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").childFolders().get())
          .thenReturn(folderResponse(folder("child1", "2024"), folder("child2", "2023")));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").messages().get(any()))
          .thenReturn(messageResponse());
      when(graphClient.me().mailFolders().byMailFolderId("child1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Tax Doc", "IRS", now())));
      when(graphClient.me().mailFolders().byMailFolderId("child2").messages().get(any()))
          .thenReturn(messageResponse());

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
      assertThat(result.emails().get(0).subject()).isEqualTo("Tax Doc");
    }

    @Test
    void hasMoreTrue_whenResultsExceedPageSize() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      MessageCollectionResponse resp = new MessageCollectionResponse();
      resp.setValue(
          IntStream.rangeClosed(1, 11)
              .mapToObj(i -> message("msg" + i, "Rent " + i, "A", date(2025, 1, i)))
              .toList());
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(resp);

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(10);
      assertThat(result.hasMore()).isTrue();
    }
  }

  @Nested
  class GetRentalEmailsWithConfiguredFolders {

    @BeforeEach
    void stubIncome() {}

    @Test
    void configuredFolderIds_usedDirectlyWithoutFilter() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(
                          List.of(
                              new FolderSetting("f1", false, 42L), new FolderSetting("f2", false)))
                      .receiptsFolderBase(OutlookSettings.DEFAULT_RECEIPTS_FOLDER)
                      .build()));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "From f1", "A", date(2025, 1, 1))));
      when(graphClient.me().mailFolders().byMailFolderId("f2").messages().get(any()))
          .thenReturn(messageResponse(message("msg2", "From f2", "B", date(2025, 2, 1))));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(2);
      assertThat(result.emails().get(0).id()).isEqualTo("msg2");
      assertThat(result.emails().get(0).activityId()).isNull();
      assertThat(result.emails().get(1).id()).isEqualTo("msg1");
      assertThat(result.emails().get(1).activityId()).isEqualTo(42L);
    }

    @Test
    void configuredActivityUsesDateFilterWithoutRequiringRentalCategory() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of(new FolderSetting("employment", false, 42L)))
                      .build()));
      AtomicReference<String> appliedFilter = new AtomicReference<>();
      when(graphClient.me().mailFolders().byMailFolderId("employment").messages().get(any()))
          .thenAnswer(
              invocation -> {
                @SuppressWarnings("unchecked")
                Consumer<MessagesRequestBuilder.GetRequestConfiguration> configure =
                    invocation.getArgument(0);
                MessagesRequestBuilder concreteBuilder =
                    new MessagesRequestBuilder(
                        "https://example.test/messages", mock(RequestAdapter.class));
                MessagesRequestBuilder.GetRequestConfiguration configuration =
                    concreteBuilder.new GetRequestConfiguration();
                configure.accept(configuration);
                appliedFilter.set(configuration.queryParameters.filter);
                return messageResponse(
                    message("msg-pay", "Pay advice", "Synthetic District", now()));
              });

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).singleElement().extracting("activityId").isEqualTo(42L);
      assertThat(appliedFilter.get())
          .contains("receivedDateTime ge 2025-01-01")
          .doesNotContain("categories/any");
    }

    @Test
    void expandSubfolders_includesChildFolderMessages() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of(new FolderSetting("taxes-id", true, 43L)))
                      .receiptsFolderBase(OutlookSettings.DEFAULT_RECEIPTS_FOLDER)
                      .build()));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").childFolders().get())
          .thenReturn(folderResponse(folder("child1", "2024")));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").messages().get(any()))
          .thenReturn(messageResponse());
      when(graphClient.me().mailFolders().byMailFolderId("child1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Tax Doc", "IRS", now())));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
      assertThat(result.emails().get(0).subject()).isEqualTo("Tax Doc");
      assertThat(result.emails().get(0).activityId()).isEqualTo(43L);
    }

    @Test
    void expandSubfoldersFalse_doesNotIncludeChildren() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of(new FolderSetting("taxes-id", false)))
                      .receiptsFolderBase(OutlookSettings.DEFAULT_RECEIPTS_FOLDER)
                      .build()));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Tax Doc", "IRS", now())));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
    }

    @Test
    void emptyConfiguredFolders_returnsEmptyPage() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .receiptsFolderBase(OutlookSettings.DEFAULT_RECEIPTS_FOLDER)
                      .build()));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).isEmpty();
      assertThat(result.hasMore()).isFalse();
    }
  }

  @Nested
  class UpdateConfiguredFolders {

    @Test
    void validatesConfiguredActivityBeforeSaving() {
      when(outlookSettingsRepository.findById(1L)).thenReturn(Optional.empty());
      when(financialActivityService.findActiveById(42L))
          .thenReturn(FinancialActivity.builder().id(42L).name("Teaching").active(true).build());
      FolderSetting setting = new FolderSetting("employment", true, 42L);

      outlookService.updateConfiguredFolderSettings(List.of(setting));

      verify(financialActivityService).findActiveById(42L);
      verify(outlookSettingsRepository)
          .save(
              org.mockito.ArgumentMatchers.argThat(
                  saved -> saved.getFolderSettings().equals(List.of(setting))));
    }
  }

  @Nested
  class GetAvailableFolders {

    @Test
    void nullResponse_returnsEmpty() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(null);

      List<OutlookService.FolderInfo> result = outlookService.getAvailableFolders();

      assertThat(result).isEmpty();
    }

    @Test
    void topLevelFolders_returnedWithDisplayName() {
      when(graphClient.me().mailFolders().get(any()))
          .thenReturn(folderResponse(folder("f1", "inbox")));
      when(graphClient.me().mailFolders().byMailFolderId("f1").childFolders().get(any()))
          .thenReturn(folderResponse(new MailFolder[0]));

      List<OutlookService.FolderInfo> result = outlookService.getAvailableFolders();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).id()).isEqualTo("f1");
      assertThat(result.get(0).displayPath()).isEqualTo("inbox");
    }

    @Test
    void childFolders_includedWithParentPrefix() {
      when(graphClient.me().mailFolders().get(any()))
          .thenReturn(folderResponse(folder("taxes-id", "Taxes")));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").childFolders().get(any()))
          .thenReturn(folderResponse(folder("child1", "2024"), folder("child2", "2023")));

      List<OutlookService.FolderInfo> result = outlookService.getAvailableFolders();

      assertThat(result).hasSize(3);
      assertThat(result.get(0).displayPath()).isEqualTo("Taxes");
      assertThat(result.get(1).displayPath()).isEqualTo("Taxes > 2024");
      assertThat(result.get(2).displayPath()).isEqualTo("Taxes > 2023");
    }
  }

  @Nested
  class FetchMessageBody {

    @BeforeEach
    void stubNoAttachments() {
      when(graphClient.me().messages().byMessageId(anyString()).attachments().get())
          .thenReturn(null);
    }

    @Test
    void noAttachments_returnsBodyOnly() {
      Message msg = new Message();
      msg.setId("msg1");
      msg.setSubject("HOA Bill");
      ItemBody body = new ItemBody();
      body.setContent("Please pay your HOA dues.");
      msg.setBody(body);
      when(graphClient.me().messages().byMessageId("msg1").get(any())).thenReturn(msg);

      OutlookService.MessageContent result = outlookService.fetchMessageBody("msg1");

      assertThat(result.body()).isEqualTo("Please pay your HOA dues.");
    }

    @Test
    void nullMessage_returnsEmptyContent() {
      when(graphClient.me().messages().byMessageId(anyString()).get(any())).thenReturn(null);

      OutlookService.MessageContent result = outlookService.fetchMessageBody("msg1");

      assertThat(result.subject()).isEmpty();
      assertThat(result.body()).isEmpty();
    }

    @Test
    void nullBody_returnsSubjectWithEmptyBody() {
      Message msg = new Message();
      msg.setId("msg1");
      msg.setSubject("Rent");
      msg.setBody(null);
      when(graphClient.me().messages().byMessageId("msg1").get(any())).thenReturn(msg);

      OutlookService.MessageContent result = outlookService.fetchMessageBody("msg1");

      assertThat(result.subject()).isEqualTo("Rent");
      assertThat(result.body()).isEmpty();
    }

    @Test
    void htmlBody_stripsTagsAndCollapsesWhitespace() {
      Message msg = new Message();
      msg.setId("msg1");
      msg.setSubject("Rent");
      ItemBody body = new ItemBody();
      body.setContent("<p>Rent  payment</p><p>for  January</p>");
      msg.setBody(body);
      when(graphClient.me().messages().byMessageId("msg1").get(any())).thenReturn(msg);

      OutlookService.MessageContent result = outlookService.fetchMessageBody("msg1");

      assertThat(result.subject()).isEqualTo("Rent");
      assertThat(result.body()).isEqualTo("Rent payment for January");
    }

    @Test
    void nullBodyContent_returnsEmptyBody() {
      Message msg = new Message();
      msg.setId("msg1");
      msg.setSubject("Rent");
      ItemBody body = new ItemBody();
      body.setContent(null);
      msg.setBody(body);
      when(graphClient.me().messages().byMessageId("msg1").get(any())).thenReturn(msg);

      OutlookService.MessageContent result = outlookService.fetchMessageBody("msg1");

      assertThat(result.subject()).isEqualTo("Rent");
      assertThat(result.body()).isEmpty();
    }
  }

  @Nested
  class AttachmentIntake {

    @Test
    void supportedNonInlineAttachmentsDefineCardinality() {
      when(outlookMail.getMessage("message", true))
          .thenReturn(
              Optional.of(
                  outlookMessage(
                      List.of(
                          attachment("attachment-1", "one.pdf", "application/pdf", false),
                          attachment("attachment-2", "two.png", "image/png", false),
                          attachment("inline", "logo.png", "image/png", true),
                          attachment("unsupported", "notes.txt", "text/plain", false)))));

      List<OutlookService.IntakeTarget> result = attachmentService.discoverIntakeTargets("message");

      assertThat(result)
          .extracting(OutlookService.IntakeTarget::attachmentId)
          .containsExactly("attachment-1", "attachment-2");
      assertThat(result)
          .extracting(OutlookService.IntakeTarget::sourceId)
          .doesNotHaveDuplicates()
          .allMatch(sourceId -> sourceId.startsWith("outlook-attachment:"));
      assertThat(result)
          .extracting(OutlookService.IntakeTarget::subject)
          .containsExactly("PayPal receipts - one.pdf", "PayPal receipts - two.png");
    }

    @Test
    void noSupportedAttachmentRetainsOneBodyTarget() {
      when(outlookMail.getMessage("message", true))
          .thenReturn(
              Optional.of(
                  outlookMessage(
                      List.of(
                          attachment("inline", "logo.png", "image/png", true),
                          attachment("svg", "logo.svg", "image/svg+xml", false),
                          attachment("unsupported", "notes.txt", "text/plain", false)))));

      assertThat(attachmentService.discoverIntakeTargets("message"))
          .containsExactly(
              new OutlookService.IntakeTarget("message", "message", null, null, "PayPal receipts"));
    }

    @Test
    void missingOrDuplicateAttachmentIdentityRejectsDiscoveryBeforeMutation() {
      when(outlookMail.getMessage("missing", true))
          .thenReturn(
              Optional.of(
                  outlookMessage(List.of(attachment(null, "one.pdf", "application/pdf", false)))));
      when(outlookMail.getMessage("duplicate", true))
          .thenReturn(
              Optional.of(
                  outlookMessage(
                      List.of(
                          attachment("same", "one.pdf", "application/pdf", false),
                          attachment("same", "two.pdf", "application/pdf", false)))));

      assertThatThrownBy(() -> attachmentService.discoverIntakeTargets("missing"))
          .isInstanceOf(com.bookie.integrations.IntegrationException.class);
      assertThatThrownBy(() -> attachmentService.discoverIntakeTargets("duplicate"))
          .isInstanceOf(com.bookie.integrations.IntegrationException.class);
    }

    @Test
    void selectedAttachmentContentIncludesSharedBodyAndExcludesSibling() {
      when(outlookMail.getMessage("message", true))
          .thenReturn(
              Optional.of(
                  outlookMessage(
                      List.of(
                          attachment("attachment-1", "one.pdf", "application/pdf", false),
                          attachment("attachment-2", "two.pdf", "application/pdf", false)))));
      when(pdfExtractorService.extractText(any(byte[].class), eq("one.pdf")))
          .thenReturn("FIRST AMOUNT 10.00");

      OutlookService.MessageContent result =
          attachmentService.fetchMessageContent("message", "attachment-1");

      assertThat(result.subject()).isEqualTo("PayPal receipts - one.pdf");
      assertThat(result.body()).contains("Shared email context", "FIRST AMOUNT 10.00");
      verify(pdfExtractorService).extractText(any(byte[].class), eq("one.pdf"));
      verify(pdfExtractorService, never()).extractText(any(byte[].class), eq("two.pdf"));
    }

    @Test
    void derivedSourcePreviewResolvesParentAndSelectedAttachment() {
      InboxItem item =
          InboxItem.builder()
              .id(5L)
              .origin(ExpenseSource.OUTLOOK_EMAIL)
              .legacySourceId("derived-source")
              .state(InboxState.READY)
              .build();
      item.addArtifact(
          InboxArtifact.builder()
              .type(InboxArtifactType.OUTLOOK_EMAIL)
              .externalId("message")
              .textValue("attachment-2")
              .build());
      when(inboxItemStore.findBySourceIdentity(ExpenseSource.OUTLOOK_EMAIL, "derived-source"))
          .thenReturn(Optional.of(item));
      when(outlookMail.getMessage("message", true))
          .thenReturn(
              Optional.of(
                  outlookMessage(
                      List.of(
                          attachment("attachment-1", "one.pdf", "application/pdf", false),
                          attachment("attachment-2", "two.pdf", "application/pdf", false)))));
      when(pdfExtractorService.extractText(any(byte[].class), eq("two.pdf")))
          .thenReturn("SECOND AMOUNT 20.00");

      OutlookService.MessageContent result = attachmentService.fetchMessageBody("derived-source");

      assertThat(result.body())
          .contains("Shared email context", "SECOND AMOUNT 20.00")
          .doesNotContain("FIRST AMOUNT 10.00");
    }
  }

  @Nested
  class ValidateEmailAutoMove {

    @Test
    void nonEmailSource_doesNotThrow() {
      outlookService.validateEmailAutoMove(ExpenseSource.RECEIPT);
      outlookService.validateEmailAutoMove(ExpenseSource.MANUAL);
    }

    @Test
    void emailSource_autoMoveDisabled_doesNotThrow() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .autoMoveEnabled(false)
                      .build()));

      outlookService.validateEmailAutoMove(ExpenseSource.OUTLOOK_EMAIL);
    }

    @Test
    void emailSource_autoMoveEnabledWithFolder_doesNotThrow() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .autoMoveEnabled(true)
                      .moveDestinationFolderId("folder-1")
                      .build()));

      outlookService.validateEmailAutoMove(ExpenseSource.OUTLOOK_EMAIL);
    }

    @Test
    void emailSource_autoMoveEnabledWithoutFolder_throws400() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .autoMoveEnabled(true)
                      .moveDestinationFolderId(null)
                      .build()));

      assertThatThrownBy(() -> outlookService.validateEmailAutoMove(ExpenseSource.OUTLOOK_EMAIL))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
          .satisfies(
              ex ->
                  assertThat(
                          ((org.springframework.web.server.ResponseStatusException) ex)
                              .getStatusCode())
                      .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST));
    }

    @Test
    void emailSource_noSettingsRecord_doesNotThrow() {
      when(outlookSettingsRepository.findById(1L)).thenReturn(Optional.empty());

      outlookService.validateEmailAutoMove(ExpenseSource.OUTLOOK_EMAIL);
    }
  }

  @Nested
  class MoveEmailIfConfigured {

    @Test
    void autoMoveDisabled_returnsEmpty() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .autoMoveEnabled(false)
                      .moveDestinationFolderId("folder-1")
                      .build()));

      Optional<String> result = outlookService.moveEmailIfConfigured("msg-1");

      assertThat(result).isEmpty();
      verify(graphClient.me().messages().byMessageId(anyString()).move(), never()).post(any());
    }

    @Test
    void autoMoveEnabledWithFolder_preservesLegacyMessageId() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .autoMoveEnabled(true)
                      .moveDestinationFolderId("folder-1")
                      .build()));
      Message current = new Message();
      current.setId("immutable-1");
      current.setParentFolderId("source-folder");
      when(graphClient.me().messages().byMessageId("msg-1").get(any())).thenReturn(current);
      Message movedMsg = new Message();
      movedMsg.setId("immutable-1");
      when(graphClient.me().messages().byMessageId("immutable-1").move().post(any(), any()))
          .thenReturn(movedMsg);

      Optional<String> result = outlookService.moveEmailIfConfigured("msg-1");

      assertThat(result).contains("msg-1");
    }

    @Test
    void autoMoveEnabledWithoutFolder_returnsEmpty() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .autoMoveEnabled(true)
                      .moveDestinationFolderId(null)
                      .build()));

      Optional<String> result = outlookService.moveEmailIfConfigured("msg-1");

      assertThat(result).isEmpty();
      verify(graphClient.me().messages().byMessageId(anyString()).move(), never()).post(any());
    }

    @Test
    void noSettingsRecord_returnsEmpty() {
      when(outlookSettingsRepository.findById(1L)).thenReturn(Optional.empty());

      Optional<String> result = outlookService.moveEmailIfConfigured("msg-1");

      assertThat(result).isEmpty();
      verify(graphClient.me().messages().byMessageId(anyString()).move(), never()).post(any());
    }

    @Test
    void alreadyInDestinationFolder_skipsMove_returnsEmpty() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .autoMoveEnabled(true)
                      .moveDestinationFolderId("folder-1")
                      .build()));
      Message current = new Message();
      current.setId("immutable-1");
      current.setParentFolderId("folder-1");
      when(graphClient.me().messages().byMessageId("msg-1").get(any())).thenReturn(current);

      Optional<String> result = outlookService.moveEmailIfConfigured("msg-1");

      assertThat(result).isEmpty();
      verify(graphClient.me().messages().byMessageId("immutable-1").move(), never())
          .post(any(), any());
    }

    @Test
    void identityMoveReturnsResolvedIdentityWhenMessageIsAlreadyAtDestination() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  OutlookSettings.builder()
                      .id(1L)
                      .folderSettings(List.of())
                      .autoMoveEnabled(true)
                      .moveDestinationFolderId("folder-1")
                      .build()));
      Message current = new Message();
      current.setId("immutable-1");
      current.setParentFolderId("folder-1");
      when(graphClient.me().messages().byMessageId("immutable-1").get(any())).thenReturn(current);

      Optional<OutlookMoveResult> result =
          outlookService.moveEmailIdentityIfConfigured(
              new OutlookMessageIdentity("msg-1", "immutable-1"));

      assertThat(result)
          .contains(
              new OutlookMoveResult(
                  OutlookMoveResult.Status.ALREADY_AT_DESTINATION,
                  new OutlookMessageIdentity("msg-1", "immutable-1")));
      verify(graphClient.me().messages().byMessageId("immutable-1").move(), never())
          .post(any(), any());
    }
  }

  // --- helpers ---

  private static MailFolderCollectionResponse folderResponse(String... ids) {
    return folderResponse(
        Arrays.stream(ids).map(id -> folder(id, null)).toArray(MailFolder[]::new));
  }

  private static MailFolderCollectionResponse folderResponse(MailFolder... folders) {
    MailFolderCollectionResponse resp = new MailFolderCollectionResponse();
    resp.setValue(List.of(folders));
    return resp;
  }

  private static MailFolder folder(String id, String displayName) {
    MailFolder f = new MailFolder();
    f.setId(id);
    f.setDisplayName(displayName);
    return f;
  }

  private static Message message(
      String id, String subject, String senderName, OffsetDateTime receivedAt) {
    Message msg = new Message();
    msg.setId(id);
    msg.setSubject(subject);
    msg.setBodyPreview("preview");
    msg.setReceivedDateTime(receivedAt);
    Recipient from = new Recipient();
    EmailAddress ea = new EmailAddress();
    ea.setName(senderName);
    from.setEmailAddress(ea);
    msg.setFrom(from);
    return msg;
  }

  private static MessageCollectionResponse messageResponse(Message... messages) {
    MessageCollectionResponse resp = new MessageCollectionResponse();
    resp.setValue(List.of(messages));
    return resp;
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now(ZoneOffset.UTC);
  }

  private static OffsetDateTime date(int year, int month, int day) {
    return OffsetDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC);
  }

  private static OutlookMessage outlookMessage(List<OutlookAttachment> attachments) {
    return new OutlookMessage(
        new OutlookMessageIdentity("message", "immutable-message"),
        "PayPal receipts",
        "PayPal",
        date(2026, 8, 20),
        "preview",
        "<p>Shared email context</p>",
        "inbox",
        attachments);
  }

  private static OutlookAttachment attachment(
      String id, String name, String contentType, boolean inline) {
    return new OutlookAttachment(id, name, contentType, inline, name.getBytes());
  }

  private static InboxItem outlookItem(
      Long id, InboxState state, String sourceId, String outlookMessageId) {
    InboxItem item =
        InboxItem.builder()
            .id(id)
            .origin(ExpenseSource.OUTLOOK_EMAIL)
            .legacySourceId(sourceId)
            .state(state)
            .build();
    item.addArtifact(
        InboxArtifact.builder()
            .type(InboxArtifactType.OUTLOOK_EMAIL)
            .externalId(outlookMessageId)
            .build());
    return item;
  }
}

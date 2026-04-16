package com.bookie.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.bookie.model.Expense;
import com.bookie.model.FolderSetting;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.model.OutlookSettings;
import com.bookie.model.PendingExpense;
import com.bookie.model.PendingExpenseStatus;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.OutlookSettingsRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.MailFolder;
import com.microsoft.graph.models.MailFolderCollectionResponse;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutlookServiceTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private GraphServiceClient graphClient;

  @Mock private PdfExtractorService pdfExtractorService;
  @Mock private ExpenseRepository expenseRepository;
  @Mock private IncomeRepository incomeRepository;
  @Mock private PendingExpenseRepository pendingExpenseRepository;
  @Mock private OutlookSettingsRepository outlookSettingsRepository;

  @InjectMocks private OutlookService outlookService;

  private static final int YEAR = 2025;

  @Nested
  class GetRentalEmails {

    @BeforeEach
    void useDefaultFolders() {
      // No settings saved → falls back to hardcoded filter path
      when(outlookSettingsRepository.findById(1L)).thenReturn(Optional.empty());
      when(incomeRepository.findBySourceIdIn(any())).thenReturn(List.of());
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
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

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
      Expense expense = new Expense();
      expense.setId(42L);
      expense.setSourceId("msg1");
      when(expenseRepository.findBySourceIdIn(List.of("msg1", "msg2")))
          .thenReturn(List.of(expense));
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
      assertThat(result.emails().get(0).id()).isEqualTo("msg2");
    }

    @Test
    void enrichesWithPendingIdAndStatus_whenPendingExpenseExists() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent", "A", now())));
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      PendingExpense pending = new PendingExpense();
      pending.setId(7L);
      pending.setSourceId("msg1");
      pending.setStatus(PendingExpenseStatus.PROCESSING);
      when(pendingExpenseRepository.findBySourceIdIn(List.of("msg1"))).thenReturn(List.of(pending));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails().get(0).pendingId()).isEqualTo(7L);
      assertThat(result.emails().get(0).pendingStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void emailsWithoutSavedExpense_areIncluded() {
      when(graphClient.me().mailFolders().get(any())).thenReturn(folderResponse("f1"));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Rent", "A", now())));
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

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
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

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
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

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
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

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
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(10);
      assertThat(result.hasMore()).isTrue();
    }
  }

  @Nested
  class GetRentalEmailsWithConfiguredFolders {

    @BeforeEach
    void stubIncome() {
      when(incomeRepository.findBySourceIdIn(any())).thenReturn(List.of());
    }

    @Test
    void configuredFolderIds_usedDirectlyWithoutFilter() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  new OutlookSettings(
                      1L,
                      List.of(new FolderSetting("f1", false), new FolderSetting("f2", false)),
                      OutlookSettings.DEFAULT_RECEIPTS_FOLDER)));
      when(graphClient.me().mailFolders().byMailFolderId("f1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "From f1", "A", date(2025, 1, 1))));
      when(graphClient.me().mailFolders().byMailFolderId("f2").messages().get(any()))
          .thenReturn(messageResponse(message("msg2", "From f2", "B", date(2025, 2, 1))));
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(2);
      assertThat(result.emails().get(0).id()).isEqualTo("msg2");
      assertThat(result.emails().get(1).id()).isEqualTo("msg1");
    }

    @Test
    void expandSubfolders_includesChildFolderMessages() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  new OutlookSettings(
                      1L,
                      List.of(new FolderSetting("taxes-id", true)),
                      OutlookSettings.DEFAULT_RECEIPTS_FOLDER)));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").childFolders().get())
          .thenReturn(folderResponse(folder("child1", "2024")));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").messages().get(any()))
          .thenReturn(messageResponse());
      when(graphClient.me().mailFolders().byMailFolderId("child1").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Tax Doc", "IRS", now())));
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
      assertThat(result.emails().get(0).subject()).isEqualTo("Tax Doc");
    }

    @Test
    void expandSubfoldersFalse_doesNotIncludeChildren() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  new OutlookSettings(
                      1L,
                      List.of(new FolderSetting("taxes-id", false)),
                      OutlookSettings.DEFAULT_RECEIPTS_FOLDER)));
      when(graphClient.me().mailFolders().byMailFolderId("taxes-id").messages().get(any()))
          .thenReturn(messageResponse(message("msg1", "Tax Doc", "IRS", now())));
      when(expenseRepository.findBySourceIdIn(any())).thenReturn(List.of());
      when(pendingExpenseRepository.findBySourceIdIn(any())).thenReturn(List.of());

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).hasSize(1);
    }

    @Test
    void emptyConfiguredFolders_returnsEmptyPage() {
      when(outlookSettingsRepository.findById(1L))
          .thenReturn(
              Optional.of(
                  new OutlookSettings(1L, List.of(), OutlookSettings.DEFAULT_RECEIPTS_FOLDER)));

      OutlookEmailsPage result = outlookService.getRentalEmails(0, YEAR);

      assertThat(result.emails()).isEmpty();
      assertThat(result.hasMore()).isFalse();
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
}

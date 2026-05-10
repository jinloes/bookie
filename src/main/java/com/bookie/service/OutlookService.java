package com.bookie.service;

import com.bookie.model.Expense;
import com.bookie.model.ExpenseSource;
import com.bookie.model.FolderSetting;
import com.bookie.model.Income;
import com.bookie.model.OutlookEmail;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.model.OutlookSettings;
import com.bookie.model.PendingExpense;
import com.bookie.repository.ExpenseRepository;
import com.bookie.repository.IncomeRepository;
import com.bookie.repository.OutlookSettingsRepository;
import com.bookie.repository.PendingExpenseRepository;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.MailFolder;
import com.microsoft.graph.models.MailFolderCollectionResponse;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody;
import com.microsoft.kiota.ApiException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Service for interacting with Outlook email via Microsoft Graph API. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutlookService {

  private static final int PAGE_SIZE = 10;
  private static final int GRAPH_FETCH_SIZE = 100;
  private static final int MAX_MESSAGES_PER_FOLDER = 500;
  private static final String FOLDER_DISPLAY_FILTER =
      "displayName eq 'inbox' or displayName eq 'Rent Expenses' or displayName eq 'Taxes'";
  // receivedDateTime must appear before categories in the filter because the Graph API requires
  // any property used in $orderby to appear first in $filter, or it returns InefficientFilter.
  private static final String RENTAL_CATEGORY_FILTER_TEMPLATE =
      "receivedDateTime ge %d-01-01T00:00:00Z"
          + " and receivedDateTime lt %d-01-01T00:00:00Z"
          + " and categories/any(c:c eq 'Rental')";
  // Graph API returns email bodies as HTML; these strip tags and collapse whitespace
  // so the AI parser receives clean plain text rather than markup noise.
  private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  private final GraphServiceClient graphClient;
  private final PdfExtractorService pdfExtractorService;
  private final ExpenseRepository expenseRepository;
  private final IncomeRepository incomeRepository;
  private final PendingExpenseRepository pendingExpenseRepository;
  private final OutlookSettingsRepository outlookSettingsRepository;

  /**
   * Returns a paginated list of rental emails from Outlook for the given year.
   *
   * @param page zero-based page index
   * @param year calendar year to filter by; defaults to current year if not specified
   * @return the page of rental emails
   */
  public OutlookEmailsPage getRentalEmails(int page, int year) {
    List<String> folderIds = resolveConfiguredFolderIds();

    // Graph API $orderby applies per-folder only; a client-side sort is required
    // to produce a globally ordered result after merging across folders.
    List<OutlookEmail> allEmails =
        folderIds.stream()
            .flatMap(id -> fetchMessagesFromFolder(id, year).stream())
            .sorted(Comparator.comparing(OutlookEmail::receivedAt).reversed())
            .toList();

    // Filter out emails already saved as expenses or income before paginating so pages are sparse.
    List<String> allEmailIds = allEmails.stream().map(OutlookEmail::id).toList();
    Set<String> savedSourceIds = new java.util.HashSet<>();
    expenseRepository.findBySourceIdIn(allEmailIds).stream()
        .map(Expense::getSourceId)
        .filter(Objects::nonNull)
        .forEach(savedSourceIds::add);
    incomeRepository.findBySourceIdIn(allEmailIds).stream()
        .map(Income::getSourceId)
        .filter(Objects::nonNull)
        .forEach(savedSourceIds::add);
    List<OutlookEmail> unsaved =
        allEmails.stream().filter(e -> !savedSourceIds.contains(e.id())).toList();

    int from = page * PAGE_SIZE;
    if (from >= unsaved.size()) {
      return new OutlookEmailsPage(List.of(), page, false);
    }
    int to = Math.min(from + PAGE_SIZE, unsaved.size());
    List<OutlookEmail> pageItems = unsaved.subList(from, to);

    List<String> pageEmailIds = pageItems.stream().map(OutlookEmail::id).toList();
    Map<String, PendingExpense> pendingBySourceId =
        pendingExpenseRepository.findBySourceIdIn(pageEmailIds).stream()
            .collect(Collectors.toMap(PendingExpense::getSourceId, p -> p));

    List<OutlookEmail> enriched =
        pageItems.stream()
            .map(
                email -> {
                  PendingExpense pending = pendingBySourceId.get(email.id());
                  return OutlookEmail.builder()
                      .id(email.id())
                      .subject(email.subject())
                      .sender(email.sender())
                      .receivedAt(email.receivedAt())
                      .preview(email.preview())
                      .pendingId(pending != null ? pending.getId() : null)
                      .pendingStatus(pending != null ? pending.getStatus().name() : null)
                      .build();
                })
            .toList();

    return new OutlookEmailsPage(enriched, page, to < unsaved.size());
  }

  /** Available folder entry returned by {@link #getAvailableFolders()}. */
  public record FolderInfo(String id, String displayPath) {}

  /**
   * Returns all top-level mail folders and their immediate children as a flat list with display
   * paths (e.g. "Taxes", "Taxes > 2024"). Child folders are fetched in parallel to reduce latency.
   */
  public List<FolderInfo> getAvailableFolders() {
    List<MailFolder> topLevel =
        Optional.ofNullable(
                graphClient
                    .me()
                    .mailFolders()
                    .get(config -> Objects.requireNonNull(config.queryParameters).top = 100))
            .map(MailFolderCollectionResponse::getValue)
            .orElse(List.of());

    return topLevel.stream()
        .flatMap(
            folder -> {
              List<FolderInfo> items = new ArrayList<>();
              items.add(new FolderInfo(folder.getId(), folder.getDisplayName()));
              Optional.ofNullable(
                      graphClient
                          .me()
                          .mailFolders()
                          .byMailFolderId(folder.getId())
                          .childFolders()
                          .get(config -> Objects.requireNonNull(config.queryParameters).top = 100))
                  .map(MailFolderCollectionResponse::getValue)
                  .orElse(List.of())
                  .stream()
                  .map(
                      child ->
                          new FolderInfo(
                              child.getId(),
                              folder.getDisplayName() + " > " + child.getDisplayName()))
                  .forEach(items::add);
              return items.stream();
            })
        .toList();
  }

  /** Returns the configured folder settings, or an empty list if no settings have been saved. */
  public List<FolderSetting> getConfiguredFolderSettings() {
    return outlookSettingsRepository
        .findById(1L)
        .map(OutlookSettings::getFolderSettings)
        .orElse(List.of());
  }

  /** Saves the given folder settings as the configured search folders. */
  public void updateConfiguredFolderSettings(List<FolderSetting> folderSettings) {
    OutlookSettings settings =
        outlookSettingsRepository
            .findById(1L)
            .orElseGet(
                () ->
                    OutlookSettings.builder()
                        .id(1L)
                        .folderSettings(new ArrayList<>())
                        .receiptsFolderBase(OutlookSettings.DEFAULT_RECEIPTS_FOLDER)
                        .build());
    settings.setFolderSettings(folderSettings);
    outlookSettingsRepository.save(settings);
  }

  /** Move settings returned by {@link #getMoveSettings()}. */
  public record MoveSettings(boolean enabled, String folderId) {}

  /** Returns the current auto-move settings. Defaults to disabled when no settings exist. */
  public MoveSettings getMoveSettings() {
    return outlookSettingsRepository
        .findById(1L)
        .map(s -> new MoveSettings(s.isAutoMoveEnabled(), s.getMoveDestinationFolderId()))
        .orElse(new MoveSettings(false, null));
  }

  /** Saves the auto-move toggle and destination folder. */
  public void updateMoveSettings(boolean enabled, String folderId) {
    OutlookSettings settings =
        outlookSettingsRepository
            .findById(1L)
            .orElseGet(
                () ->
                    OutlookSettings.builder()
                        .id(1L)
                        .folderSettings(new ArrayList<>())
                        .receiptsFolderBase(OutlookSettings.DEFAULT_RECEIPTS_FOLDER)
                        .build());
    settings.setAutoMoveEnabled(enabled);
    settings.setMoveDestinationFolderId(folderId);
    outlookSettingsRepository.save(settings);
  }

  /**
   * Throws {@code 400 Bad Request} when the source is an Outlook email, auto-move is enabled, and
   * no destination folder has been configured. Call this before persisting the expense/income so
   * the save fails cleanly rather than after the record is committed.
   */
  public void validateEmailAutoMove(ExpenseSource sourceType) {
    if (sourceType != ExpenseSource.OUTLOOK_EMAIL) {
      return;
    }
    MoveSettings settings = getMoveSettings();
    if (settings.enabled() && StringUtils.isBlank(settings.folderId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Auto-move is enabled but no destination folder is configured");
    }
  }

  /**
   * Moves the Outlook message to the configured destination folder when auto-move is enabled. Does
   * nothing if the setting is off or the folder is not configured.
   */
  /**
   * Moves the Outlook message to the configured destination folder when auto-move is enabled.
   *
   * @return the new message ID assigned by Exchange after the move, or empty if not moved. Exchange
   *     always reassigns the message ID when a message is moved between folders, so callers that
   *     store sourceId must update it to this new ID to keep filtering correct.
   */
  public Optional<String> moveEmailIfConfigured(String messageId) {
    return outlookSettingsRepository
        .findById(1L)
        .filter(
            s -> s.isAutoMoveEnabled() && StringUtils.isNotBlank(s.getMoveDestinationFolderId()))
        .flatMap(s -> moveEmail(messageId, s.getMoveDestinationFolderId()));
  }

  // Returns the new message ID after moving, or empty if the message is already in the folder.
  private Optional<String> moveEmail(String messageId, String folderId) {
    Message current =
        graphClient
            .me()
            .messages()
            .byMessageId(messageId)
            .get(
                config ->
                    Objects.requireNonNull(config.queryParameters).select =
                        new String[] {"parentFolderId"});
    String currentFolderId =
        Optional.ofNullable(current).map(Message::getParentFolderId).orElse(null);
    if (folderId.equals(currentFolderId)) {
      log.debug("moveEmail: message {} already in folder {}, skipping", messageId, folderId);
      return Optional.empty();
    }
    MovePostRequestBody body = new MovePostRequestBody();
    body.setDestinationId(folderId);
    try {
      Message moved = graphClient.me().messages().byMessageId(messageId).move().post(body);
      return Optional.of(Optional.ofNullable(moved).map(Message::getId).orElse(messageId));
    } catch (ApiException e) {
      if (e.getResponseStatusCode() == 401 || e.getResponseStatusCode() == 403) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Outlook reconnection required");
      }
      throw e;
    }
  }

  /**
   * Returns the folder IDs to search. When settings exist, expands any folder with
   * expandSubfolders=true to include its child folder IDs. When no settings have been saved, falls
   * back to resolving the hardcoded default folder names via the Graph API (preserving the original
   * inbox / Rent Expenses / Taxes + child folders behavior).
   */
  private List<String> resolveConfiguredFolderIds() {
    return outlookSettingsRepository
        .findById(1L)
        .map(OutlookSettings::getFolderSettings)
        .map(this::expandFolderSettings)
        .orElseGet(this::resolveDefaultFolderIds);
  }

  private List<String> expandFolderSettings(List<FolderSetting> folderSettings) {
    List<String> ids = new ArrayList<>();
    for (FolderSetting fs : folderSettings) {
      ids.add(fs.getFolderId());
      if (fs.isExpandSubfolders()) {
        Optional.ofNullable(
                graphClient
                    .me()
                    .mailFolders()
                    .byMailFolderId(fs.getFolderId())
                    .childFolders()
                    .get())
            .map(MailFolderCollectionResponse::getValue)
            .orElse(List.of())
            .stream()
            .map(MailFolder::getId)
            .forEach(ids::add);
      }
    }
    return ids;
  }

  private List<String> resolveDefaultFolderIds() {
    var folderResp =
        graphClient
            .me()
            .mailFolders()
            .get(
                config ->
                    Objects.requireNonNull(config.queryParameters).filter = FOLDER_DISPLAY_FILTER);
    return Optional.ofNullable(folderResp)
        .map(MailFolderCollectionResponse::getValue)
        .orElse(List.of())
        .stream()
        .flatMap(folder -> expandDefaultFolderWithChildren(folder).stream())
        .toList();
  }

  private List<String> expandDefaultFolderWithChildren(MailFolder folder) {
    List<String> ids = new ArrayList<>();
    ids.add(folder.getId());
    if ("Taxes".equalsIgnoreCase(folder.getDisplayName())) {
      Optional.ofNullable(
              graphClient.me().mailFolders().byMailFolderId(folder.getId()).childFolders().get())
          .map(MailFolderCollectionResponse::getValue)
          .orElse(List.of())
          .stream()
          .map(MailFolder::getId)
          .forEach(ids::add);
    }
    return ids;
  }

  private List<OutlookEmail> fetchMessagesFromFolder(String folderId, int year) {
    String filter = RENTAL_CATEGORY_FILTER_TEMPLATE.formatted(year, year + 1);
    List<OutlookEmail> result = new ArrayList<>();

    MessageCollectionResponse page =
        graphClient
            .me()
            .mailFolders()
            .byMailFolderId(folderId)
            .messages()
            .get(
                config -> {
                  Objects.requireNonNull(config.queryParameters).filter = filter;
                  config.queryParameters.select =
                      new String[] {"subject", "from", "receivedDateTime", "bodyPreview"};
                  config.queryParameters.orderby = new String[] {"receivedDateTime desc"};
                  config.queryParameters.top = GRAPH_FETCH_SIZE;
                });

    while (page != null && result.size() < MAX_MESSAGES_PER_FOLDER) {
      Optional.ofNullable(page.getValue()).orElse(List.of()).stream()
          .map(this::toOutlookEmail)
          .forEach(result::add);
      if (page.getOdataNextLink() == null) {
        break;
      }
      final String nextLink = page.getOdataNextLink();
      page =
          graphClient
              .me()
              .mailFolders()
              .byMailFolderId(folderId)
              .messages()
              .withUrl(nextLink)
              .get();
    }
    return result;
  }

  private OutlookEmail toOutlookEmail(Message msg) {
    return OutlookEmail.builder()
        .id(msg.getId())
        .subject(msg.getSubject())
        .sender(
            Optional.ofNullable(msg.getFrom())
                .map(Recipient::getEmailAddress)
                .map(EmailAddress::getName)
                .orElse(""))
        .receivedAt(Optional.ofNullable(msg.getReceivedDateTime()).map(Object::toString).orElse(""))
        .preview(msg.getBodyPreview())
        .build();
  }

  /** Holds the subject, plain-text body, and received date (YYYY-MM-DD) of an email message. */
  public record MessageContent(String subject, String body, String receivedDate) {}

  /**
   * Fetches the subject and plain-text body of a message by ID.
   *
   * @param messageId the Outlook message ID
   * @return the message content
   */
  public MessageContent fetchMessageBody(String messageId) {
    Message message =
        graphClient
            .me()
            .messages()
            .byMessageId(messageId)
            .get(
                config -> {
                  Objects.requireNonNull(config.queryParameters).select =
                      new String[] {"subject", "body", "receivedDateTime"};
                  // Expand attachments inline so contentBytes is always populated;
                  // a separate attachments call omits contentBytes for some message types.
                  config.queryParameters.expand = new String[] {"attachments"};
                });

    String attachmentText = extractPdfAttachmentText(message);
    log.info(
        "fetchMessageBody: messageId={} attachmentTextLen={}", messageId, attachmentText.length());
    return Optional.ofNullable(message)
        .map(
            m -> {
              MessageContent base = toMessageContent(m);
              String body =
                  attachmentText.isBlank()
                      ? base.body()
                      : base.body() + "\n\n[Attachment]\n" + attachmentText;
              return new MessageContent(base.subject(), body, base.receivedDate());
            })
        .orElse(new MessageContent("", "", ""));
  }

  private String extractPdfAttachmentText(Message message) {
    if (message == null || message.getAttachments() == null) {
      return "";
    }
    log.info("Message has {} attachment(s)", message.getAttachments().size());
    message
        .getAttachments()
        .forEach(
            a ->
                log.info(
                    "  Attachment: class={} name={} contentType={} isInline={}",
                    a.getClass().getSimpleName(),
                    a.getName(),
                    a instanceof FileAttachment fa ? fa.getContentType() : "n/a",
                    a.getIsInline()));
    return message.getAttachments().stream()
        .filter(FileAttachment.class::isInstance)
        .map(FileAttachment.class::cast)
        .filter(a -> StringUtils.startsWithIgnoreCase(a.getContentType(), "application/pdf"))
        .map(
            a -> {
              byte[] bytes = a.getContentBytes();
              log.info(
                  "  PDF attachment '{}': bytes={}",
                  a.getName(),
                  bytes == null ? "null" : bytes.length);
              return pdfExtractorService.extractText(bytes);
            })
        .filter(t -> !t.isBlank())
        .collect(Collectors.joining("\n\n"));
  }

  private MessageContent toMessageContent(Message message) {
    String plainText =
        Optional.ofNullable(message.getBody())
            .map(body -> stripHtml(Optional.ofNullable(body.getContent()).orElse("")))
            .orElse("");
    String receivedDate =
        Optional.ofNullable(message.getReceivedDateTime())
            .map(dt -> dt.toLocalDate().toString())
            .orElse("");
    return new MessageContent(message.getSubject(), plainText, receivedDate);
  }

  private String stripHtml(String html) {
    return WHITESPACE_PATTERN
        .matcher(HTML_TAG_PATTERN.matcher(html).replaceAll(" "))
        .replaceAll(" ")
        .trim();
  }
}

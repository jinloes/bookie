package com.bookie.service;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.integrations.documents.DocumentTextExtractor;
import com.bookie.integrations.outlook.OutlookFolder;
import com.bookie.integrations.outlook.OutlookMailPort;
import com.bookie.integrations.outlook.OutlookMessage;
import com.bookie.integrations.outlook.OutlookMessageIdentity;
import com.bookie.integrations.outlook.OutlookMessagePage;
import com.bookie.integrations.outlook.OutlookMessageQuery;
import com.bookie.integrations.outlook.OutlookMoveResult;
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
import org.apache.commons.collections4.CollectionUtils;
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
  private static final String INTAKE_DATE_FILTER_TEMPLATE =
      "receivedDateTime ge %d-01-01T00:00:00Z" + " and receivedDateTime lt %d-01-01T00:00:00Z";
  // Graph API returns email bodies as HTML; these strip tags and collapse whitespace
  // so the AI parser receives clean plain text rather than markup noise.
  private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  private final OutlookMailPort outlookMail;
  private final DocumentTextExtractor pdfExtractorService;
  private final ExpenseRepository expenseRepository;
  private final IncomeRepository incomeRepository;
  private final PendingExpenseRepository pendingExpenseRepository;
  private final OutlookSettingsRepository outlookSettingsRepository;
  private final ActivityCatalog activityCatalog;

  /**
   * Returns a paginated list of rental emails from Outlook for the given year.
   *
   * @param page zero-based page index
   * @param year calendar year to filter by; defaults to current year if not specified
   * @return the page of rental emails
   */
  public OutlookEmailsPage getRentalEmails(int page, int year) {
    List<FolderContext> folderContexts = resolveConfiguredFolderContexts();

    // Graph API $orderby applies per-folder only; a client-side sort is required
    // to produce a globally ordered result after merging across folders.
    // Deduplicate by email ID (same email may appear in multiple folders)
    Map<String, OutlookEmail> uniqueEmailsMap =
        folderContexts.stream()
            .flatMap(context -> fetchMessagesFromFolder(context, year).stream())
            .collect(
                Collectors.toMap(
                    OutlookEmail::id,
                    email -> email,
                    (first, second) -> first)); // Keep first instance if duplicate
    List<OutlookEmail> allEmails =
        uniqueEmailsMap.values().stream()
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
                      .activityId(email.activityId())
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
    List<OutlookFolder> topLevel = outlookMail.listFolders(null, 100);

    return topLevel.stream()
        .flatMap(
            folder -> {
              List<FolderInfo> items = new ArrayList<>();
              items.add(new FolderInfo(folder.id(), folder.displayName()));
              outlookMail.listChildFolders(folder.id(), 100).stream()
                  .map(
                      child ->
                          new FolderInfo(
                              child.id(), folder.displayName() + " > " + child.displayName()))
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
    List<FolderSetting> validatedSettings =
        CollectionUtils.emptyIfNull(folderSettings).stream()
            .filter(Objects::nonNull)
            .peek(
                setting -> {
                  if (setting.getActivityId() != null) {
                    activityCatalog.findActiveById(setting.getActivityId());
                  }
                })
            .toList();
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
    settings.setFolderSettings(new ArrayList<>(validatedSettings));
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
   * Moves the Outlook message to the configured destination folder when auto-move is enabled.
   *
   * @return the original persisted message ID after a move, or empty if no move was needed. The
   *     immutable identity remains available through {@link #moveEmailIdentityIfConfigured(String)}
   *     without overwriting the legacy source ID.
   */
  public Optional<String> moveEmailIfConfigured(String messageId) {
    return moveEmailIdentityIfConfigured(messageId)
        .filter(result -> result.status() == OutlookMoveResult.Status.MOVED)
        .map(ignored -> messageId);
  }

  /** Returns a completed move, including the immutable ID, without rewriting the legacy ID. */
  public Optional<OutlookMoveResult> moveEmailIdentityIfConfigured(String messageId) {
    return moveEmailIdentityIfConfigured(OutlookMessageIdentity.unresolved(messageId));
  }

  /** Moves by durable identity while retaining the original legacy ID for compatibility. */
  public Optional<OutlookMoveResult> moveEmailIdentityIfConfigured(
      OutlookMessageIdentity messageIdentity) {
    return outlookSettingsRepository
        .findById(1L)
        .filter(
            s -> s.isAutoMoveEnabled() && StringUtils.isNotBlank(s.getMoveDestinationFolderId()))
        .map(s -> moveEmail(messageIdentity, s.getMoveDestinationFolderId()));
  }

  private OutlookMoveResult moveEmail(OutlookMessageIdentity messageIdentity, String folderId) {
    try {
      OutlookMoveResult result = outlookMail.moveToFolder(messageIdentity, folderId);
      if (result.status() == OutlookMoveResult.Status.ALREADY_AT_DESTINATION) {
        log.debug(
            "moveEmail: message {} already in folder {}, skipping",
            messageIdentity.durableId(),
            folderId);
      }
      return result;
    } catch (IntegrationException e) {
      if (e.getKind() == IntegrationFailureKind.RECONNECT_REQUIRED) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Outlook reconnection required");
      }
      throw e;
    }
  }

  /**
   * Returns folder contexts to search. A configured activity makes that folder an explicit intake
   * scope and allows all dated messages; legacy/default folders without an activity retain the
   * Rental-category filter.
   */
  private List<FolderContext> resolveConfiguredFolderContexts() {
    return outlookSettingsRepository
        .findById(1L)
        .map(OutlookSettings::getFolderSettings)
        .map(this::expandFolderSettings)
        .orElseGet(this::resolveDefaultFolderContexts);
  }

  private List<FolderContext> expandFolderSettings(List<FolderSetting> folderSettings) {
    List<FolderContext> contexts = new ArrayList<>();
    for (FolderSetting setting : CollectionUtils.emptyIfNull(folderSettings)) {
      if (setting == null || StringUtils.isBlank(setting.getFolderId())) {
        continue;
      }
      boolean rentalOnly = setting.getActivityId() == null;
      contexts.add(new FolderContext(setting.getFolderId(), setting.getActivityId(), rentalOnly));
      if (setting.isExpandSubfolders()) {
        outlookMail.listChildFolders(setting.getFolderId(), null).stream()
            .map(OutlookFolder::id)
            .filter(StringUtils::isNotBlank)
            .map(id -> new FolderContext(id, setting.getActivityId(), rentalOnly))
            .forEach(contexts::add);
      }
    }
    return contexts;
  }

  private List<FolderContext> resolveDefaultFolderContexts() {
    return outlookMail.listFolders(FOLDER_DISPLAY_FILTER, null).stream()
        .flatMap(folder -> expandDefaultFolderWithChildren(folder).stream())
        .toList();
  }

  private List<FolderContext> expandDefaultFolderWithChildren(OutlookFolder folder) {
    List<FolderContext> contexts = new ArrayList<>();
    contexts.add(new FolderContext(folder.id(), null, true));
    if ("Taxes".equalsIgnoreCase(folder.displayName())) {
      outlookMail.listChildFolders(folder.id(), null).stream()
          .map(OutlookFolder::id)
          .filter(StringUtils::isNotBlank)
          .map(id -> new FolderContext(id, null, true))
          .forEach(contexts::add);
    }
    return contexts;
  }

  private List<OutlookEmail> fetchMessagesFromFolder(FolderContext context, int year) {
    String filter =
        (context.rentalOnly() ? RENTAL_CATEGORY_FILTER_TEMPLATE : INTAKE_DATE_FILTER_TEMPLATE)
            .formatted(year, year + 1);
    List<OutlookEmail> result = new ArrayList<>();

    OutlookMessagePage page =
        outlookMail.listMessages(
            context.folderId(),
            OutlookMessageQuery.builder()
                .filter(filter)
                .select(List.of("subject", "from", "receivedDateTime", "bodyPreview"))
                .orderBy(List.of("receivedDateTime desc"))
                .top(GRAPH_FETCH_SIZE)
                .build());

    while (page != null && result.size() < MAX_MESSAGES_PER_FOLDER) {
      page.messages().stream()
          .map(message -> toOutlookEmail(message, context.activityId()))
          .forEach(result::add);
      if (page.nextLink() == null) {
        break;
      }
      page = outlookMail.listNextMessages(page.nextLink());
    }
    return result;
  }

  private OutlookEmail toOutlookEmail(OutlookMessage msg, Long activityId) {
    return OutlookEmail.builder()
        // Preserve the legacy ID exposed by the existing API and persisted as sourceId. The
        // adapter retains the immutable ID alongside it for move/idempotency operations.
        .id(StringUtils.defaultIfBlank(msg.identity().legacyId(), msg.identity().immutableId()))
        .subject(msg.subject())
        .sender(msg.sender())
        .receivedAt(Optional.ofNullable(msg.receivedAt()).map(Object::toString).orElse(""))
        .preview(msg.preview())
        .activityId(activityId)
        .build();
  }

  private record FolderContext(String folderId, Long activityId, boolean rentalOnly) {}

  /** Holds the subject, plain-text body, and received date (YYYY-MM-DD) of an email message. */
  public record MessageContent(String subject, String body, String receivedDate) {}

  /**
   * Fetches the subject and plain-text body of a message by ID.
   *
   * @param messageId the Outlook message ID
   * @return the message content
   */
  public MessageContent fetchMessageBody(String messageId) {
    OutlookMessage message = outlookMail.getMessage(messageId, true).orElse(null);

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

  private String extractPdfAttachmentText(OutlookMessage message) {
    if (message == null) {
      return "";
    }
    log.info("Message has {} attachment(s)", message.attachments().size());
    message
        .attachments()
        .forEach(
            a ->
                log.info(
                    "  Attachment: name={} contentType={} isInline={}",
                    a.name(),
                    a.contentType(),
                    a.inline()));
    return message.attachments().stream()
        .filter(a -> StringUtils.startsWithIgnoreCase(a.contentType(), "application/pdf"))
        .map(
            a -> {
              byte[] bytes = a.contentBytes();
              log.info(
                  "  PDF attachment '{}': bytes={}",
                  a.name(),
                  bytes == null ? "null" : bytes.length);
              return pdfExtractorService.extractText(bytes);
            })
        .filter(t -> !t.isBlank())
        .collect(Collectors.joining("\n\n"));
  }

  private MessageContent toMessageContent(OutlookMessage message) {
    String plainText = stripHtml(Optional.ofNullable(message.body()).orElse(""));
    String receivedDate =
        Optional.ofNullable(message.receivedAt()).map(dt -> dt.toLocalDate().toString()).orElse("");
    return new MessageContent(message.subject(), plainText, receivedDate);
  }

  private String stripHtml(String html) {
    return WHITESPACE_PATTERN
        .matcher(HTML_TAG_PATTERN.matcher(html).replaceAll(" "))
        .replaceAll(" ")
        .trim();
  }
}

package com.bookie.intake.sources;

import com.bookie.catalog.activity.application.ActivityCatalog;
import com.bookie.intake.application.InboxItemStore;
import com.bookie.intake.domain.InboxArtifact;
import com.bookie.intake.domain.InboxArtifactType;
import com.bookie.intake.domain.InboxItem;
import com.bookie.intake.domain.InboxState;
import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.integrations.documents.DocumentTextExtractor;
import com.bookie.integrations.outlook.OutlookAttachment;
import com.bookie.integrations.outlook.OutlookFolder;
import com.bookie.integrations.outlook.OutlookMailPort;
import com.bookie.integrations.outlook.OutlookMessage;
import com.bookie.integrations.outlook.OutlookMessageIdentity;
import com.bookie.integrations.outlook.OutlookMessagePage;
import com.bookie.integrations.outlook.OutlookMessageQuery;
import com.bookie.integrations.outlook.OutlookMoveResult;
import com.bookie.ledger.application.LedgerImportReferenceQuery;
import com.bookie.ledger.domain.ExpenseSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
  private static final Set<String> SUPPORTED_ATTACHMENT_CONTENT_TYPES =
      Set.of("application/pdf", "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp");
  private static final Set<InboxState> TRANSIENT_INBOX_STATES =
      EnumSet.of(InboxState.RECEIVED, InboxState.QUEUED, InboxState.PROCESSING);
  private static final Set<String> SUPPORTED_ATTACHMENT_EXTENSIONS =
      Set.of(".pdf", ".jpg", ".jpeg", ".png", ".gif", ".webp");

  private final OutlookMailPort outlookMail;
  private final DocumentTextExtractor pdfExtractorService;
  private final LedgerImportReferenceQuery ledgerImportReferenceQuery;
  private final OutlookSettingsRepository outlookSettingsRepository;
  private final ActivityCatalog activityCatalog;
  private final InboxItemStore inboxItemStore;

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
    Map<String, FetchedEmail> uniqueEmailsMap =
        folderContexts.stream()
            .flatMap(context -> fetchMessagesFromFolder(context, year).stream())
            .collect(
                Collectors.toMap(
                    fetched -> fetched.email().id(),
                    fetched -> fetched,
                    (first, second) -> first)); // Keep first instance if duplicate
    List<FetchedEmail> allFetched =
        uniqueEmailsMap.values().stream()
            .sorted(
                Comparator.comparing((FetchedEmail fetched) -> fetched.email().receivedAt())
                    .reversed())
            .toList();

    List<String> allEmailIds = allFetched.stream().map(fetched -> fetched.email().id()).toList();
    Map<String, List<InboxItem>> activeItemsByMessageId =
        inboxItemStore.findActiveForOutlookMessages(allEmailIds).stream()
            .collect(Collectors.groupingBy(OutlookService::outlookMessageId));
    // Saving moves the message, which changes its legacy ID, so the ledger holds the pre-move ID.
    // Saved items sharing the stable immutable ID recover that ID.
    Map<String, Set<String>> savedParentIdsByImmutableId =
        inboxItemStore
            .findSavedForOutlookImmutableIds(
                allFetched.stream()
                    .map(FetchedEmail::immutableId)
                    .filter(StringUtils::isNotBlank)
                    .distinct()
                    .toList())
            .stream()
            .collect(
                Collectors.groupingBy(
                    InboxItem::getImmutableSourceId,
                    Collectors.mapping(OutlookService::outlookMessageId, Collectors.toSet())));
    Set<String> lookupIds = new LinkedHashSet<>(allEmailIds);
    savedParentIdsByImmutableId.values().forEach(lookupIds::addAll);
    Set<String> savedMessageIds =
        ledgerImportReferenceQuery.findSavedParentExternalIds(
            ExpenseSource.OUTLOOK_EMAIL.name(), List.copyOf(lookupIds));
    List<OutlookEmail> unsaved =
        allFetched.stream()
            .filter(
                fetched ->
                    !isSaved(fetched, savedMessageIds, savedParentIdsByImmutableId)
                        || !activeItemsByMessageId
                            .getOrDefault(fetched.email().id(), List.of())
                            .isEmpty())
            .map(FetchedEmail::email)
            .toList();

    int from = page * PAGE_SIZE;
    if (from >= unsaved.size()) {
      return new OutlookEmailsPage(List.of(), page, false);
    }
    int to = Math.min(from + PAGE_SIZE, unsaved.size());
    List<OutlookEmail> pageItems = unsaved.subList(from, to);

    List<OutlookEmail> enriched =
        pageItems.stream()
            .map(
                email -> {
                  List<InboxItem> inboxItems =
                      activeItemsByMessageId.getOrDefault(email.id(), List.of());
                  InboxItem inboxItem = inboxItems.stream().findFirst().orElse(null);
                  return OutlookEmail.builder()
                      .id(email.id())
                      .subject(email.subject())
                      .sender(email.sender())
                      .receivedAt(email.receivedAt())
                      .preview(email.preview())
                      .activityId(email.activityId())
                      .inboxItemId(inboxItem != null ? inboxItem.getId() : null)
                      .inboxState(aggregateInboxState(inboxItems))
                      .pendingCount(inboxItems.size())
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

  private static boolean isSaved(
      FetchedEmail fetched,
      Set<String> savedMessageIds,
      Map<String, Set<String>> savedParentIdsByImmutableId) {
    return savedMessageIds.contains(fetched.email().id())
        || Optional.ofNullable(fetched.immutableId()).map(savedParentIdsByImmutableId::get).stream()
            .flatMap(Set::stream)
            .anyMatch(savedMessageIds::contains);
  }

  private List<FetchedEmail> fetchMessagesFromFolder(FolderContext context, int year) {
    String filter =
        (context.rentalOnly() ? RENTAL_CATEGORY_FILTER_TEMPLATE : INTAKE_DATE_FILTER_TEMPLATE)
            .formatted(year, year + 1);
    List<FetchedEmail> result = new ArrayList<>();

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
          .map(
              message ->
                  new FetchedEmail(
                      toOutlookEmail(message, context.activityId()),
                      message.identity().immutableId()))
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
        .pendingCount(0)
        .build();
  }

  private record FolderContext(String folderId, Long activityId, boolean rentalOnly) {}

  private record FetchedEmail(OutlookEmail email, String immutableId) {}

  public record IntakeTarget(
      String sourceId,
      String outlookMessageId,
      String attachmentId,
      String attachmentName,
      String subject) {}

  /** Holds the subject, plain-text body, and received date (YYYY-MM-DD) of an email message. */
  public record MessageContent(String subject, String body, String receivedDate) {}

  public List<IntakeTarget> discoverIntakeTargets(String messageId) {
    OutlookMessage message =
        outlookMail
            .getMessage(messageId, true)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Outlook message not found: " + messageId));
    String parentId = StringUtils.defaultIfBlank(message.identity().legacyId(), messageId);
    List<OutlookAttachment> supported =
        message.attachments().stream().filter(this::isSupportedAttachment).toList();
    if (supported.isEmpty()) {
      return List.of(
          new IntakeTarget(
              parentId, parentId, null, null, StringUtils.defaultString(message.subject())));
    }

    Set<String> attachmentIds = new HashSet<>();
    List<IntakeTarget> targets = new ArrayList<>();
    for (OutlookAttachment attachment : supported) {
      if (StringUtils.isBlank(attachment.id()) || !attachmentIds.add(attachment.id())) {
        throw IntegrationException.builder()
            .kind(IntegrationFailureKind.INVALID_RESPONSE)
            .message("Outlook attachments require unique stable IDs")
            .build();
      }
      targets.add(
          new IntakeTarget(
              OutlookAttachmentIdentity.sourceId(parentId, attachment.id()),
              parentId,
              attachment.id(),
              attachment.name(),
              OutlookAttachmentIdentity.label(message.subject(), attachment.name())));
    }
    return List.copyOf(targets);
  }

  /**
   * Fetches the subject and plain-text body of a message by ID.
   *
   * @param messageId the Outlook message ID
   * @return the message content
   */
  public MessageContent fetchMessageBody(String messageId) {
    Optional<InboxArtifact> source =
        inboxItemStore
            .findBySourceIdentity(ExpenseSource.OUTLOOK_EMAIL, messageId)
            .flatMap(OutlookService::outlookArtifact);
    if (source.isPresent()) {
      return fetchMessageContent(source.get().getExternalId(), source.get().getTextValue());
    }
    return fetchMessageContent(messageId, null);
  }

  public MessageContent fetchMessageContent(String messageId, String attachmentId) {
    OutlookMessage message = outlookMail.getMessage(messageId, true).orElse(null);
    if (message == null) {
      return new MessageContent("", "", "");
    }
    MessageContent base = toMessageContent(message);
    if (StringUtils.isBlank(attachmentId)) {
      return base;
    }
    OutlookAttachment attachment =
        message.attachments().stream()
            .filter(candidate -> attachmentId.equals(candidate.id()))
            .filter(this::isSupportedAttachment)
            .findFirst()
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Outlook attachment not found: " + attachmentId));
    String attachmentText =
        pdfExtractorService.extractText(attachment.contentBytes(), attachment.name());
    String body =
        base.body()
            + "\n\n[Attachment: "
            + StringUtils.defaultIfBlank(attachment.name(), "Attachment")
            + "]\n"
            + attachmentText;
    return new MessageContent(
        OutlookAttachmentIdentity.label(base.subject(), attachment.name()),
        body.trim(),
        base.receivedDate());
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

  public boolean hasPendingItems(String outlookMessageId) {
    return !inboxItemStore.findActiveForOutlookMessages(List.of(outlookMessageId)).isEmpty();
  }

  private static Optional<InboxArtifact> outlookArtifact(InboxItem item) {
    return item.getArtifacts().stream()
        .filter(artifact -> artifact.getType() == InboxArtifactType.OUTLOOK_EMAIL)
        .filter(artifact -> StringUtils.isNotBlank(artifact.getExternalId()))
        .findFirst();
  }

  private static String outlookMessageId(InboxItem item) {
    return outlookArtifact(item).map(InboxArtifact::getExternalId).orElse(item.getLegacySourceId());
  }

  private boolean isSupportedAttachment(OutlookAttachment attachment) {
    if (attachment.inline()) {
      return false;
    }
    String contentType =
        StringUtils.defaultString(attachment.contentType()).toLowerCase(Locale.ROOT);
    if (SUPPORTED_ATTACHMENT_CONTENT_TYPES.contains(contentType)) {
      return true;
    }
    String name = StringUtils.defaultString(attachment.name()).toLowerCase(Locale.ROOT);
    return SUPPORTED_ATTACHMENT_EXTENSIONS.stream().anyMatch(name::endsWith);
  }

  private String aggregateInboxState(List<InboxItem> inboxItems) {
    if (inboxItems.isEmpty()) {
      return null;
    }
    if (inboxItems.stream().anyMatch(item -> TRANSIENT_INBOX_STATES.contains(item.getState()))) {
      return InboxState.PROCESSING.name();
    }
    if (inboxItems.stream().allMatch(item -> item.getState() == InboxState.FAILED)) {
      return InboxState.FAILED.name();
    }
    return InboxState.READY.name();
  }
}

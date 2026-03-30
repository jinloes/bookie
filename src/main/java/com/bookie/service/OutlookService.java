package com.bookie.service;

import com.bookie.model.Expense;
import com.bookie.model.OutlookEmail;
import com.bookie.model.OutlookEmailsPage;
import com.bookie.repository.ExpenseRepository;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.MailFolder;
import com.microsoft.graph.models.MailFolderCollectionResponse;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Service for interacting with Outlook email via Microsoft Graph API. */
@Service
public class OutlookService {

  private static final int PAGE_SIZE = 10;
  private static final String FOLDER_DISPLAY_FILTER =
      "displayName eq 'inbox' or displayName eq 'Rent Expenses'";
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
  private final ExpenseRepository expenseRepository;

  /**
   * Creates a new OutlookService.
   *
   * @param graphClient the Microsoft Graph client
   * @param expenseRepository the expense repository
   */
  public OutlookService(GraphServiceClient graphClient, ExpenseRepository expenseRepository) {
    this.graphClient = graphClient;
    this.expenseRepository = expenseRepository;
  }

  /**
   * Returns a paginated list of rental emails from Outlook for the given year.
   *
   * @param page zero-based page index
   * @param year calendar year to filter by; defaults to current year if not specified
   * @return the page of rental emails
   */
  public OutlookEmailsPage getRentalEmails(int page, int year) {
    var folderResp =
        graphClient
            .me()
            .mailFolders()
            .get(
                config ->
                    Objects.requireNonNull(config.queryParameters).filter = FOLDER_DISPLAY_FILTER);

    List<String> folderIds =
        Optional.ofNullable(folderResp)
            .map(MailFolderCollectionResponse::getValue)
            .map(folders -> folders.stream().map(MailFolder::getId).toList())
            .orElse(List.of());

    // Graph API $orderby applies per-folder only; a client-side sort is required
    // to produce a globally ordered result after merging across folders.
    List<OutlookEmail> sorted =
        folderIds.stream()
            .flatMap(id -> fetchMessagesFromFolder(id, year).stream())
            .sorted(Comparator.comparing(OutlookEmail::receivedAt).reversed())
            .toList();

    int from = page * PAGE_SIZE;
    if (from >= sorted.size()) {
      return new OutlookEmailsPage(List.of(), page, false);
    }
    int to = Math.min(from + PAGE_SIZE, sorted.size());
    List<OutlookEmail> pageItems = sorted.subList(from, to);

    // Batch lookup scoped to this page only — avoids querying all fetched emails
    // and limits DB work to the PAGE_SIZE items actually being returned.
    List<String> pageEmailIds = pageItems.stream().map(OutlookEmail::id).toList();
    Map<String, Long> expenseIdBySourceId =
        expenseRepository.findBySourceIdIn(pageEmailIds).stream()
            .collect(Collectors.toMap(Expense::getSourceId, Expense::getId));

    List<OutlookEmail> enriched =
        pageItems.stream()
            .map(
                email ->
                    new OutlookEmail(
                        email.id(),
                        email.subject(),
                        email.sender(),
                        email.receivedAt(),
                        email.preview(),
                        expenseIdBySourceId.get(email.id())))
            .toList();

    return new OutlookEmailsPage(enriched, page, to < sorted.size());
  }

  private List<OutlookEmail> fetchMessagesFromFolder(String folderId, int year) {
    String filter = RENTAL_CATEGORY_FILTER_TEMPLATE.formatted(year, year + 1);
    var resp =
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
                  config.queryParameters.top = 50;
                });

    return Optional.ofNullable(resp)
        .map(MessageCollectionResponse::getValue)
        .orElseGet(List::of)
        .stream()
        .map(
            msg ->
                new OutlookEmail(
                    msg.getId(),
                    msg.getSubject(),
                    Optional.ofNullable(msg.getFrom())
                        .map(Recipient::getEmailAddress)
                        .map(EmailAddress::getName)
                        .orElse(""),
                    Optional.ofNullable(msg.getReceivedDateTime()).map(Object::toString).orElse(""),
                    msg.getBodyPreview(),
                    null))
        .toList();
  }

  /** Holds the subject and plain-text body of an email message. */
  public record MessageContent(String subject, String body) {}

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
                config ->
                    Objects.requireNonNull(config.queryParameters).select =
                        new String[] {"subject", "body"});

    return Optional.ofNullable(message)
        .map(Message::getBody)
        .map(
            body -> {
              String content = Optional.ofNullable(body.getContent()).orElse("");
              String plainText =
                  WHITESPACE_PATTERN
                      .matcher(HTML_TAG_PATTERN.matcher(content).replaceAll(" "))
                      .replaceAll(" ")
                      .trim();
              return new MessageContent(message.getSubject(), plainText);
            })
        .orElse(new MessageContent("", ""));
  }
}

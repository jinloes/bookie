package com.bookie.integrations.outlook;

import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.integrations.MicrosoftGraphFailures;
import com.microsoft.graph.models.ConvertIdResult;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ExchangeIdFormat;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.MailFolder;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.messages.item.move.MovePostRequestBody;
import com.microsoft.graph.users.item.translateexchangeids.TranslateExchangeIdsPostRequestBody;
import com.microsoft.graph.users.item.translateexchangeids.TranslateExchangeIdsPostResponse;
import com.microsoft.kiota.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrosoftGraphOutlookAdapter implements OutlookMailPort {

  static final int TRANSLATION_BATCH_SIZE = 1000;
  private static final int DESTINATION_VERIFICATION_PAGE_LIMIT = 5;

  private final GraphServiceClient graphClient;

  @Override
  public List<OutlookFolder> listFolders(String filter, Integer top) {
    try {
      var response =
          graphClient
              .me()
              .mailFolders()
              .get(
                  configuration -> {
                    if (StringUtils.isNotBlank(filter)) {
                      Objects.requireNonNull(configuration.queryParameters).filter = filter;
                    }
                    if (top != null) {
                      Objects.requireNonNull(configuration.queryParameters).top = top;
                    }
                  });
      return Optional.ofNullable(response).map(value -> value.getValue()).orElse(List.of()).stream()
          .filter(Objects::nonNull)
          .map(MicrosoftGraphOutlookAdapter::toFolder)
          .toList();
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("list Outlook folders", e);
    }
  }

  @Override
  public List<OutlookFolder> listChildFolders(String folderId, Integer top) {
    try {
      var childFolders =
          graphClient
              .me()
              .mailFolders()
              .byMailFolderId(requireIdentifier(folderId, "folderId"))
              .childFolders();
      var response =
          top == null
              ? childFolders.get()
              : childFolders.get(
                  configuration -> Objects.requireNonNull(configuration.queryParameters).top = top);
      return Optional.ofNullable(response).map(value -> value.getValue()).orElse(List.of()).stream()
          .filter(Objects::nonNull)
          .map(MicrosoftGraphOutlookAdapter::toFolder)
          .toList();
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("list Outlook child folders", e);
    }
  }

  @Override
  public OutlookMessagePage listMessages(String folderId, OutlookMessageQuery query) {
    Objects.requireNonNull(query, "query");
    try {
      var response =
          graphClient
              .me()
              .mailFolders()
              .byMailFolderId(requireIdentifier(folderId, "folderId"))
              .messages()
              .get(
                  configuration -> {
                    GraphImmutableIdHeaders.apply(configuration);
                    if (StringUtils.isNotBlank(query.filter())) {
                      Objects.requireNonNull(configuration.queryParameters).filter = query.filter();
                    }
                    if (!query.select().isEmpty()) {
                      Objects.requireNonNull(configuration.queryParameters).select =
                          query.select().toArray(String[]::new);
                    }
                    if (!query.orderBy().isEmpty()) {
                      Objects.requireNonNull(configuration.queryParameters).orderby =
                          query.orderBy().toArray(String[]::new);
                    }
                    if (query.top() != null) {
                      Objects.requireNonNull(configuration.queryParameters).top = query.top();
                    }
                    if (query.expandAttachments()) {
                      Objects.requireNonNull(configuration.queryParameters).expand =
                          new String[] {"attachments"};
                    }
                  });
      return toPage(response);
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("list Outlook messages", e);
    }
  }

  @Override
  public OutlookMessagePage listNextMessages(String nextLink) {
    requireIdentifier(nextLink, "nextLink");
    try {
      var response =
          graphClient.me().messages().withUrl(nextLink).get(GraphImmutableIdHeaders::apply);
      return toPage(response);
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("list next Outlook message page", e);
    }
  }

  @Override
  public Optional<OutlookMessage> getMessage(String messageId, boolean includeAttachments) {
    try {
      Message message =
          graphClient
              .me()
              .messages()
              .byMessageId(requireIdentifier(messageId, "messageId"))
              .get(
                  configuration -> {
                    GraphImmutableIdHeaders.apply(configuration);
                    Objects.requireNonNull(configuration.queryParameters).select =
                        new String[] {
                          "id",
                          "subject",
                          "from",
                          "receivedDateTime",
                          "bodyPreview",
                          "body",
                          "parentFolderId"
                        };
                    if (includeAttachments) {
                      configuration.queryParameters.expand = new String[] {"attachments"};
                    }
                  });
      return Optional.ofNullable(message).map(value -> toMessage(value, messageId));
    } catch (ApiException e) {
      IntegrationException failure = MicrosoftGraphFailures.from("get Outlook message", e);
      if (failure.getKind() == IntegrationFailureKind.NOT_FOUND) {
        return Optional.empty();
      }
      throw failure;
    }
  }

  @Override
  public OutlookMoveResult moveToFolder(
      OutlookMessageIdentity identity, String destinationFolderId) {
    Objects.requireNonNull(identity, "identity");
    String destination = requireIdentifier(destinationFolderId, "destinationFolderId");
    OutlookMessage resolved = resolveForMove(identity, destination);
    OutlookMessageIdentity resolvedIdentity = mergeIdentity(identity, resolved.identity());
    if (destination.equals(resolved.parentFolderId())) {
      return new OutlookMoveResult(
          OutlookMoveResult.Status.ALREADY_AT_DESTINATION, resolvedIdentity);
    }

    MovePostRequestBody body = new MovePostRequestBody();
    body.setDestinationId(destination);
    try {
      Message moved =
          graphClient
              .me()
              .messages()
              .byMessageId(resolvedIdentity.durableId())
              .move()
              .post(body, GraphImmutableIdHeaders::apply);
      String immutableId =
          Optional.ofNullable(moved)
              .map(Message::getId)
              .filter(StringUtils::isNotBlank)
              .orElse(resolvedIdentity.durableId());
      return new OutlookMoveResult(
          OutlookMoveResult.Status.MOVED,
          new OutlookMessageIdentity(resolvedIdentity.legacyId(), immutableId));
    } catch (ApiException e) {
      IntegrationException failure = MicrosoftGraphFailures.from("move Outlook message", e);
      if (failure.getKind() == IntegrationFailureKind.NOT_FOUND
          && isInDestination(resolvedIdentity.durableId(), destination)) {
        return new OutlookMoveResult(
            OutlookMoveResult.Status.ALREADY_AT_DESTINATION, resolvedIdentity);
      }
      throw failure;
    }
  }

  @Override
  public List<OutlookMessageIdentity> translateLegacyIds(List<String> legacyIds) {
    return translateIds(
        legacyIds,
        "legacyId",
        ExchangeIdFormat.RestId,
        ExchangeIdFormat.RestImmutableEntryId,
        true);
  }

  private List<OutlookMessageIdentity> translateImmutableIds(List<String> immutableIds) {
    return translateIds(
        immutableIds,
        "immutableId",
        ExchangeIdFormat.RestImmutableEntryId,
        ExchangeIdFormat.RestId,
        false);
  }

  private List<OutlookMessageIdentity> translateIds(
      List<String> ids,
      String field,
      ExchangeIdFormat sourceFormat,
      ExchangeIdFormat targetFormat,
      boolean sourceIsLegacy) {
    List<String> requested =
        Optional.ofNullable(ids).orElse(List.of()).stream()
            .map(id -> requireIdentifier(id, field))
            .toList();
    Set<String> distinct = new LinkedHashSet<>(requested);
    if (distinct.size() != requested.size()) {
      throw new IllegalArgumentException("Outlook message IDs must be unique");
    }
    if (requested.isEmpty()) {
      return List.of();
    }

    Map<String, OutlookMessageIdentity> translated = new LinkedHashMap<>();
    for (int offset = 0; offset < requested.size(); offset += TRANSLATION_BATCH_SIZE) {
      int end = Math.min(offset + TRANSLATION_BATCH_SIZE, requested.size());
      translateBatch(
          requested.subList(offset, end), sourceFormat, targetFormat, sourceIsLegacy, translated);
    }
    if (!translated.keySet().equals(distinct)) {
      throw MicrosoftGraphFailures.invalidResponse("translate Outlook message IDs");
    }
    return requested.stream().map(translated::get).toList();
  }

  private OutlookMessage resolveForMove(
      OutlookMessageIdentity identity, String destinationFolderId) {
    String lookupId = identity.durableId();
    Optional<OutlookMessage> current = getMessage(lookupId, false);
    if (current.isPresent()) {
      return current.get();
    }
    String immutableId = identity.immutableId();
    if (StringUtils.isBlank(immutableId) && StringUtils.isNotBlank(identity.legacyId())) {
      immutableId = translateLegacyIds(List.of(identity.legacyId())).get(0).immutableId();
    }
    if (StringUtils.isNotBlank(immutableId) && isInDestination(immutableId, destinationFolderId)) {
      return new OutlookMessage(
          new OutlookMessageIdentity(identity.legacyId(), immutableId),
          null,
          null,
          null,
          null,
          null,
          destinationFolderId,
          List.of());
    }
    throw IntegrationException.builder()
        .kind(IntegrationFailureKind.NOT_FOUND)
        .message("Outlook message is missing and was not found in the destination folder")
        .statusCode(404)
        .build();
  }

  private boolean isInDestination(String immutableId, String destinationFolderId) {
    OutlookMessagePage page =
        listMessages(
            destinationFolderId,
            OutlookMessageQuery.builder().select(List.of("id", "parentFolderId")).top(100).build());
    for (int pageNumber = 0; pageNumber < DESTINATION_VERIFICATION_PAGE_LIMIT; pageNumber++) {
      if (page.messages().stream()
          .map(OutlookMessage::identity)
          .map(OutlookMessageIdentity::durableId)
          .anyMatch(immutableId::equals)) {
        return true;
      }
      if (StringUtils.isBlank(page.nextLink())) {
        return false;
      }
      if (pageNumber + 1 == DESTINATION_VERIFICATION_PAGE_LIMIT) {
        return false;
      }
      page = listNextMessages(page.nextLink());
    }
    return false;
  }

  private void translateBatch(
      List<String> batch,
      ExchangeIdFormat sourceFormat,
      ExchangeIdFormat targetFormat,
      boolean sourceIsLegacy,
      Map<String, OutlookMessageIdentity> translated) {
    TranslateExchangeIdsPostRequestBody body = new TranslateExchangeIdsPostRequestBody();
    body.setInputIds(batch);
    body.setSourceIdType(sourceFormat);
    body.setTargetIdType(targetFormat);
    TranslateExchangeIdsPostResponse response;
    try {
      response = graphClient.me().translateExchangeIds().post(body, GraphImmutableIdHeaders::apply);
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("translate Outlook message IDs", e);
    }
    List<ConvertIdResult> results =
        Optional.ofNullable(response)
            .map(TranslateExchangeIdsPostResponse::getValue)
            .orElse(List.of());
    Set<String> expected = Set.copyOf(batch);
    for (ConvertIdResult result : results) {
      if (result == null
          || result.getErrorDetails() != null
          || StringUtils.isAnyBlank(result.getSourceId(), result.getTargetId())
          || !expected.contains(result.getSourceId())
          || translated.containsKey(result.getSourceId())) {
        throw MicrosoftGraphFailures.invalidResponse("translate Outlook message IDs");
      }
      translated.put(
          result.getSourceId(),
          sourceIsLegacy
              ? new OutlookMessageIdentity(result.getSourceId(), result.getTargetId())
              : new OutlookMessageIdentity(result.getTargetId(), result.getSourceId()));
    }
    if (results.size() != batch.size()) {
      throw MicrosoftGraphFailures.invalidResponse("translate Outlook message IDs");
    }
  }

  private OutlookMessagePage toPage(com.microsoft.graph.models.MessageCollectionResponse response) {
    if (response == null) {
      return new OutlookMessagePage(List.of(), null);
    }
    List<OutlookMessage> messages =
        Optional.ofNullable(response.getValue()).orElse(List.of()).stream()
            .filter(Objects::nonNull)
            .map(message -> toMessage(message, null))
            .toList();
    if (messages.isEmpty()) {
      return new OutlookMessagePage(messages, response.getOdataNextLink());
    }
    Map<String, OutlookMessageIdentity> identitiesByImmutableId =
        translateImmutableIds(
                messages.stream()
                    .map(OutlookMessage::identity)
                    .map(OutlookMessageIdentity::immutableId)
                    .toList())
            .stream()
            .collect(
                java.util.stream.Collectors.toMap(
                    OutlookMessageIdentity::immutableId, identity -> identity));
    List<OutlookMessage> identifiedMessages =
        messages.stream()
            .map(
                message ->
                    withIdentity(
                        message, identitiesByImmutableId.get(message.identity().immutableId())))
            .toList();
    return new OutlookMessagePage(identifiedMessages, response.getOdataNextLink());
  }

  private static OutlookMessage withIdentity(
      OutlookMessage message, OutlookMessageIdentity identity) {
    if (identity == null) {
      throw MicrosoftGraphFailures.invalidResponse("translate Outlook message IDs");
    }
    return new OutlookMessage(
        identity,
        message.subject(),
        message.sender(),
        message.receivedAt(),
        message.preview(),
        message.body(),
        message.parentFolderId(),
        message.attachments());
  }

  private static OutlookMessage toMessage(Message message, String requestedId) {
    String immutableId = message.getId();
    if (StringUtils.isBlank(immutableId)) {
      throw MicrosoftGraphFailures.invalidResponse("read Outlook message");
    }
    String legacyId =
        StringUtils.isNotBlank(requestedId) && !requestedId.equals(immutableId)
            ? requestedId
            : null;
    List<OutlookAttachment> attachments = new ArrayList<>();
    Optional.ofNullable(message.getAttachments()).orElse(List.of()).stream()
        .filter(FileAttachment.class::isInstance)
        .map(FileAttachment.class::cast)
        .map(
            attachment ->
                new OutlookAttachment(
                    attachment.getId(),
                    attachment.getName(),
                    attachment.getContentType(),
                    Boolean.TRUE.equals(attachment.getIsInline()),
                    attachment.getContentBytes()))
        .forEach(attachments::add);
    return new OutlookMessage(
        new OutlookMessageIdentity(legacyId, immutableId),
        message.getSubject(),
        Optional.ofNullable(message.getFrom())
            .map(Recipient::getEmailAddress)
            .map(EmailAddress::getName)
            .orElse(""),
        message.getReceivedDateTime(),
        message.getBodyPreview(),
        Optional.ofNullable(message.getBody()).map(value -> value.getContent()).orElse(""),
        message.getParentFolderId(),
        attachments);
  }

  private static OutlookFolder toFolder(MailFolder folder) {
    return new OutlookFolder(folder.getId(), folder.getDisplayName());
  }

  private static OutlookMessageIdentity mergeIdentity(
      OutlookMessageIdentity requested, OutlookMessageIdentity resolved) {
    String legacyId = StringUtils.defaultIfBlank(requested.legacyId(), resolved.legacyId());
    String immutableId =
        StringUtils.defaultIfBlank(resolved.immutableId(), requested.immutableId());
    if (Objects.equals(legacyId, immutableId)) {
      legacyId = null;
    }
    return new OutlookMessageIdentity(legacyId, immutableId);
  }

  private static String requireIdentifier(String value, String field) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}

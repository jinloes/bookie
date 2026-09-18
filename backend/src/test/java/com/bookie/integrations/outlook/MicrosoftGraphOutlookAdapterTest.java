package com.bookie.integrations.outlook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.microsoft.graph.models.ConvertIdResult;
import com.microsoft.graph.models.ExchangeIdFormat;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionResponse;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.mailfolders.item.messages.MessagesRequestBuilder;
import com.microsoft.graph.users.item.messages.item.MessageItemRequestBuilder;
import com.microsoft.graph.users.item.messages.item.move.MoveRequestBuilder;
import com.microsoft.graph.users.item.translateexchangeids.TranslateExchangeIdsPostRequestBody;
import com.microsoft.graph.users.item.translateexchangeids.TranslateExchangeIdsPostResponse;
import com.microsoft.graph.users.item.translateexchangeids.TranslateExchangeIdsRequestBuilder;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.BaseRequestConfiguration;
import com.microsoft.kiota.RequestAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MicrosoftGraphOutlookAdapterTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private GraphServiceClient graphClient;

  private MicrosoftGraphOutlookAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new MicrosoftGraphOutlookAdapter(graphClient);
  }

  @Test
  void listsMessagesWithImmutableHeadersAndBothIdentityForms() {
    Message message = message("immutable-1", "source-folder");
    when(graphClient.me().mailFolders().byMailFolderId("source-folder").messages().get(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<MessagesRequestBuilder.GetRequestConfiguration> configure =
                  invocation.getArgument(0);
              MessagesRequestBuilder builder =
                  new MessagesRequestBuilder(
                      "https://example.test/messages", mock(RequestAdapter.class));
              assertImmutableHeader(configure, builder.new GetRequestConfiguration());
              return response(message);
            });
    stubTranslation(
        body -> {
          assertThat(body.getSourceIdType()).isEqualTo(ExchangeIdFormat.RestImmutableEntryId);
          assertThat(body.getTargetIdType()).isEqualTo(ExchangeIdFormat.RestId);
          return body.getInputIds().stream().map(ignored -> "legacy-1").toList();
        },
        null);

    OutlookMessagePage page =
        adapter.listMessages(
            "source-folder", OutlookMessageQuery.builder().select(List.of("id")).build());

    assertThat(page.messages()).singleElement();
    assertThat(page.messages().getFirst().identity())
        .isEqualTo(new OutlookMessageIdentity("legacy-1", "immutable-1"));
  }

  @Test
  void appliesImmutableHeaderToPaginationRequests() {
    when(graphClient.me().messages().withUrl("https://next.test/page").get(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<
                      com.microsoft.graph.users.item.messages.MessagesRequestBuilder
                          .GetRequestConfiguration>
                  configure = invocation.getArgument(0);
              var builder =
                  new com.microsoft.graph.users.item.messages.MessagesRequestBuilder(
                      "https://next.test/page", mock(RequestAdapter.class));
              assertImmutableHeader(configure, builder.new GetRequestConfiguration());
              return response(message("immutable-1", "source-folder"));
            });
    stubTranslation(body -> body.getInputIds().stream().map(ignored -> "legacy-1").toList(), null);

    OutlookMessagePage page = adapter.listNextMessages("https://next.test/page");

    assertThat(page.messages().getFirst().identity().legacyId()).isEqualTo("legacy-1");
  }

  @Test
  void preservesGraphAttachmentIdentityAndContent() {
    Message message = message("immutable-1", "source-folder");
    FileAttachment attachment = new FileAttachment();
    attachment.setId("attachment-1");
    attachment.setName("receipt.pdf");
    attachment.setContentType("application/pdf");
    attachment.setIsInline(false);
    attachment.setContentBytes(new byte[] {1, 2, 3});
    message.setAttachments(List.of(attachment));
    when(graphClient.me().messages().byMessageId("legacy-1").get(any())).thenReturn(message);

    OutlookMessage result = adapter.getMessage("legacy-1", true).orElseThrow();

    assertThat(result.attachments()).singleElement();
    assertThat(result.attachments().getFirst())
        .extracting(
            OutlookAttachment::id,
            OutlookAttachment::name,
            OutlookAttachment::contentType,
            OutlookAttachment::inline)
        .containsExactly("attachment-1", "receipt.pdf", "application/pdf", false);
    assertThat(result.attachments().getFirst().contentBytes()).containsExactly(1, 2, 3);
  }

  @Test
  void moveUsesImmutableIdAndHeaderWhilePreservingLegacyId() {
    when(graphClient.me().messages().byMessageId("legacy-1").get(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<MessageItemRequestBuilder.GetRequestConfiguration> configure =
                  invocation.getArgument(0);
              MessageItemRequestBuilder builder =
                  new MessageItemRequestBuilder(
                      "https://example.test/message", mock(RequestAdapter.class));
              assertImmutableHeader(configure, builder.new GetRequestConfiguration());
              return message("immutable-1", "source-folder");
            });
    when(graphClient.me().messages().byMessageId("immutable-1").move().post(any(), any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<MoveRequestBuilder.PostRequestConfiguration> configure =
                  invocation.getArgument(1);
              MoveRequestBuilder builder =
                  new MoveRequestBuilder("https://example.test/move", mock(RequestAdapter.class));
              assertImmutableHeader(configure, builder.new PostRequestConfiguration());
              return message("immutable-1", "destination-folder");
            });

    OutlookMoveResult result =
        adapter.moveToFolder(OutlookMessageIdentity.unresolved("legacy-1"), "destination-folder");

    assertThat(result.status()).isEqualTo(OutlookMoveResult.Status.MOVED);
    assertThat(result.identity()).isEqualTo(new OutlookMessageIdentity("legacy-1", "immutable-1"));
  }

  @Test
  void missingSourceIsSuccessWhenBoundedDestinationCheckFindsImmutableId() {
    ApiException notFound = mock(ApiException.class);
    when(notFound.getResponseStatusCode()).thenReturn(404);
    when(graphClient.me().messages().byMessageId("legacy-1").get(any())).thenThrow(notFound);
    stubTranslation(
        body ->
            body.getSourceIdType() == ExchangeIdFormat.RestId
                ? List.of("immutable-1")
                : List.of("legacy-1"),
        null);
    when(graphClient.me().mailFolders().byMailFolderId("destination-folder").messages().get(any()))
        .thenReturn(response(message("immutable-1", "destination-folder")));

    OutlookMoveResult result =
        adapter.moveToFolder(OutlookMessageIdentity.unresolved("legacy-1"), "destination-folder");

    assertThat(result.status()).isEqualTo(OutlookMoveResult.Status.ALREADY_AT_DESTINATION);
    assertThat(result.identity()).isEqualTo(new OutlookMessageIdentity("legacy-1", "immutable-1"));
    verify(graphClient.me().messages().byMessageId("immutable-1").move(), never())
        .post(any(), any());
  }

  @Test
  void translatesLegacyIdsInValidatedBatchesOfOneThousand() {
    List<Integer> batchSizes = new ArrayList<>();
    AtomicReference<ExchangeIdFormat> sourceFormat = new AtomicReference<>();
    stubTranslation(
        body -> {
          batchSizes.add(body.getInputIds().size());
          sourceFormat.set(body.getSourceIdType());
          return body.getInputIds().stream().map(id -> "immutable-" + id).toList();
        },
        null);
    List<String> legacyIds = IntStream.range(0, 1001).mapToObj(index -> "legacy-" + index).toList();

    List<OutlookMessageIdentity> result = adapter.translateLegacyIds(legacyIds);

    assertThat(batchSizes).containsExactly(1000, 1);
    assertThat(sourceFormat.get()).isEqualTo(ExchangeIdFormat.RestId);
    assertThat(result).hasSize(1001);
    assertThat(result.getLast())
        .isEqualTo(new OutlookMessageIdentity("legacy-1000", "immutable-legacy-1000"));
  }

  @Test
  void rejectsPartialTranslationResponsesAsManualReviewFailures() {
    stubTranslation(body -> List.of("immutable-only-first"), 1);

    assertThatThrownBy(() -> adapter.translateLegacyIds(List.of("legacy-1", "legacy-2")))
        .isInstanceOfSatisfying(
            IntegrationException.class,
            failure -> {
              assertThat(failure.getKind()).isEqualTo(IntegrationFailureKind.INVALID_RESPONSE);
              assertThat(failure.isRetryable()).isFalse();
              assertThat(failure.isManualReviewRequired()).isTrue();
            });
  }

  private void stubTranslation(
      Function<TranslateExchangeIdsPostRequestBody, List<String>> targetIds,
      Integer responseLimit) {
    when(graphClient.me().translateExchangeIds().post(any(), any()))
        .thenAnswer(
            invocation -> {
              TranslateExchangeIdsPostRequestBody body = invocation.getArgument(0);
              @SuppressWarnings("unchecked")
              Consumer<TranslateExchangeIdsRequestBuilder.PostRequestConfiguration> configure =
                  invocation.getArgument(1);
              TranslateExchangeIdsRequestBuilder builder =
                  new TranslateExchangeIdsRequestBuilder(
                      "https://example.test/translate", mock(RequestAdapter.class));
              assertImmutableHeader(configure, builder.new PostRequestConfiguration());

              List<String> targets = targetIds.apply(body);
              int count =
                  responseLimit == null
                      ? body.getInputIds().size()
                      : Math.min(responseLimit, body.getInputIds().size());
              List<ConvertIdResult> values = new ArrayList<>();
              for (int index = 0; index < count; index++) {
                ConvertIdResult value = new ConvertIdResult();
                value.setSourceId(body.getInputIds().get(index));
                value.setTargetId(targets.get(index));
                values.add(value);
              }
              TranslateExchangeIdsPostResponse response = new TranslateExchangeIdsPostResponse();
              response.setValue(values);
              return response;
            });
  }

  private static <T extends BaseRequestConfiguration> void assertImmutableHeader(
      Consumer<T> configure, T configuration) {
    configure.accept(configuration);
    assertThat(configuration.headers.get(GraphImmutableIdHeaders.HEADER_NAME))
        .containsExactly(GraphImmutableIdHeaders.HEADER_VALUE);
  }

  private static Message message(String id, String parentFolderId) {
    Message message = new Message();
    message.setId(id);
    message.setParentFolderId(parentFolderId);
    return message;
  }

  private static MessageCollectionResponse response(Message... messages) {
    MessageCollectionResponse response = new MessageCollectionResponse();
    response.setValue(List.of(messages));
    return response;
  }
}

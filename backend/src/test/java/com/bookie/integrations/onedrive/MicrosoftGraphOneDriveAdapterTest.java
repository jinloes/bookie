package com.bookie.integrations.onedrive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.microsoft.graph.drives.item.items.item.children.ChildrenRequestBuilder;
import com.microsoft.graph.models.Drive;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.DriveItemCollectionResponse;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import com.microsoft.kiota.RequestAdapter;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MicrosoftGraphOneDriveAdapterTest {

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private GraphServiceClient graphClient;

  private MicrosoftGraphOneDriveAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new MicrosoftGraphOneDriveAdapter(graphClient);
  }

  @Test
  void listsTypedItemsUsingAValidatedPathAndStableOrdering() {
    stubDrive();
    when(graphClient
            .drives()
            .byDriveId("drive-1")
            .items()
            .byDriveItemId("root:/bookie/taxes:")
            .children()
            .get(any()))
        .thenAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Consumer<ChildrenRequestBuilder.GetRequestConfiguration> configure =
                  invocation.getArgument(0);
              ChildrenRequestBuilder builder =
                  new ChildrenRequestBuilder(
                      "https://example.test/children", mock(RequestAdapter.class));
              ChildrenRequestBuilder.GetRequestConfiguration configuration =
                  builder.new GetRequestConfiguration();
              configure.accept(configuration);
              assertThat(configuration.queryParameters.orderby)
                  .containsExactly("lastModifiedDateTime desc");
              return response(folder("folder-1", "2026"), file("file-1", "receipt.pdf"));
            });

    List<OneDriveItem> result = adapter.listChildren("/bookie/taxes");

    assertThat(result)
        .extracting(OneDriveItem::id, OneDriveItem::name, OneDriveItem::folder)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("folder-1", "2026", true),
            org.assertj.core.groups.Tuple.tuple("file-1", "receipt.pdf", false));
  }

  @Test
  void returnsEmptyWhenARequestedItemDoesNotExist() {
    stubDrive();
    ApiException notFound = mock(ApiException.class);
    when(notFound.getResponseStatusCode()).thenReturn(404);
    when(graphClient.drives().byDriveId("drive-1").items().byDriveItemId("missing").get())
        .thenThrow(notFound);

    assertThat(adapter.getItem("missing")).isEmpty();
  }

  @Test
  void reportsExistingNonFolderDestinationAsConflict() {
    stubDrive();
    when(graphClient
            .drives()
            .byDriveId("drive-1")
            .items()
            .byDriveItemId("root:/bookie/taxes/2026:")
            .get())
        .thenReturn(file("file-1", "2026"));

    assertThatThrownBy(() -> adapter.ensureFolder("bookie/taxes", "2026"))
        .isInstanceOfSatisfying(
            IntegrationException.class,
            failure -> {
              assertThat(failure.getKind()).isEqualTo(IntegrationFailureKind.CONFLICT);
              assertThat(failure.isManualReviewRequired()).isTrue();
            });
  }

  @Test
  void mapsRateLimitsToRetryableTypedFailures() {
    stubDrive();
    ApiException rateLimited = mock(ApiException.class);
    when(rateLimited.getResponseStatusCode()).thenReturn(429);
    when(graphClient.drives().byDriveId("drive-1").items().byDriveItemId("file-1").get())
        .thenThrow(rateLimited);

    assertThatThrownBy(() -> adapter.getItem("file-1"))
        .isInstanceOfSatisfying(
            IntegrationException.class,
            failure -> {
              assertThat(failure.getKind()).isEqualTo(IntegrationFailureKind.RATE_LIMITED);
              assertThat(failure.isRetryable()).isTrue();
              assertThat(failure.isManualReviewRequired()).isFalse();
            });
  }

  @Test
  void rejectsTraversalBeforeCallingGraph() {
    assertThatThrownBy(() -> adapter.listChildren("../outside"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("configured root");
  }

  private void stubDrive() {
    Drive drive = new Drive();
    drive.setId("drive-1");
    when(graphClient.me().drive().get()).thenReturn(drive);
  }

  private static DriveItem file(String id, String name) {
    DriveItem item = new DriveItem();
    item.setId(id);
    item.setName(name);
    return item;
  }

  private static DriveItem folder(String id, String name) {
    DriveItem item = file(id, name);
    item.setFolder(new Folder());
    return item;
  }

  private static DriveItemCollectionResponse response(DriveItem... items) {
    DriveItemCollectionResponse response = new DriveItemCollectionResponse();
    response.setValue(List.of(items));
    return response;
  }
}

package com.bookie.service;

import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over the Microsoft Graph drive APIs so the rest of the codebase doesn't have to
 * chain {@code graphClient.drives().byDriveId(...).items().byDriveItemId(...)...} every time. The
 * wrapper exists primarily to keep services testable: mocking five methods on this class is
 * dramatically less brittle than mocking a 9-deep Graph SDK builder chain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OneDriveClient {

  private final GraphServiceClient graphClient;

  /** Uploads bytes to {@code root:/{path}}, creating or overwriting the item. */
  public DriveItem upload(String path, InputStream content) {
    return graphClient
        .drives()
        .byDriveId(driveId())
        .items()
        .byDriveItemId("root:/" + path + ":")
        .content()
        .put(content);
  }

  /**
   * Lists immediate children of {@code root:/{folderPath}}, ordered by last-modified descending.
   * Returns an empty list if the folder does not yet exist.
   */
  public List<DriveItem> listChildren(String folderPath) {
    try {
      var resp =
          graphClient
              .drives()
              .byDriveId(driveId())
              .items()
              .byDriveItemId("root:/" + folderPath + ":")
              .children()
              .get(
                  config ->
                      config.queryParameters.orderby = new String[] {"lastModifiedDateTime desc"});
      return resp != null && resp.getValue() != null ? resp.getValue() : List.of();
    } catch (Exception e) {
      log.warn(
          "listChildren failed for {} (folder may not exist yet): {}", folderPath, e.getMessage());
      return List.of();
    }
  }

  public DriveItem getItem(String fileId) {
    return graphClient.drives().byDriveId(driveId()).items().byDriveItemId(fileId).get();
  }

  public InputStream download(String fileId) {
    return graphClient.drives().byDriveId(driveId()).items().byDriveItemId(fileId).content().get();
  }

  public void delete(String fileId) {
    graphClient.drives().byDriveId(driveId()).items().byDriveItemId(fileId).delete();
  }

  private String driveId() {
    return graphClient.me().drive().get().getId();
  }
}

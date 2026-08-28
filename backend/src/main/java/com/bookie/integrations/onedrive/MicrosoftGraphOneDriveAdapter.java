package com.bookie.integrations.onedrive;

import com.bookie.integrations.IntegrationException;
import com.bookie.integrations.IntegrationFailureKind;
import com.bookie.integrations.MicrosoftGraphFailures;
import com.microsoft.graph.models.DriveItem;
import com.microsoft.graph.models.Folder;
import com.microsoft.graph.models.ItemReference;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.kiota.ApiException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrosoftGraphOneDriveAdapter implements OneDrivePort {

  private final GraphServiceClient graphClient;

  @Override
  public OneDriveItem upload(String path, InputStream content) {
    String normalizedPath = normalizePath(path);
    Objects.requireNonNull(content, "content");
    try {
      return toItem(
          graphClient
              .drives()
              .byDriveId(driveId())
              .items()
              .byDriveItemId("root:/" + normalizedPath + ":")
              .content()
              .put(content),
          "upload OneDrive item");
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("upload OneDrive item", e);
    }
  }

  @Override
  public List<OneDriveItem> listChildren(String folderPath) {
    String normalizedPath = normalizePath(folderPath);
    try {
      var response =
          graphClient
              .drives()
              .byDriveId(driveId())
              .items()
              .byDriveItemId("root:/" + normalizedPath + ":")
              .children()
              .get(
                  config ->
                      Objects.requireNonNull(config.queryParameters).orderby =
                          new String[] {"lastModifiedDateTime desc"});
      return Optional.ofNullable(response).map(value -> value.getValue()).orElse(List.of()).stream()
          .filter(Objects::nonNull)
          .map(MicrosoftGraphOneDriveAdapter::toItem)
          .toList();
    } catch (ApiException e) {
      IntegrationException failure = MicrosoftGraphFailures.from("list OneDrive folder", e);
      if (failure.getKind() == IntegrationFailureKind.NOT_FOUND) {
        return List.of();
      }
      throw failure;
    }
  }

  @Override
  public List<OneDriveItem> listChildrenById(String itemId) {
    try {
      var response =
          graphClient
              .drives()
              .byDriveId(driveId())
              .items()
              .byDriveItemId(requireIdentifier(itemId, "itemId"))
              .children()
              .get();
      return Optional.ofNullable(response).map(value -> value.getValue()).orElse(List.of()).stream()
          .filter(Objects::nonNull)
          .map(MicrosoftGraphOneDriveAdapter::toItem)
          .toList();
    } catch (ApiException e) {
      IntegrationException failure = MicrosoftGraphFailures.from("list OneDrive item children", e);
      if (failure.getKind() == IntegrationFailureKind.NOT_FOUND) {
        return List.of();
      }
      throw failure;
    }
  }

  @Override
  public Optional<OneDriveItem> getItem(String fileId) {
    try {
      return Optional.ofNullable(
              graphClient
                  .drives()
                  .byDriveId(driveId())
                  .items()
                  .byDriveItemId(requireIdentifier(fileId, "fileId"))
                  .get())
          .map(MicrosoftGraphOneDriveAdapter::toItem);
    } catch (ApiException e) {
      IntegrationException failure = MicrosoftGraphFailures.from("get OneDrive item", e);
      if (failure.getKind() == IntegrationFailureKind.NOT_FOUND) {
        return Optional.empty();
      }
      throw failure;
    }
  }

  @Override
  public InputStream download(String fileId) {
    try {
      return graphClient
          .drives()
          .byDriveId(driveId())
          .items()
          .byDriveItemId(requireIdentifier(fileId, "fileId"))
          .content()
          .get();
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("download OneDrive item", e);
    }
  }

  @Override
  public void delete(String fileId) {
    try {
      graphClient
          .drives()
          .byDriveId(driveId())
          .items()
          .byDriveItemId(requireIdentifier(fileId, "fileId"))
          .delete();
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("delete OneDrive item", e);
    }
  }

  @Override
  public String ensureFolder(String parentPath, String name) {
    String normalizedParent = normalizePath(parentPath);
    String normalizedName = normalizeSegment(name, "name");
    String fullPath = normalizedParent + "/" + normalizedName;
    Optional<OneDriveItem> existing = getItemByPath(fullPath);
    if (existing.isPresent()) {
      if (!existing.get().folder()) {
        throw IntegrationException.builder()
            .kind(IntegrationFailureKind.CONFLICT)
            .message("OneDrive destination exists but is not a folder")
            .build();
      }
      return existing.get().id();
    }

    DriveItem folder = new DriveItem();
    folder.setName(normalizedName);
    folder.setFolder(new Folder());
    try {
      DriveItem created =
          graphClient
              .drives()
              .byDriveId(driveId())
              .items()
              .byDriveItemId("root:/" + normalizedParent + ":")
              .children()
              .post(folder);
      return toItem(created, "create OneDrive folder").id();
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("create OneDrive folder", e);
    }
  }

  @Override
  public void move(String itemId, String destinationFolderId) {
    DriveItem update = new DriveItem();
    ItemReference parent = new ItemReference();
    parent.setId(requireIdentifier(destinationFolderId, "destinationFolderId"));
    update.setParentReference(parent);
    try {
      graphClient
          .drives()
          .byDriveId(driveId())
          .items()
          .byDriveItemId(requireIdentifier(itemId, "itemId"))
          .patch(update);
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("move OneDrive item", e);
    }
  }

  private Optional<OneDriveItem> getItemByPath(String path) {
    try {
      return Optional.ofNullable(
              graphClient
                  .drives()
                  .byDriveId(driveId())
                  .items()
                  .byDriveItemId("root:/" + normalizePath(path) + ":")
                  .get())
          .map(MicrosoftGraphOneDriveAdapter::toItem);
    } catch (ApiException e) {
      IntegrationException failure = MicrosoftGraphFailures.from("get OneDrive path", e);
      if (failure.getKind() == IntegrationFailureKind.NOT_FOUND) {
        return Optional.empty();
      }
      throw failure;
    }
  }

  private String driveId() {
    try {
      var drive = graphClient.me().drive().get();
      if (drive == null || StringUtils.isBlank(drive.getId())) {
        throw MicrosoftGraphFailures.invalidResponse("resolve OneDrive");
      }
      return drive.getId();
    } catch (ApiException e) {
      throw MicrosoftGraphFailures.from("resolve OneDrive", e);
    }
  }

  private static OneDriveItem toItem(DriveItem item, String operation) {
    if (item == null || StringUtils.isBlank(item.getId())) {
      throw MicrosoftGraphFailures.invalidResponse(operation);
    }
    return toItem(item);
  }

  private static OneDriveItem toItem(DriveItem item) {
    return new OneDriveItem(
        item.getId(),
        item.getName(),
        Optional.ofNullable(item.getSize()).orElse(0L),
        Optional.ofNullable(item.getLastModifiedDateTime()).map(Object::toString).orElse(""),
        Optional.ofNullable(item.getCreatedDateTime()).map(Object::toString).orElse(null),
        item.getWebUrl(),
        Optional.ofNullable(item.getParentReference()).map(ItemReference::getPath).orElse(null),
        item.getFolder() != null);
  }

  private static String normalizePath(String path) {
    if (StringUtils.isBlank(path)) {
      throw new IllegalArgumentException("OneDrive path must not be blank");
    }
    String normalized = path.replace('\\', '/');
    while (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    if (normalized.isBlank()
        || normalized.equals("..")
        || normalized.startsWith("../")
        || normalized.endsWith("/..")
        || normalized.contains("/../")) {
      throw new IllegalArgumentException("OneDrive path must stay under its configured root");
    }
    return normalized;
  }

  private static String normalizeSegment(String value, String field) {
    String normalized = requireIdentifier(value, field);
    if (normalized.contains("/") || normalized.contains("\\") || normalized.equals("..")) {
      throw new IllegalArgumentException(field + " must be a single path segment");
    }
    return normalized;
  }

  private static String requireIdentifier(String value, String field) {
    if (StringUtils.isBlank(value)) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}

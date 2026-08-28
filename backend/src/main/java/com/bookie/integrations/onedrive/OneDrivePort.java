package com.bookie.integrations.onedrive;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

public interface OneDrivePort {

  OneDriveItem upload(String path, InputStream content);

  List<OneDriveItem> listChildren(String folderPath);

  List<OneDriveItem> listChildrenById(String itemId);

  Optional<OneDriveItem> getItem(String fileId);

  InputStream download(String fileId);

  void delete(String fileId);

  String ensureFolder(String parentPath, String name);

  void move(String itemId, String destinationFolderId);
}

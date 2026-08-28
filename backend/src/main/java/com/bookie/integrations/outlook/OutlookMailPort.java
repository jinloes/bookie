package com.bookie.integrations.outlook;

import java.util.List;
import java.util.Optional;

public interface OutlookMailPort {

  List<OutlookFolder> listFolders(String filter, Integer top);

  List<OutlookFolder> listChildFolders(String folderId, Integer top);

  OutlookMessagePage listMessages(String folderId, OutlookMessageQuery query);

  OutlookMessagePage listNextMessages(String nextLink);

  Optional<OutlookMessage> getMessage(String messageId, boolean includeAttachments);

  OutlookMoveResult moveToFolder(OutlookMessageIdentity identity, String destinationFolderId);

  List<OutlookMessageIdentity> translateLegacyIds(List<String> legacyIds);
}

package com.bookie.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outlook_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutlookSettings {

  public static final String DEFAULT_RECEIPTS_FOLDER = "bookie/receipts";

  @Id private Long id;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "outlook_settings_folder",
      joinColumns = @JoinColumn(name = "settings_id"))
  private List<FolderSetting> folderSettings;

  // Nullable at the DB level so existing rows don't fail schema update; ReceiptService
  // falls back to DEFAULT_RECEIPTS_FOLDER when blank or null.
  private String receiptsFolderBase;

  // Additional OneDrive folders (outside receiptsFolderBase) to scan for receipts that were
  // already placed there by something other than Bookie's own upload flow (e.g. a phone
  // scanning app saving directly into OneDrive).
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "outlook_settings_receipts_import_folder",
      joinColumns = @JoinColumn(name = "settings_id"))
  @jakarta.persistence.Column(name = "folder_path")
  @Builder.Default
  private List<String> receiptsImportFolders = new ArrayList<>();

  @jakarta.persistence.Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
  private boolean autoMoveEnabled;

  // Nullable; required when autoMoveEnabled is true.
  private String moveDestinationFolderId;
}

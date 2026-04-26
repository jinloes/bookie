package com.bookie.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
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

  @ElementCollection
  @CollectionTable(
      name = "outlook_settings_folder",
      joinColumns = @JoinColumn(name = "settings_id"))
  private List<FolderSetting> folderSettings;

  // Nullable at the DB level so existing rows don't fail schema update; ReceiptService
  // falls back to DEFAULT_RECEIPTS_FOLDER when blank or null.
  private String receiptsFolderBase;

  @jakarta.persistence.Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
  private boolean autoMoveEnabled;

  // Nullable; required when autoMoveEnabled is true.
  private String moveDestinationFolderId;
}

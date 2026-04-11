package com.bookie.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "outlook_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OutlookSettings {

  @Id private Long id;

  @ElementCollection
  @CollectionTable(
      name = "outlook_settings_folder",
      joinColumns = @JoinColumn(name = "settings_id"))
  private List<FolderSetting> folderSettings;
}

package com.bookie.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderSetting {

  @Column(name = "folder_id")
  private String folderId;

  @Column(name = "expand_subfolders")
  private boolean expandSubfolders;
}

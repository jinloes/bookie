package com.bookie.intake.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inbox_artifacts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxArtifact {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "inbox_item_id", nullable = false)
  private InboxItem inboxItem;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private InboxArtifactType type;

  @Column(name = "text_value", length = 2000)
  private String textValue;

  @Column(name = "external_id")
  private String externalId;

  @Column(name = "file_name", length = 500)
  private String fileName;

  @Column(length = 64)
  private String sha256;
}

package com.bookie.intake.domain;

import com.bookie.model.ExpenseSource;
import com.bookie.model.TransactionDirection;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inbox_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private ExpenseSource origin;

  @Column(name = "legacy_source_type", length = 50)
  private String legacySourceType;

  @Column(name = "legacy_source_id")
  private String legacySourceId;

  @Column(name = "immutable_source_id")
  private String immutableSourceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private InboxState state;

  @Enumerated(EnumType.STRING)
  @Column(name = "external_sync_state", nullable = false, length = 30)
  private ExternalSyncState externalSyncState;

  @Enumerated(EnumType.STRING)
  @Column(name = "proposed_direction", length = 20)
  private TransactionDirection proposedDirection;

  @Column(name = "raw_status", nullable = false, length = 50)
  private String rawStatus;

  @Column(length = 1000)
  private String subject;

  @Column(name = "proposed_amount", precision = 38, scale = 2)
  private BigDecimal proposedAmount;

  @Column(name = "proposed_description", length = 2000)
  private String proposedDescription;

  @Column(name = "proposed_date")
  private LocalDate proposedDate;

  @Column(name = "proposed_category")
  private String proposedCategory;

  @Column(name = "proposed_property_name")
  private String proposedPropertyName;

  @Column(name = "proposed_counterparty_name")
  private String proposedCounterpartyName;

  @Column(name = "proposed_source_label", length = 500)
  private String proposedSourceLabel;

  @Column(name = "legacy_activity_id")
  private Long legacyActivityId;

  @Column(name = "legacy_category_id")
  private Long legacyCategoryId;

  @Column(name = "legacy_property_id")
  private Long legacyPropertyId;

  @Column(name = "legacy_counterparty_id")
  private Long legacyCounterpartyId;

  @Column(name = "configured_activity_id")
  private Long configuredActivityId;

  @Column(name = "classification_ambiguous", nullable = false)
  private boolean classificationAmbiguous;

  @Column(name = "error_message", length = 2000)
  private String errorMessage;

  @Column(name = "receipt_external_id")
  private String receiptExternalId;

  @Column(name = "receipt_file_name", length = 500)
  private String receiptFileName;

  @Column(name = "financial_transaction_id")
  private Long financialTransactionId;

  @Column(name = "migration_legacy_table", length = 30)
  private String migrationLegacyTable;

  @Column(name = "migration_legacy_id")
  private Long migrationLegacyId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @OneToMany(
      mappedBy = "inboxItem",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @OrderBy("id ASC")
  @Builder.Default
  private Set<InboxArtifact> artifacts = new LinkedHashSet<>();

  public void addArtifact(InboxArtifact artifact) {
    artifact.setInboxItem(this);
    artifacts.add(artifact);
  }

  public void removeArtifacts(InboxArtifactType type) {
    artifacts.removeIf(artifact -> artifact.getType() == type);
  }

  @PrePersist
  private void initializeAuditFields() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
    if (state == null) {
      state = InboxState.RECEIVED;
    }
    if (externalSyncState == null) {
      externalSyncState = ExternalSyncState.NOT_REQUIRED;
    }
  }

  @PreUpdate
  private void updateAuditTimestamp() {
    updatedAt = LocalDateTime.now();
  }
}

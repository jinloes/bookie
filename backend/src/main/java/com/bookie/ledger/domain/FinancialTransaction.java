package com.bookie.ledger.domain;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@Table(name = "financial_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull
  @Positive
  @Digits(integer = 36, fraction = 2)
  @Column(nullable = false, precision = 38, scale = 2)
  private BigDecimal amount;

  @NotNull
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionDirection direction;

  @NotNull
  @Column(name = "transaction_date", nullable = false)
  private LocalDate date;

  @NotBlank
  @Column(nullable = false, length = 2000)
  private String description;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "activity_id", nullable = false)
  private FinancialActivity activity;

  @NotNull
  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "neutral_category_id", nullable = false)
  private NeutralCategory neutralCategory;

  @Column(name = "counterparty_id")
  private Long counterpartyId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "deleted_at")
  private LocalDateTime deletedAt;

  @OneToMany(
      mappedBy = "transaction",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @OrderBy("id ASC")
  @Builder.Default
  private Set<TransactionAttachment> attachments = new LinkedHashSet<>();

  @OneToMany(
      mappedBy = "transaction",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @OrderBy("id ASC")
  @Builder.Default
  private Set<TransactionImportReference> importReferences = new LinkedHashSet<>();

  public void replaceAttachment(
      String storageProvider, String externalId, String fileName, String sha256) {
    if (storageProvider == null && externalId == null && fileName == null && sha256 == null) {
      attachments.clear();
      return;
    }
    TransactionAttachment attachment =
        attachments.stream()
            .findFirst()
            .orElseGet(
                () -> {
                  TransactionAttachment created =
                      TransactionAttachment.builder().transaction(this).build();
                  attachments.add(created);
                  return created;
                });
    attachment.setStorageProvider(storageProvider == null ? "ONEDRIVE" : storageProvider);
    attachment.setExternalId(externalId);
    attachment.setFileName(fileName);
    attachment.setSha256(sha256);
  }

  public void replaceImportReference(String origin, String externalId, String sourceLabel) {
    if (origin == null && externalId == null && sourceLabel == null) {
      importReferences.clear();
      return;
    }
    TransactionImportReference reference =
        importReferences.stream()
            .findFirst()
            .orElseGet(
                () -> {
                  TransactionImportReference created =
                      TransactionImportReference.builder().transaction(this).build();
                  importReferences.add(created);
                  return created;
                });
    reference.setOrigin(origin == null ? "MANUAL" : origin);
    reference.setExternalId(externalId);
    reference.setSourceLabel(sourceLabel);
  }

  public void tombstone(LocalDateTime deletedAt) {
    this.deletedAt = deletedAt;
  }

  public void reactivate() {
    deletedAt = null;
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
  }

  @PreUpdate
  private void updateAuditTimestamp() {
    updatedAt = LocalDateTime.now();
  }
}

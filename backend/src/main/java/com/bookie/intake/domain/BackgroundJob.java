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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "background_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackgroundJob {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "inbox_item_id", nullable = false)
  private InboxItem inboxItem;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private BackgroundJobType type;

  @Column(name = "idempotency_key", nullable = false, length = 500, unique = true)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private BackgroundJobState state;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "execution_id")
  private UUID executionId;

  @Column(name = "execution_attempt_base", nullable = false)
  private int executionAttemptBase;

  @Column(name = "execution_previous_max_attempts")
  private Integer executionPreviousMaxAttempts;

  @Column(name = "execution_started", nullable = false)
  private boolean executionStarted;

  @Column(name = "available_at", nullable = false)
  private LocalDateTime availableAt;

  @Column(name = "lease_owner")
  private String leaseOwner;

  @Column(name = "lease_expires_at")
  private LocalDateTime leaseExpiresAt;

  @Column(name = "last_error", length = 2000)
  private String lastError;

  @Column(name = "terminal_reason", length = 100)
  private String terminalReason;

  @Enumerated(EnumType.STRING)
  @Column(name = "legacy_pending_table", length = 30)
  private LegacyPendingTable legacyPendingTable;

  @Column(name = "legacy_pending_id")
  private Long legacyPendingId;

  @Column(name = "target_year")
  private Integer targetYear;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Version
  @Column(nullable = false)
  private Long version;

  @PrePersist
  private void initializeAuditFields() {
    LocalDateTime now = LocalDateTime.now();
    if (createdAt == null) {
      createdAt = now;
    }
    if (updatedAt == null) {
      updatedAt = now;
    }
    if (availableAt == null) {
      availableAt = now;
    }
    if (state == null) {
      state = BackgroundJobState.AVAILABLE;
    }
    if (maxAttempts == 0) {
      maxAttempts = 11;
    }
  }

  @PreUpdate
  private void updateAuditTimestamp() {
    updatedAt = LocalDateTime.now();
  }
}

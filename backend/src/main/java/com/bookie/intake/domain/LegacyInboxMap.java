package com.bookie.intake.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "legacy_inbox_map")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyInboxMap {

  @EmbeddedId private LegacyPendingKey key;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "inbox_item_id", nullable = false)
  private InboxItem inboxItem;

  @Column(name = "mapped_at", nullable = false)
  private LocalDateTime mappedAt;
}

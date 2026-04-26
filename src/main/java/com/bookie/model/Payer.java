package com.bookie.model;

import com.bookie.util.AccountNumbers;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Payer {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  private PayerType type;

  /** Alternate names or abbreviations (e.g. "PG&E" for "Pacific Gas and Electric Company"). */
  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(name = "payer_aliases", joinColumns = @JoinColumn(name = "payer_id"))
  @Column(name = "alias", nullable = false)
  @Builder.Default
  private List<String> aliases = new ArrayList<>();

  /** Account numbers for this payer (e.g. utility account numbers per property). */
  @ElementCollection
  @CollectionTable(name = "payer_accounts", joinColumns = @JoinColumn(name = "payer_id"))
  @Column(name = "account_number", nullable = false)
  @Builder.Default
  private Set<String> accounts = new HashSet<>();

  @PrePersist
  @PreUpdate
  private void normalizeAccounts() {
    accounts = new HashSet<>(AccountNumbers.normalize(accounts));
  }
}

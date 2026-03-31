package com.bookie.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
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
  private List<String> accounts = new ArrayList<>();
}

package com.bookie.catalog.counterparty.infrastructure;

import com.bookie.catalog.counterparty.domain.CounterpartyType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "counterparties")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class NormalizedCounterpartyRecord {

  @Id private Long id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  private CounterpartyType type;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "counterparty_aliases",
      joinColumns = @JoinColumn(name = "counterparty_id"))
  @Column(name = "alias", nullable = false)
  @Builder.Default
  private List<String> aliases = new ArrayList<>();

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "counterparty_accounts",
      joinColumns = @JoinColumn(name = "counterparty_id"))
  @Column(name = "account_number", nullable = false)
  @Builder.Default
  private Set<String> accounts = new HashSet<>();
}

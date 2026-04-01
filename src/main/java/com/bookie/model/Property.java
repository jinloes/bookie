package com.bookie.model;

import com.bookie.util.AccountNumbers;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "properties")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Property {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String address;

  @Enumerated(EnumType.STRING)
  private PropertyType type;

  private String notes;

  /** Account numbers for this property (e.g. utility account numbers tied to this address). */
  @ElementCollection
  @CollectionTable(name = "property_accounts", joinColumns = @JoinColumn(name = "property_id"))
  @Column(name = "account_number", nullable = false)
  @Builder.Default
  private Set<String> accounts = new HashSet<>();

  @PrePersist
  @PreUpdate
  private void normalizeAccounts() {
    accounts = new HashSet<>(AccountNumbers.normalize(accounts));
  }
}

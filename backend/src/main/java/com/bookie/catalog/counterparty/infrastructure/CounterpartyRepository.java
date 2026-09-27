package com.bookie.catalog.counterparty.infrastructure;

import com.bookie.catalog.counterparty.domain.Counterparty;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CounterpartyRepository extends JpaRepository<Counterparty, Long> {

  List<Counterparty> findAllByNameIgnoreCase(String name);

  @Query(
      "SELECT DISTINCT counterparty FROM Counterparty counterparty JOIN counterparty.aliases alias "
          + "WHERE LOWER(alias) = LOWER(:alias)")
  List<Counterparty> findAllByAliasIgnoreCase(@Param("alias") String alias);

  @Query(
      "SELECT COUNT(counterparty) FROM Counterparty counterparty JOIN counterparty.aliases alias "
          + "WHERE LOWER(alias) = LOWER(:alias) AND counterparty.id <> :id")
  long countOtherAliasOwners(@Param("alias") String alias, @Param("id") Long id);

  /**
   * Single-result alias lookup kept for {@code CatalogRepositoryQueryTest}; production code uses
   * {@link #findAllByAliasIgnoreCase} through {@code JpaCounterpartyStore}, which treats several
   * owners as ambiguous instead of throwing.
   */
  @Query(
      "SELECT counterparty FROM Counterparty counterparty JOIN counterparty.aliases alias "
          + "WHERE LOWER(alias) = LOWER(:alias)")
  Optional<Counterparty> findByAliasIgnoreCase(@Param("alias") String alias);

  @Query(
      "SELECT DISTINCT counterparty FROM Counterparty counterparty "
          + "JOIN counterparty.accounts account WHERE LOWER(account) IN :accounts")
  List<Counterparty> findByAccountIn(@Param("accounts") List<String> accounts);
}

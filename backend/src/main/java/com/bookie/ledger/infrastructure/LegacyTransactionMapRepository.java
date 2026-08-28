package com.bookie.ledger.infrastructure;

import com.bookie.ledger.domain.LegacyTransactionKey;
import com.bookie.ledger.domain.LegacyTransactionMap;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface LegacyTransactionMapRepository
    extends JpaRepository<LegacyTransactionMap, LegacyTransactionKey> {

  @Override
  @EntityGraph(
      attributePaths = {
        "transaction",
        "transaction.activity",
        "transaction.activity.owner",
        "transaction.activity.property",
        "transaction.neutralCategory",
        "transaction.attachments",
        "transaction.importReferences"
      })
  Optional<LegacyTransactionMap> findById(LegacyTransactionKey key);

  @EntityGraph(
      attributePaths = {
        "transaction",
        "transaction.activity",
        "transaction.activity.owner",
        "transaction.activity.property",
        "transaction.neutralCategory",
        "transaction.attachments",
        "transaction.importReferences"
      })
  Optional<LegacyTransactionMap> findByTransactionId(Long transactionId);
}

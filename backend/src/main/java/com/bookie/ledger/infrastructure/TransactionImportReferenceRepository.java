package com.bookie.ledger.infrastructure;

import com.bookie.ledger.domain.TransactionImportReference;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
interface TransactionImportReferenceRepository
    extends JpaRepository<TransactionImportReference, Long> {

  List<TransactionImportReference> findByOriginAndParentExternalIdInAndTransactionDeletedAtIsNull(
      String origin, Collection<String> parentExternalIds);
}

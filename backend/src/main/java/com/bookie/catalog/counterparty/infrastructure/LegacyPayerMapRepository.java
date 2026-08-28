package com.bookie.catalog.counterparty.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface LegacyPayerMapRepository extends JpaRepository<LegacyPayerMapRecord, Long> {

  Optional<LegacyPayerMapRecord> findByCounterpartyId(Long counterpartyId);
}

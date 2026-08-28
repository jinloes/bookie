package com.bookie.catalog.counterparty.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface NormalizedCounterpartyRepository
    extends JpaRepository<NormalizedCounterpartyRecord, Long> {}

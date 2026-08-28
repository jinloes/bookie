package com.bookie.ledger.application;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;

public record ResolvedLedgerReferences(
    FinancialActivity activity, NeutralCategory neutralCategory, Long counterpartyId) {}

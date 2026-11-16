package com.bookie.ledger.application;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;

/** Catalog references a ledger write resolved. */
public record ResolvedTransactionReferences(
    FinancialActivity activity, NeutralCategory neutralCategory, Long counterpartyId) {}

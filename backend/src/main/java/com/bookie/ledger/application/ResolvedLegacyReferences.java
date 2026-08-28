package com.bookie.ledger.application;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;

public record ResolvedLegacyReferences(
    FinancialActivity activity,
    NeutralCategory neutralCategory,
    Long legacyCategoryId,
    Long legacyCounterpartyId) {}

package com.bookie.ledger.application;

import com.bookie.catalog.activity.domain.FinancialActivity;
import com.bookie.catalog.category.domain.NeutralCategory;

/**
 * Catalog references a ledger write resolved. {@code financialCategoryId} is the retained report
 * category the neutral category maps to for the activity, used for classification history.
 */
public record ResolvedTransactionReferences(
    FinancialActivity activity,
    NeutralCategory neutralCategory,
    Long financialCategoryId,
    Long counterpartyId) {}

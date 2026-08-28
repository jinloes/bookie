package com.bookie.catalog.category.application;

import com.bookie.catalog.category.domain.LegacyTaxTreatment;
import com.bookie.model.TransactionDirection;
import lombok.Builder;

/** Immutable category shape exposed by the catalog while legacy rows remain authoritative. */
@Builder
public record LegacyCategoryView(
    Long id,
    String key,
    String label,
    TransactionDirection direction,
    LegacyTaxTreatment taxTreatment,
    String taxLine,
    boolean active,
    boolean system) {}

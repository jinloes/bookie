package com.bookie.catalog.property.application;

import java.util.Optional;

/**
 * Published synchronously inside the deleting transaction before a property and its optional rental
 * activity are removed, so owning modules reassign/detach their references and fail closed if any
 * remain.
 */
public record PropertyDeleting(Long propertyId, Optional<Long> rentalActivityId) {}

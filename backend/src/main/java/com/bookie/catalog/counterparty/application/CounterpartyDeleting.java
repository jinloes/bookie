package com.bookie.catalog.counterparty.application;

/**
 * Published synchronously inside the deleting transaction before a counterparty is removed, so
 * owning modules detach their references and fail closed if any remain.
 */
public record CounterpartyDeleting(Long counterpartyId) {}

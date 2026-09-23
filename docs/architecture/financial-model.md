# Financial Model

[← Architecture index](../../ARCHITECTURE.md)

## Financial Activity and Reporting Model

- `HouseholdMember` owns zero or more financial activities. The system creates a default household
  member during migration so existing installations remain usable.
- Creating a property creates its corresponding Schedule E rental activity. Deleting a property
  first reclassifies finalized and pending records to direction-compatible
  `NEEDS_CLASSIFICATION` categories, removes the rental activity, detaches legacy property
  references, verifies zero remaining references, and only then removes the property. It never
  deletes an income, expense, or pending financial record.
- Transaction APIs accept flat `activityId` and `categoryId` references. For rental activities,
  the backend derives the property; clients do not independently choose an inconsistent property.
- Pending income and expense records carry the same activity/category context as finalized records
  so the review queue is the classification boundary.
- A reporting profile (`SCHEDULE_E`, `SCHEDULE_C`, `W2`, or `NONE`) is assigned to an activity over
  a non-overlapping effective date range. Category placement and report lines are resolved from the
  activity profile plus the neutral category; the old category/treatment columns remain available
  throughout the compatibility window.
- `BOOKIE_REPORT_POLICY_MODE` controls rollout: `NEW` is the default and reads effective
  assignments, `LEGACY` remains available for temporary rollback, and `COMPARE` resolves both
  models and fails closed on any field-level difference while returning legacy payloads. Report
  periods with gaps, overlaps, or multiple profiles fail explicitly rather than guessing.
- `GET /api/reports/cashflow?from=...&to=...` is the authoritative server-side cashflow
  aggregation. Optional `ownerId` and `activityId` filters scope income, expenses, net cashflow,
  and activity-level totals.
- `GET /api/reports/schedule-e?year=...` is the authoritative Schedule E aggregation by rental
  activity and financial category. It supports the same optional owner/activity filters, and
  negative net values are intentionally preserved.
- Reporting application code consumes `LedgerReportQuery` and `ReportPolicyResolver`; it never
  imports income, expense, or unified-ledger repositories. The ledger exposes immutable cashflow
  and Schedule E totals. A compatibility provider wraps the old aggregate queries, while the
  unified provider reads active `FinancialTransaction` rows and resolves each effective neutral
  category mapping.
- `BOOKIE_REPORTING_READ_MODE` independently controls report-query rollout: `UNIFIED` (default)
  reads only the ledger provider, `LEGACY` returns compatibility aggregates for temporary rollback,
  and `COMPARE` runs both providers and returns the legacy result only after grouping and exact
  monetary parity. Comparison ignores ordering and `BigDecimal` scale representation but never
  rounds values.
- The Dashboard and Tax Report consume these report endpoints rather than reconstructing totals
  independently from transaction lists.

## Unified Ledger and Compatibility

- `FinancialTransaction` is the unified ledger aggregate. It stores a positive amount, direction,
  date, description, activity, neutral category, optional counterparty, audit timestamps, optimistic
  version, tombstone state, normalized attachments, and normalized import references. A transaction
  never stores an independent property reference; rental property always derives through its
  activity.
- Flyway V13 copies every legacy income and expense into `financial_transactions`, preserves all
  legacy rows, and records exactly one durable
  `(legacy_table, legacy_id) -> (transaction_id, canonical_hash)` mapping. Migration prechecks reject
  invalid or unmappable rows, duplicate source identities, and duplicate attachment identities
  rather than repairing or merging financial data. Count, direction-total, mapping, attachment, and
  import-reference reconciliation must all pass before the migration completes.
- `transaction_attachments` and `transaction_import_references` retain normalized metadata.
  Non-null import identity is unique by `(origin, external_id)`, and attachment external identity is
  unique by storage provider. Compatibility commands accept only metadata the legacy schema can
  represent, while preserving target-only attachment hashes across later legacy writes.
- Existing income and expense APIs remain compatibility contracts. Their create/update/delete paths
  synchronize the unified ledger in the same database transaction. `/api/v2/transactions` writes
  the rollback-compatible legacy representation first, then synchronizes the exact saved row into
  the ledger. Deletes remove the compatibility row while retaining a tombstoned unified record and
  its mapping.
- `BOOKIE_LEDGER_READ_MODE` controls old-endpoint rollout: `UNIFIED` (default) reconstructs
  old-endpoint payloads from active unified rows, `LEGACY` returns legacy reads for temporary
  rollback, and `COMPARE` compares field-level legacy and unified representations and fails closed
  on drift while returning the legacy payload. Dual writes remain active in every read mode, and
  switching modes never drops either representation.
- The generated frontend client and stable wrappers expose `/api/v2/transactions`, but the existing
  UI remains on compatibility endpoints until the read rollout is complete.

## Counterparty Compatibility

- `CounterpartyCatalog` owns counterparty CRUD plus case-insensitive name/alias and normalized
  account lookup. Roles are inferred from transaction context rather than encoded as separate
  employer/vendor/tenant/customer entity types.
- During compatibility, the `Counterparty` JPA entity still writes the legacy `payers`,
  `payer_aliases`, and `payer_accounts` tables so every existing foreign key and endpoint remains
  valid. The same transaction mirrors each write to normalized `counterparties`,
  `counterparty_aliases`, `counterparty_accounts`, and the one-to-one `legacy_payer_map`.
- Counterparty deletion detaches finalized and pending financial references and verifies they are
  clear before deleting catalog rows. Financial records are retained; only their optional
  counterparty reference is cleared.

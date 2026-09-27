# Financial Model

[← Architecture index](../../ARCHITECTURE.md)

## Financial Activity and Reporting Model

- `HouseholdMember` owns zero or more financial activities. The system creates a default household
  member during migration so existing installations remain usable.
- Creating a property creates its corresponding Schedule E rental activity. Deleting a property
  first reclassifies ledger transactions and inbox proposals to direction-compatible
  `NEEDS_CLASSIFICATION` categories, removes the rental activity, detaches property references,
  verifies zero remaining references, and only then removes the property. It never deletes a
  ledger transaction or inbox item.
- Transaction APIs accept flat `activityId` and `neutralCategoryId` references. For rental
  activities, the backend derives the property; clients do not independently choose an
  inconsistent property.
- Inbox items carry the same activity/category proposal a saved transaction would, so the review
  queue is the classification boundary.
- A reporting profile (`SCHEDULE_E`, `SCHEDULE_C`, `W2`, or `NONE`) is assigned to an activity over
  a non-overlapping effective date range. Category placement and report lines are resolved from the
  activity profile plus the neutral category. Report periods with gaps, overlaps, or multiple
  profiles fail explicitly rather than guessing.
- `GET /api/reports/cashflow?from=...&to=...` is the authoritative server-side cashflow
  aggregation. Optional `ownerId` and `activityId` filters scope income, expenses, net cashflow,
  and activity-level totals.
- `GET /api/reports/schedule-e?year=...` is the authoritative Schedule E aggregation by rental
  activity and financial category. It supports the same optional owner/activity filters, and
  negative net values are intentionally preserved.
- Reporting application code consumes `LedgerReportQuery` and `ReportPolicyResolver`; it never
  imports ledger repositories. The ledger's only `LedgerReportQuery`, `UnifiedLedgerReportQuery`,
  reads active `FinancialTransaction` rows and resolves each effective neutral category mapping
  into immutable cashflow and Schedule E totals.
- The Dashboard and Tax Report consume these report endpoints rather than reconstructing totals
  independently from transaction lists.

## Unified Ledger

- `FinancialTransaction` is the unified ledger aggregate. It stores a positive amount, direction,
  date, description, activity, neutral category, optional counterparty, audit timestamps, optimistic
  version, tombstone state, normalized attachments, and normalized import references. A transaction
  never stores an independent property reference; rental property always derives through its
  activity.
- `financial_transactions` is the only financial record store. Historically, Flyway V13 copied
  every legacy income and expense into it with a durable legacy mapping, V13–V19 kept the legacy
  tables dual-written, and V20 verified exact parity before dropping them (see
  [`data-lifecycle.md`](data-lifecycle.md)).
- `transaction_attachments` and `transaction_import_references` retain normalized metadata.
  Non-null import identity is unique by `(origin, external_id)`. Attachment external identity is
  not unique: one statement file (a monthly Venmo CSV) is attached to every transaction it lists.
- `/api/v2/transactions` is the transaction API: create, update (optimistic `version`; direction is
  immutable) and delete (tombstone) write the ledger row directly and record the confirmed
  classification in the same transaction. The bundled UI uses it for every transaction screen.
- A manual create with origin `RECEIPT` and an attachment publishes the ledger event
  `TransactionRecorded` (EV-3) inside the create transaction. Intake's `ReceiptFilingListener` (a
  synchronous `@EventListener`) schedules a durable `MOVE_RECEIPT` job in that same transaction, so
  the job exists exactly when the transaction commits; the job relay then moves the receipt into its
  tax-year OneDrive folder with the same retries, terminal states and explicit retry as every other
  intake job. Consistency: the receipt may not be filed yet when the save returns, and a rolled-back
  create schedules nothing. The move is idempotent and preserves the OneDrive item ID. This is the
  only cross-module event. An inbox receipt save never publishes it: it records the transaction
  through the non-filing path and the save's own `MOVE_RECEIPT` job moves the file after commit,
  with its checksum guard.

## Inbox API

`/api/v2/inbox` is the review queue over durable `inbox_items`:

- `GET /api/v2/inbox` lists active items (not SAVED or DISMISSED), newest first; `GET /{id}` and
  `GET /{id}/jobs` inspect one item; `POST /jobs/{jobId}/retry` retries a terminal job.
- `POST /{id}/save` saves a READY item as an expense or income
  (`direction, amount, date, description, activityId, neutralCategoryId, counterpartyId?,
  sourceLabel?`). One database transaction creates the ledger row, marks the item SAVED and
  schedules any external-sync job (`MOVE_OUTLOOK`, `MOVE_RECEIPT`); a failure rolls all back. A
  terminal item returns 409.
- `POST /{id}/reparse` requeues a READY or FAILED Outlook email or receipt item (409 while
  processing); `DELETE /{id}` dismisses an item.
- `GET /events` streams `inbox-updated` events (`{id, state, origin, proposedDirection}`); the UI
  also polls while any item is transient, because an SSE event can be missed.
- `POST /imports/venmo` (multipart `file`, optional `counterpartyId`, `activityId`, `propertyId`)
  creates READY Venmo income items for review.

## Counterparties

- `CounterpartyCatalog` owns counterparty CRUD plus case-insensitive name/alias and normalized
  account lookup. Roles are inferred from transaction context rather than encoded as separate
  employer/vendor/tenant/customer entity types.
- The `Counterparty` entity maps `counterparties`, `counterparty_aliases` and
  `counterparty_accounts(counterparty_id)`. V20 renamed these from `payers*` in place, keeping
  native identity generation and every foreign key, so IDs are unchanged. The HTTP API is
  `/api/counterparties` (plus `/types` and `/keywords`).
- Counterparty deletion detaches ledger and inbox references and verifies they are clear before
  deleting catalog rows. Financial records are retained; only their optional counterparty reference
  is cleared.

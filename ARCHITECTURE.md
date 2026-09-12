# Bookie Architecture

A household financial-activity tracking application built with Spring Boot and React. Bookie
supports rental, employment, self-employment, and unclassified activity contexts; it is not a
tax-filing engine.

## Architecture

- **Backend:** Spring Boot 3.5, Java 21, H2 (file-based at `~/.bookie/bookiedb`), JPA/Hibernate, Lombok, springdoc OpenAPI
- **Frontend:** React 19, React Router, Vite — delivered as a standalone **Tauri 2** desktop app (`frontend/src-tauri/`)
- **Desktop:** Tauri 2 (Rust) wraps the React frontend; on startup it spawns the Spring Boot backend if not already running, waits for it to be healthy, then shows the window. In release builds, a supervisor thread also watches for the backend process exiting unexpectedly and auto-restarts it (bounded retries, then a blocking error dialog). A controlled restore restart suppresses that supervisor, asks Spring to close gracefully, waits for the owned process and datasource to exit, then starts its replacement. A single-instance guard focuses the existing window instead of spawning a duplicate backend if the app is launched twice (relevant since it also has autostart + a tray icon).
- **Build:** Gradle manages the backend only. The frontend is built and run via `npm run dev:tauri` / `npm run build:tauri` in the `frontend/` directory
- **AI Agent:** Integrated AI service (`gpt-5-mini` by default) - `AgentService` extracts a proposed income or expense from freeform chat; nothing is saved until the user reviews and explicitly saves it in the UI
- **Email Parsing:** Integrated AI service (`gpt-5-mini` by default) - used by `EmailParserService` for neutral structured extraction from Outlook emails before deterministic activity/category classification
- **Auto-Import Polling:** `AutoImportPollingService` runs on a schedule (default every 30 min, `bookie.auto-import.*`) to discover Outlook emails and OneDrive receipts and create durable parsing jobs, so items appear in the editable review queue without a manual "Parse" click

## Project Structure

```
backend/src/main/java/com/bookie/
  catalog/
    api/                  Shared catalog transport values
    activity/
      api/             Existing activity REST contract and transport DTOs
      application/     ActivityCatalog use cases and property lookup port
      domain/          FinancialActivity, ActivityType, TaxTreatment
      infrastructure/  JPA store and property lifecycle adapter
    category/
      application/     Legacy-to-neutral category catalog
      domain/          Neutral categories and explicit legacy aliases
      infrastructure/  Module-owned neutral category repositories
    classification/
      api/             Legacy history routes preserved at their existing paths
      application/     ClassificationHistory compatibility port
      infrastructure/  Legacy history repositories and adapter
    counterparty/
      api/             Legacy /api/payers transport contract
      application/     CounterpartyCatalog use cases and lifecycle ports
      domain/          Counterparty and CounterpartyType
      infrastructure/  Legacy payer store plus normalized compatibility mirror
    household/
      api/             Existing household-member REST contract and transport DTOs
      application/     HouseholdCatalog use cases and persistence port
      domain/          HouseholdMember
      infrastructure/  JPA store
    property/
      api/             Existing property REST contract and transport DTOs
      application/     PropertyCatalog and lossless lifecycle orchestration
      domain/          Property and PropertyType
      infrastructure/  Property JPA store
    reportpolicy/
      application/     Effective policy resolution and comparison modes
      domain/          Reporting profiles, assignments, and category mappings
      infrastructure/  JPA resolver, assignment writer, and overlap constraint
  ledger/
    api/             Unified `/api/v2/transactions` transport contract
    application/     Transaction commands, report/query ports, invariants, and lifecycle ports
    compatibility/   Same-transaction legacy writes and legacy report/read adapters
    domain/          Unified transaction, normalized metadata, and durable legacy map
    infrastructure/  JPA stores, unified report provider, and catalog persistence adapters
  reporting/
    api/             Existing `/api/reports` transport contract and module-local report DTOs
    application/     Cashflow and Schedule E use cases over ledger/report-policy ports
    domain/          Immutable report results
    infrastructure/  Legacy/unified comparison and rollout selection
  intake/
    api/             Durable inbox/job inspection and explicit retry transport
    application/     Transactional intent, engine callbacks/projections, and persistence ports
    compatibility/   Same-transaction legacy pending synchronization and job dispatch
    domain/          Inbox items, artifacts, background jobs, states, and legacy maps
    infrastructure/  JPA stores, raw H2 JobRunr storage, server-only candidate gate and lifecycle
  integrations/
    outlook/      Microsoft Graph mail and MSAL adapters, immutable message identities
    onedrive/     Typed OneDrive storage port and Graph adapter
    llm/          Provider-neutral LLM gateway plus Copilot and Spring AI adapters
    documents/    Document text extraction port and PDF/OCR adapter
    venmo/        Deterministic Venmo statement input port
    receipts/     Deterministic, checksum-aware receipt file port
  compatibility/
    api/         Shared legacy response mappers and error envelope
    catalog/     Cross-capability catalog/ledger/intake composition adapters
    intake/      Legacy pending synchronization and integration job dispatch
  controller/   Retained legacy REST entry points + SpaFilter
  datalifecycle/
    migration/  Versioned integrity manifests and lossless reconciliation
    restore/    Shadow staging, durable restore journal, activation, rollback
  model/        JPA entities + domain enums
  repository/   Legacy ledger/intake repositories and compatibility port adapters
  service/      Business logic
backend/src/main/resources/
  application.properties
frontend/
  src/          React source
  src-tauri/    Tauri desktop wrapper (Rust)
    src/lib.rs  Backend lifecycle + window management
    tauri.conf.json
  vite.config.js  Builds to frontend/dist (consumed by Tauri)
diagrams/
  erd.drawio          Entity-relationship diagram
  architecture.drawio System architecture diagram
```

## Key Conventions

- All API routes are prefixed with `/api`
- API responses are transport DTOs (controllers do not expose JPA entities directly)
- API errors use a structured JSON envelope: `{ "code": "...", "message": "...", "details": { ... } }`
- OpenAPI docs are exposed at `/v3/api-docs` with Swagger UI at `/swagger-ui/index.html`
- `SpaFilter` (a `OncePerRequestFilter`) forwards non-API, non-file requests to `index.html` — present but unused in normal Tauri mode since the frontend is served by Tauri
- Every finalized or pending transaction belongs to one `FinancialActivity` and one
  database-backed `FinancialCategory`.
- `FinancialActivity` captures the household member, activity type, tax treatment, and optional
  rental property that provide transaction context. A system `NEEDS_CLASSIFICATION` activity
  holds legacy or unresolved transactions.
- `FinancialCategory` retains the legacy IDs, keys, treatment, and tax-line columns required by
  existing rows and clients. `NeutralCategory` separately owns the category label and direction;
  `LegacyCategoryMap` records every explicit old-key alias without deleting or rewriting a legacy
  row. The legacy `ExpenseCategory` enum remains only as a compatibility adapter.
- Rental activities have exactly one property. Non-rental activities cannot reference a property.
- New transaction writes validate that activity, direction, and category are compatible. These
  classifications organize records; they do not calculate or file taxes.
- `PropertyType` enum has a `label` field for display
- `Counterparty` is the domain term for an employer, vendor, tenant, customer, or other party.
  Existing `/api/payers` routes, payer-shaped JSON fields, and the legacy `payers` table remain
  compatibility contracts.
- The frontend fetches activities and compatible categories from the backend instead of
  hardcoding the domain catalog.

## Catalog Module Boundaries

- Household members, financial activities, properties, counterparties, classification history,
  neutral categories, and report policy are catalog slices in the modular monolith. Existing HTTP
  paths, JSON fields, legacy table names, legacy category IDs, and stored enum values are unchanged.
- Module APIs depend on application interfaces, application code depends on domain types and
  persistence/lookup ports, and infrastructure implements those ports. Code outside a catalog
  slice depends on `HouseholdCatalog`, `ActivityCatalog`, `PropertyCatalog`,
  `CounterpartyCatalog`, or `ClassificationHistory`, never a catalog repository.
- Activity depends on household and property domain identities; household and property core do not
  reverse that dependency. Property lifecycle invokes an activity-owned adapter through
  `PropertyActivityLifecycle`. Legacy ledger and intake repositories implement explicit
  reassign/detach/reference-count ports rather than being injected into `PropertyService`.
- Classification history repositories are reachable only through `ClassificationHistory` or the
  property/counterparty cleanup ports. The legacy keyword-history HTTP paths are served by the
  classification adapter without making property or counterparty APIs depend on its infrastructure.
- `ModuleBoundaryTest` uses ArchUnit to reject catalog cycles, outer-layer imports, infrastructure
  leakage, and reversed `api -> application -> domain` dependencies.

## Module Dependency and Compatibility Boundary

- The capability dependency graph is directional: catalog has no capability dependency; ledger may
  use catalog; intake may use ledger and catalog; reporting may use ledger and catalog. Capability
  cycles and cross-module repository access are rejected by `ModuleBoundaryTest`.
- A capability's API and application layers expose module-owned values and ports, not legacy JPA
  entities or repositories. Ledger, intake, and reporting infrastructure is internal to its owning
  capability.
- Module-local `compatibility` packages adapt retained endpoint and persistence shapes. The
  top-level `com.bookie.compatibility` package is the composition boundary for adapters that must
  coordinate more than one capability or translate integration failures into application-owned
  outcomes. Module-local compatibility adapters may use this shared boundary; normal capability
  code cannot import it.
- Root `controller`, `service`, `model`, and `repository` packages remain only where old HTTP or
  storage contracts require them. Compatibility readers and writers preserve those contracts for
  rollback; they are not new application entry points.
- Compatibility code can be retired only in a separately approved change after its legacy
  endpoint/table contract is no longer needed and parity has been proved. Removing legacy tables,
  endpoints, or records is never part of a package-boundary cleanup.

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

## Automated Financial Intake

- Agent chat, Outlook email, OneDrive receipts, and Venmo CSV imports all produce editable proposals
  or pending records; extraction never finalizes a transaction.
- Flyway V14 copies every legacy pending row and proposal field into `inbox_items`, retains every
  legacy row, records a durable legacy map, preserves aliases/receipt metadata as artifacts, and
  creates recoverable parsing and Outlook-ID translation jobs. Duplicate source identities or any
  count/field reconciliation mismatch abort migration instead of merging or dropping intake data.
- The durable lifecycle is `RECEIVED -> QUEUED -> PROCESSING -> READY -> SAVE_PENDING -> SAVED`,
  with explicit `FAILED` and `DISMISSED` transitions. External synchronization has a separate state
  so a completed financial save is never represented as lost merely because Graph or OneDrive is
  unavailable.
- Parsing, Outlook moves, receipt moves, and legacy-ID translation execute through embedded
  JobRunr OSS 8.8.1, one worker with five-second pickup. JobRunr alone owns execution, ten retries
  after the first attempt, and orphan recovery. Retry n is due after 3^n seconds (3, 9, 27, 81,
  243, 729, 2187, 6561, 19683, 59049); failure 11 remains FAILED. No jitter, cap, override or
  separate orphan allowance is applied.
- Saving a reviewed item writes the compatibility financial row, unified ledger row, inbox state,
  and external-sync job in one database transaction. Graph and OneDrive effects begin only after
  that transaction returns and are idempotent or fail visibly; they are never hidden post-commit
  callbacks.
- Existing pending endpoints and tables remain compatibility contracts and are updated in the same
  transaction as the durable inbox. `BOOKIE_INTAKE_READ_MODE` defaults to `UNIFIED` and uses durable
  ordering only after exact snapshot parity; `LEGACY` remains available for temporary rollback, and
  `COMPARE` fails closed on mapping or field drift while returning legacy order.
  `BOOKIE_INTAKE_WORKER_ENABLED` is a global execution gate for scheduled polling, post-save and
  parse kickoffs, and explicit retries. It defaults to `true` after the validated ID-translation
  rollout. `BOOKIE_INTAKE_WORKER_ALLOWED_JOB_TYPES` defaults to `TRANSLATE_OUTLOOK_ID`,
  `PARSE_OUTLOOK`, `PARSE_RECEIPT`, and `MOVE_RECEIPT`; Outlook move jobs require explicit
  allowlisting after their remote effects are approved. `MOVE_RECEIPT` is allowlisted because it
  refuses to act without a durable artifact whose persisted SHA-256 still matches the current remote
  bytes, and its destination folder move is idempotent and preserves the OneDrive item ID.
- `/api/v2/inbox` exposes durable item, external-sync, error, and projected job status, while terminal
  jobs can be explicitly retried. Existing financial `sourceId` values remain unchanged.
- `AutomatedIntakeClassificationService` applies one deterministic classification policy after
  extraction: an explicitly configured activity wins, followed by unique confirmed
  activity-scoped keyword history, followed by deterministic rental-property resolution.
  Unresolved or conflicting evidence stays on `NEEDS_CLASSIFICATION` with an ambiguity warning.
- Outlook watched folders may carry an optional activity. A configured activity includes all dated
  messages from that folder and is copied into the pending record; folders without one retain the
  legacy Rental-category filter. Saving an empty watched-folder selection disables that feed.
- Confirmed keyword history stores activity and category together, preventing a shared counterparty
  or keyword from leaking a classification between employment, self-employment, and rental work.
- Review screens allow the user to correct direction, activity, category, and source-specific
  details. Only the explicit **Save** action calls the normal income or expense persistence API.

### Engine storage, bindings and lifecycle

Flyway V15 adds `execution_id` (indexed nullable UUID), `execution_attempt_base`,
`execution_previous_max_attempts`, and `execution_started` to `background_jobs`, plus the pinned
PUBLIC JobRunr tables, indexes, stats view and counter seed. V1-V14 and historical cells remain
unchanged. One engine UUID binds a singleton or a bounded translation batch; later arrivals cannot
join it. The UUID reference is logical, not a foreign key: vendor retention may remove old records.
The persisted payload is `DurableBackgroundJobWorker.executeV1(String type, JobContext)` with
`JobContext.Null` at enqueue; context supplies the UUID, and locked business bindings supply members.

`relay()` only binds due intent, publishes missing never-started UUIDs, and projects raw engine
state. It is not an executor. Start evidence commits before provider calls; callbacks and outcome
commits validate the current generation. Finished members keep their counters and are skipped by
batch retries. Retryable outcomes commit independently before the worker throws for native retry.
Unexpected dispatcher errors remain business-terminal; infrastructure/persistence errors escape.
Unsupported callback contracts are native nonretryable failures.

The scheduler, relay and restore use the single raw H2 `StorageProvider` bean. Only the
Ready-created server receives `JobRunrIntakeStorageProvider`, behind the engine's thread-safe wrapper.
It filters exactly ENQUEUED list reads, due SCHEDULED reads, and cutoff PROCESSING orphan reads
before native mutations. Ordinary pages, by-ID reads, counts, stats, metadata and persistence remain
raw. The decorator inherits public native claims and optimistic-conflict handling instead of
forwarding to H2's optimized claim override (which bypasses candidate reads). No election filter
replaces or supplements JobRunr's default retry chain.

Startup-immutable disallowed types keep state, due time, version and failure history; unknown
callback contracts still follow native failure handling. Disabled orphans receive neither synthetic
heartbeats nor failures. Re-enabling the same UUID lets native orphan handling apply its remaining
retry policy, including final exhaustion. Healthy workers retain native heartbeats.
`BOOKIE_INTAKE_WORKER_LEASE_SECONDS` is now a deprecated heartbeat-timeout alias:
`max(4, ceil(seconds / 5))` polls, not an application lease. Legacy lease columns remain in backups;
current LEASED projections have null owner/expiry.

Candidate selection reads expanding complete prefixes, retaining the caller's order and cutoff,
from `min(count, max(64, limit))` up to the captured state count. Each prefix is refiltered; overlapping
prefixes are never concatenated. Timestamp ties need no unsupported ID sort or unsafe offset cursor.
Selection terminates at sufficient allowed rows, a short prefix, or the finite ceiling; fresh calls
observe concurrent growth. Unrepresentable counts fail explicitly. Worst-case rows and memory are
O(backlog); an individual scan can exceed the poll interval. This deliberately retains a single
desktop server, not a multi-process queue or pickup-latency guarantee.

Historical attempts are snapshotted at adoption; 2/5 becomes a fresh 11-execution generation reported
against cumulative ceiling 13, not three remaining retries. Already exhausted legacy rows remain
terminal. SCHEDULED projects the engine due time/error, PROCESSING projects LEASED, and final FAILED
projects MAX_ATTEMPTS. Retained disabled orphans can remain LEASED. Started missing executions and
unfinished DELETED/SUCCEEDED anomalies require manual review. Explicit permitted retry clears
binding/start evidence; ensure/read paths never reset a generation or resurrect dismissed work.

Raw storage follows Flyway with `skip-create=true`. PostRestoreValidator reconciles all PUBLIC rows,
including vendor metadata/history, at Started before Ready permits server creation, adoption,
publication or execution. Dashboard and telemetry are off. Server shutdown precedes the
container-owned raw storage close and datasource closure. Stop old executors before cutover and
retain backups; rollback requires disabling execution, and V15 downgrade is unproven. Remote
effects are at-least-once, not exactly-once.

## Integration Boundaries

- Business services depend on typed ports in `com.bookie.integrations`, not Microsoft Graph, MSAL,
  Copilot SDK, Spring AI, PDFBox, or Commons CSV types. Provider implementations and request
  configuration stay under the corresponding integration package. `ModuleBoundaryTest` rejects
  provider-SDK dependencies outside that boundary and cycles between integration slices.
- Microsoft Graph failures use one typed taxonomy: authentication/authorization failures require
  reconnect, rate limits and server failures are retryable, and missing, conflicting, malformed, or
  otherwise ambiguous results require terminal/manual review. Other ports expose provider-neutral
  exceptions or result types. Adapters do not own retries or workflow state.
- Every Outlook message list, pagination, get, move, and ID-translation request asks Graph for
  immutable IDs. Message identities retain both the existing REST ID and the immutable ID. Existing
  API responses and financial `sourceId` values continue to use the legacy ID; no financial record
  is rewritten. V14 stores immutable IDs alongside legacy IDs; the intake worker translates
  existing IDs in batches and leaves missing or ambiguous results as visible manual-review jobs.
  OAuth requests include delegated `User.Read`, which Microsoft Graph requires for
  `/me/translateExchangeIds`, in addition to mail and file access.
- Outlook moves first inspect the current parent folder. Being at the destination is success, and a
  missing source triggers only a bounded destination scan before the adapter returns a typed
  outcome. ID translation is batched and rejects partial, duplicate, erroneous, or ambiguous
  responses.
- Receipt-file writes use root-confined deterministic paths and SHA-256 comparisons. Repeating the
  same content reports already-present; different content at the same path reports a conflict and
  is never overwritten. Durable receipt moves require a matching artifact identity and persisted
  checksum before OneDrive can be mutated; missing evidence becomes manual review.

## Tauri Plugins

Installed: `dialog`, `notification`, `window-state`, `autostart`, `single-instance`, `shell`, `updater`, `process`, `store`. Permissions are declared in `frontend/src-tauri/capabilities/default.json`.

- **`single-instance`** — must be the first plugin registered (see `lib.rs`); focuses the existing window instead of letting a second launch spawn a duplicate backend process on the same port.
- **`shell`** — used via `frontend/src/utils/links.js` (`openExternalUrl`) to reliably open external links (e.g. "Open in OneDrive") in the system browser instead of relying on undocumented webview `target="_blank"` handling.
- **`store`** — used via `frontend/src/utils/persistentStore.js` to mirror `useSessionState` values (page filters) to an on-disk `settings.json` store, so they survive a full app restart, not just navigation within one run. Best-effort: no-ops outside Tauri.
- **`updater` / `process`** — wired into the "Updates" section of Settings (`check()` / `downloadAndInstall()` / `relaunch()`). `tauri.conf.json` ships with a placeholder `plugins.updater` config (`pubkey: ""`, `endpoints: []`) — this is required for the plugin to initialize at all (an empty/missing config fails to deserialize and the app won't start), but it means **auto-updates are not functional out of the box**: `check()` will simply fail gracefully and Settings shows "Update check is not available for this build." To enable real updates before shipping a release:
  1. Generate a signing keypair: `npm run tauri signer generate -- -w ~/.tauri/bookie.key` (from `frontend/`).
  2. Add a `plugins.updater` block to `tauri.conf.json` with `endpoints` (e.g. a `latest.json` published alongside GitHub releases) and the generated public key.
  3. Set `TAURI_SIGNING_PRIVATE_KEY` (and `TAURI_SIGNING_PRIVATE_KEY_PASSWORD` if used) in the build environment, and add `"bundle": { "createUpdaterArtifacts": true }` so `npm run build:tauri` produces signed update bundles.
  4. Publish `latest.json` + the signed artifacts to wherever `endpoints` points.
  Until this is done, "Check for Updates" in Settings will show "Update check is not available for this build" rather than crashing.

Rust unit tests (`frontend/src-tauri/src/lib.rs`, `#[cfg(test)] mod tests`, run via `cargo test --lib` from `frontend/src-tauri/`) guard against startup regressions, notably `tauri_conf_updater_plugin_config_deserializes`, which parses the real `tauri.conf.json` and deserializes `plugins.updater` using `tauri_plugin_updater::Config` — this fails at test time if the required placeholder block is ever removed, instead of only failing when the packaged app is launched.

## Database Migrations

Schema is managed by [Flyway](https://flywaydb.org). `spring.jpa.hibernate.ddl-auto=validate` - Hibernate only checks that the entity model matches the live schema, it never modifies it.

- Versioned SQL scripts live in `backend/src/main/resources/db/migration/` named `V{n}__{description}.sql`
- Each script must be idempotent or irreversible-safe. Released V1-V10 bytes are frozen by
  `db/migration-checksums.sha256`; CI rejects modification, deletion, or rename while allowing a
  new migration. Never bypass a mismatch with `flyway.repair()` or by editing history. Add a new
  V{n+1} migration; checksum repair requires a separately reviewed, schema-verified recovery.
- Migration scripts currently use H2-specific syntax (e.g. `ADD CONSTRAINT IF NOT EXISTS`, `ENUM(...)` column types). If this project ever migrates to a different RDBMS those need translation.
- Dev databases that predate Flyway are baselined at V1 via `spring.flyway.baseline-on-migrate=true` and `spring.flyway.baseline-version=1`, so they skip V1 and pick up at V2+. Fresh installs run V1 to create the full schema.
- Dependency is `org.flywaydb:flyway-core` only. H2 support is built into the core - there is no `flyway-database-h2` artifact on Maven Central.
- V7 introduces household members, financial activities, transaction activity ownership, a
  default household member, per-property rental activities, and the `NEEDS_CLASSIFICATION`
  fallback.
- V8 introduces the financial category catalog, seeds W-2/Schedule C/Schedule E/unclassified
  categories, and backfills non-null category ownership on finalized and pending transactions.
- V9 adds Outlook folder activity context, pending-item ambiguity metadata, and activity-scoped
  keyword classification history for automated intake.
- V10 backfills stable system keys for household members.
- V11 adds neutral categories, an explicit legacy category map, reporting profiles, effective-dated
  activity profile assignments, and category/report mappings. It copies every category and
  assignment into additive tables, rejects unknown system keys and overlapping ranges, and leaves
  all legacy category and financial records unchanged.
- V12 adds normalized counterparty, alias, account, and one-to-one legacy-map tables. It copies
  every legacy payer row and collection value with the same ID, verifies count and value parity,
  and leaves properties, payer tables, classification history, and all financial records unchanged.
- V13 adds the unified ledger, normalized transaction attachments/import references, and durable
  legacy mappings. It copies and reconciles every income and expense without dropping legacy data;
  legacy and unified writes remain transactionally synchronized throughout the rollback window.

`backend/MIGRATIONS.md` documents the immutable-migration release check and the versioned integrity
manifest. The verifier captures canonical row hashes without raw values, table/reference counts,
grouped financial totals, and uniqueness/orphan checks. Reconciliation fails closed on any missing
row, changed preserved-column value, changed total/reference, duplicate, or orphan; additive tables
and columns remain allowed.

Do not write `ApplicationRunner` or `CommandLineRunner` beans to fix up the schema. That pattern is fragile, hard to test, and accumulates dead code once migrations complete.

## Backup Restore Safety

Restore never executes backup SQL against the active datasource:

1. The backend bounds the download, records its SHA-256, checks free space, and restores it into a
   same-filesystem shadow H2 database.
2. Flyway and the migration integrity verifier validate the shadow. A checksummed, fsynced journal
   records live, shadow, rollback, manifest, and source hashes; the API returns
   `VALIDATED` with `restartRequired=true`.
3. A packaged Tauri app performs a controlled backend restart. Browser and development modes leave
   the validated candidate staged and explicitly require the operator to stop and restart the
   backend.
4. Before Spring creates the datasource, bootstrap atomically rotates live → retained rollback and
   shadow → live. Journal states make either interrupted rename recoverable.
5. After startup, the backend reconciles the activated database with the staged manifest and reports
   `POST_START_VALIDATED`. On failure it records `ROLLBACK_REQUIRED`; the supervisor's next restart
   atomically reactivates the retained live file and preserves the failed candidate.

The previous live database, restore journals, integrity manifests, and failed candidates are not
deleted automatically. Restore status is available at `GET /api/backup/restore/status`.

## Diagrams

Diagrams live in `diagrams/` as draw.io files (`.drawio`), compatible with the diagrams.net IntelliJ plugin and Lucidchart import.

- After any change to a JPA entity in `backend/src/main/java/com/bookie/model/` that adds, removes, or renames a table, column, or foreign key, update `diagrams/erd.drawio` before considering the task complete
- After any change that adds, removes, or renames a controller or service in `backend/src/main/java/com/bookie/`, or adds/removes an external integration, update `diagrams/architecture.drawio` before considering the task complete

## Environment Variables

| Variable | Description |
| --- | --- |
| `AI_CLI_PATH` | Optional absolute path to the AI service CLI executable |
| `AI_USE_LOGGED_IN_USER` | Use local logged-in auth for the AI service (default: `true`) |
| `AI_AUTH_TOKEN` | Optional token auth for the AI service when not using logged-in auth |
| `AI_MODEL_AGENT` | Model for `/api/agent/transaction` proposals (default: `gpt-5-mini`) |
| `AI_MODEL_CHAT` | Model for email parsing (default: `gpt-5-mini`) |
| `AI_MODEL_VISION` | Model for receipt OCR (default: `gpt-5-mini`) |
| `AI_TOOLS_EMAIL_PARSER_ENABLED` | Enables Copilot tool-calling during email parsing (default: `false`) |
| `AI_TOOLS_TRACE_EVENTS` | Enables tool execution event tracing for diagnostics/tests (default: `false`) |
| `AI_REQUEST_TIMEOUT_MS` | Request timeout in milliseconds for AI service calls (default: `180000`) |
| `BOOKIE_REPORT_POLICY_MODE` | Report-policy source: `NEW` (default), `LEGACY`, or `COMPARE` |
| `BOOKIE_LEDGER_READ_MODE` | Legacy-endpoint ledger read mode: `UNIFIED` (default), `LEGACY`, or `COMPARE` |
| `BOOKIE_REPORTING_READ_MODE` | Report query provider: `UNIFIED` (default), `LEGACY`, or `COMPARE` |
| `BOOKIE_INTAKE_READ_MODE` | Pending-item read mode: `UNIFIED` (default), `LEGACY`, or `COMPARE` |
| `BOOKIE_INTAKE_WORKER_ENABLED` | Global gate for every scheduled or direct durable-job execution path (default: `true`) |
| `BOOKIE_INTAKE_WORKER_ALLOWED_JOB_TYPES` | Comma-separated claim allowlist (default: `TRANSLATE_OUTLOOK_ID,PARSE_OUTLOOK,PARSE_RECEIPT,MOVE_RECEIPT`) |
| `OUTLOOK_CLIENT_ID` | Azure app client ID for Outlook integration |
| `OUTLOOK_CLIENT_SECRET` | Azure app client secret for Outlook integration |
| `OUTLOOK_TENANT_ID` | Azure tenant ID for Outlook integration |
| `OUTLOOK_REDIRECT_URI` | OAuth2 redirect URI (default: `http://localhost:48763/api/outlook/callback`) |

See `.env.example` for a template.

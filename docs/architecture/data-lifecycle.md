# Data Lifecycle

[← Architecture index](../../ARCHITECTURE.md)

## Database Migrations

Schema is managed by [Flyway](https://flywaydb.org). `spring.jpa.hibernate.ddl-auto=validate` - Hibernate only checks that the entity model matches the live schema, it never modifies it.

- Versioned SQL scripts live in `backend/src/main/resources/db/migration/` named `V{n}__{description}.sql`
- Each script must be idempotent or irreversible-safe. Every migration listed in
  `db/migration-checksums.sha256` (currently V1–V19) is frozen; new migrations are frozen by a later
  release procedure. CI rejects modification, deletion, or rename of a frozen migration while
  allowing a new one. Never bypass a mismatch with `flyway.repair()` or by editing history. Add a new
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
  legacy mappings. It copied and reconciled every income and expense; V13–V19 kept the legacy
  tables dual-written.
- V14–V19 add the durable inbox, identity evidence, JobRunr storage, Outlook attachment intake, and
  the ledger parent external ID.
- V20 irreversibly retires the legacy flows. It first runs every precheck, before any DDL (H2 DDL
  auto-commits): each `expenses`/`incomes` row maps to an equal active ledger transaction, the
  ledger quarantine is empty, each `pending_expenses`/`pending_incomes` row maps to an equal active
  inbox item (including alias artifacts and identity evidence), and `payers*` exactly equal the
  `counterparties*` mirror with an identity `legacy_payer_map`. It then drops
  `pending_expense_identity_evidence`, `pending_income_identity_evidence`,
  `pending_expense_aliases`, `pending_expenses`, `pending_incomes`, `legacy_inbox_map`,
  `legacy_transaction_map`, `ledger_migration_quarantine`, `expenses`, `incomes`,
  `legacy_payer_map` and the `counterparties*` mirror, renames `payers`, `payer_aliases` and
  `payer_accounts` to `counterparties`, `counterparty_aliases` and `counterparty_accounts`
  (column `payer_id` → `counterparty_id`), and re-points the ledger counterparty foreign key. A
  failed precheck leaves the V19 data intact; recover by restoring the retained backup, never with
  `flyway repair`. Running an older binary on a V20 database is unsupported.
- V21 added `EVENT_PUBLICATION`, the Spring Modulith JDBC event publication registry. V22 replaces
  it with durable jobs: `background_jobs.inbox_item_id` becomes nullable, a `source_external_id`
  column names the OneDrive item of a manually entered receipt's `MOVE_RECEIPT` job (a check
  constraint requires exactly one of the two), each unfinished receipt-filing publication becomes an
  equivalent `MOVE_RECEIPT` job, and the registry is dropped. A precheck, run before any DDL, fails
  the migration if an unfinished receipt filing cannot be read.
- V23 extends each activity's earliest reporting profile assignment back to `0001-01-01`, the start
  V11 seeded. Activities created later had a first assignment starting on their creation date, so
  Schedule E reports for a year that began earlier failed as "reporting profile period is
  missing". An activity's first assignment now always starts at `0001-01-01`; a later
  tax-treatment change still closes it and opens a new range from that day. A postcheck fails the
  migration unless every activity with assignments covers the start of history.
- V24 drops the `uk_transaction_attachments_external` unique key and replaces it with a plain
  index. One statement file (a monthly Venmo CSV) is the attachment for every transaction it lists,
  so saving the second row from the same CSV failed with a data integrity violation.

`backend/MIGRATIONS.md` documents the immutable-migration release check and the versioned integrity
manifest. The verifier captures canonical row hashes without raw values, table/reference counts,
grouped financial totals, and uniqueness/orphan checks. Reconciliation fails closed on any missing
row, changed preserved-column value, changed total/reference, duplicate, or orphan; additive tables
and columns remain allowed. A column a migration deliberately rewrites is skipped only when the
upgrade crosses that migration: `ACTIVITY_REPORTING_PROFILE_ASSIGNMENTS.EFFECTIVE_FROM` for V23,
whose postcheck guards the result. Every other column of that table is still compared.

Restoring a backup from any released schema (V1–V21) upgrades it through V24. The verifier knows a
fixed set of tables V20 retires: those may disappear, every other pre-migration table must survive.
`EVENT_PUBLICATION` (present only in V21 backups) held transient delivery state, so like
`FLYWAY_SCHEMA_HISTORY` it is excluded from restore reconciliation. A V21 backup holding an
unfinished receipt filing gains a `MOVE_RECEIPT` job during V22, which changes the
`BACKGROUND_JOBS` count, so its restore fails closed.
`PAYERS`, `PAYER_ALIASES` and `PAYER_ACCOUNTS` must match the row counts of their renamed
`COUNTERPARTIES*` successors, and the pre-migration income plus expense totals per direction and
year must equal the active `FINANCIAL_TRANSACTIONS` totals afterwards. A drifted backup fails V20's
prechecks inside the shadow database, so it is rejected before activation and the live database is
untouched.

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

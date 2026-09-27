# Migration Safety

Released Flyway migrations are immutable. Every migration listed in
`src/main/resources/db/migration-checksums.sha256` (currently V1–V19) is frozen; new migrations are
frozen by a later release procedure. CI runs:

```bash
python3 scripts/verify_released_migrations.py
python3 -m unittest scripts/test_verify_released_migrations.py
./gradlew test spotlessCheck
```

The CI invocation also supplies the pull request or push base SHA. Every migration present in that
base tree is immutable, so the guard rejects its modification, deletion, or rename even if somebody
changes the checksum manifest in the same branch. A migration first introduced after the base is
allowed; if it is added to the checksum manifest, its current bytes must match that checksum and it
becomes base-frozen after merge.

## Adding a migration

1. Leave every released migration byte-identical.
2. Add the next unused `V{n}__description.sql` file.
3. Exercise upgrades from every materially different historical schema in a synthetic fixture.
4. Capture a pre-migration `MigrationIntegrityManifest`.
5. Run the migration against a copy or shadow database.
6. Capture the post-migration manifest and call `MigrationIntegrityVerifier.reconcile`.
7. Stop on any count, aggregate, reference, canonical-row-hash, uniqueness, or orphan mismatch.
8. Freeze the new migration only as part of a later release procedure.

Never repair a checksum merely to bypass Flyway validation. A checksum recovery requires a
separate, schema-verified recovery procedure.

The dual-write compatibility migrations were:

- V11: neutral categories and report policy;
- V12: normalized counterparties plus a one-to-one legacy payer map;
- V13: unified financial transactions, normalized attachment/import metadata, and durable
  income/expense mappings; and
- V14: durable inbox items, artifacts, legacy pending mappings, and leased background jobs;
- V15: transaction identity resolution state and evidence snapshots; and
- V16: JobRunr OSS tables and durable execution bindings.

V12 copies payer, alias, and account values exactly. V13 copies every legacy income/expense and
reconciles counts, totals, mappings, attachments, and import identities. V14 copies every pending
income/expense field, alias, and receipt identity, rejects duplicate source identities, and checks
exact legacy-to-inbox parity before completing. None of V11–V19 modifies or deletes a legacy
financial or pending row.

V20 (`V20__retire_legacy_flows.sql`) is the irreversible cutover. All prechecks run before any DDL
and prove that every legacy expense, income, pending item, alias, identity-evidence row and payer
row is exactly represented in the ledger, inbox and counterparty mirror; only then are the legacy
tables dropped and `payers*` renamed to `counterparties*`.

V21 (`V21__event_publication_registry.sql`) creates `EVENT_PUBLICATION`, the Spring Modulith JDBC
event publication registry, verbatim from the Modulith 1.4 H2 schema (without `IF NOT EXISTS`).
Flyway owns it: `spring.modulith.events.jdbc.schema-initialization.enabled=false`. Completed
publications are deleted (`completion-mode=DELETE`) and outstanding ones are not republished on
restart; `EventPublicationResubmitter` retries incomplete ones instead.

V20 and V21 are above the highest frozen version, so they need no manifest entry until a later
release procedure freezes them.

## Integrity manifests

`MigrationIntegrityVerifier` reads through JDBC and emits a versioned, SHA-256-checksummed
manifest. It contains:

- application table counts;
- direction/year/activity/category row counts and `BigDecimal` totals;
- import and attachment reference counts;
- uniqueness and foreign-key orphan checks; and
- canonical per-row and per-column SHA-256 hashes, keyed by table identity metadata.

The manifest contains hashes rather than raw row values. It is deterministic for an unchanged
database and can be written as machine-readable JSON with
`MigrationIntegrityManifestCodec`. Reconciliation is fail-closed: additive tables are allowed,
but every table, row, and pre-existing column from the pre-migration manifest must remain
byte-semantically equivalent, except the fixed set of tables V20 retires. Those may disappear;
`FLYWAY_SCHEMA_HISTORY` and the transient `EVENT_PUBLICATION` registry are not compared at all;
the renamed `PAYERS*` tables must match their `COUNTERPARTIES*` successor counts, and legacy income
plus expense totals per direction and year must equal the active ledger totals afterwards.
New tables and columns are allowed; reconciliation projects post-migration rows onto the
pre-migration columns so additive schema changes cannot hide a changed legacy value. A migration
that deliberately rewrites a column registers it in `MigrationIntegrityVerifier.REWRITTEN_COLUMNS`
under its version; that column alone is skipped, and only for upgrades crossing that version, so the
migration must carry its own postcheck. V23 registers
`ACTIVITY_REPORTING_PROFILE_ASSIGNMENTS.EFFECTIVE_FROM`.

`assertFinancialParity` compares legacy and target ledger aggregates without exposing financial row
contents.

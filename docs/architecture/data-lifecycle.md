# Data Lifecycle

[← Architecture index](../../ARCHITECTURE.md)

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

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

## Database Vendors and Portable Backups

`BOOKIE_DB_VENDOR` selects `h2` (default) or `sqlserver` (Azure SQL); any other value fails
startup. `DatabaseVendorConfiguration` activates the `sqlserver` profile before config files load,
so the variable must be set in the environment. That profile applies Flyway from
`db/migration-sqlserver` (its `V24__sqlserver_baseline.sql` equals H2 V24; `baseline-on-migrate`
is off, so it needs an empty database), an idle-releasing Hikari pool (`minimum-idle=0`, ten-second
idle timeout, two-minute connection timeout so a paused database can resume), on-demand intake and
startup-only auto-import. `DatabaseConnectionPolicy` requires `encrypt=true`, rejects
`trustServerCertificate=true` and pins `hostNameInCertificate=*.database.windows.net`. The password
comes only from the OS secret store (`util.SecretStore`): Keychain service `com.bookie.sqlserver`,
account = `BOOKIE_DB_USERNAME` on macOS, or the Credential Manager generic credential
`com.bookie.sqlserver:<BOOKIE_DB_USERNAME>` (UTF-16LE secret) on Windows.

A **portable archive** is a versioned, vendor-neutral ZIP of every application and JobRunr table,
read back and checked after export. It is encrypted with AES-256-GCM (`.bookiebak`) under a key in
the OS secret store, service `com.bookie.backup-key`, account `default` (base64 of 32 bytes; on
Windows the Credential Manager target `com.bookie.backup-key:default`). If no key is stored, the
first backup generates one, so install the shared key before the first backup on a new machine. Backups require the
key to be confirmed: the Backups page shows it once, and confirming writes `backup-key-confirmed` in
the data directory. A missing or different key fails with "backup key missing/mismatched".

OneDrive layout under `bookie/backups`:

| Location | Contents | Pruning |
| --- | --- | --- |
| root | H2 nightly `.sql` (plaintext); `sqlserver` startup/daily `bookie-*.bookiebak`; restore safety copies `bookie-safety-*.bookiebak` | newest 30 `.bookiebak` kept; `.sql` never pruned |
| `pre-migration/` | encrypted portable archive and encrypted `.sql` script | never |
| `rollback/` | encrypted portable archive | never |

`POST /api/backups/pre-migration` (H2 only, otherwise 409) and `POST /api/backups/rollback`
(`sqlserver` only) **freeze writes**, drain intake, and upload with a SHA-256 read-back check. The
pre-migration backup also keeps plaintext copies plus an H2 `BACKUP` zip in
`<data dir>/pre-migration/<timestamp>/`. While frozen, every non-GET request except the backup and
restore endpoints returns 423. The freeze is a `<data dir>` flag file, so it survives restarts and a
vendor switch, and ends when a portable restore commits or `DELETE /api/backups/freeze` lifts it.

Restoring a `.bookiebak` works in either vendor and needs no restart: Bookie decrypts and validates
the archive, freezes and drains, uploads a `bookie-safety-*` backup, then replaces all rows in one
transaction (the database is unchanged on failure). Encrypted `.sql` copies are for manual recovery
only, and legacy `.sql` backups restore only into H2 through the shadow restore above.
`GET /api/backups/status` reports the vendor, freeze and key confirmation.

## Azure SQL Migration Runbook

Do every step in order; do not skip the verified backup. Commands below are run by you, not Bookie.

**1. Provision Azure SQL.**
- Create an Azure SQL Database with the **free offer** (General Purpose serverless), behaviour
  "auto-pause the database until next month" when the free allowance runs out, and collation
  `Latin1_General_100_CS_AS_SC` (case-sensitive like H2, and the collation the SQL Server tests
  run with). Keep the admin login in a password manager; Bookie never uses it.
- Budget the free allowance (100,000 vCore-seconds a month). With that behaviour Azure only allows
  the default 60-minute auto-pause delay, so every wake keeps the database up for at least an hour
  at 0.5 vCore, about 1,800 vCore-seconds, or about 55 wakes a month. Each app session costs one
  wake (its startup backup shares it), and leaving the app open adds one daily-backup wake a day.
  Close Bookie when you are done; once the allowance is used, the database stays paused until the
  next month.
- Server networking: public access only for **selected networks**, one firewall rule for your
  current home IPv4 address, **Allow Azure services and resources** off, minimum TLS **1.2**.
- In the Bookie database, as the admin, create the app's contained user with only these roles:

  ```sql
  ALTER DATABASE CURRENT SET ALLOW_SNAPSHOT_ISOLATION ON;
  CREATE USER bookie_app WITH PASSWORD = '<long generated password>';
  ALTER ROLE db_datareader ADD MEMBER bookie_app;
  ALTER ROLE db_datawriter ADD MEMBER bookie_app;
  ALTER ROLE db_ddladmin ADD MEMBER bookie_app;
  ```

- Store that password in Keychain (`-w` last prompts, keeping it out of shell history):
  `security add-generic-password -s com.bookie.sqlserver -a bookie_app -w`.

**2. Save the backup key (on H2).** Open **Backups**, choose **Show key**, store it in your
password manager, then confirm. Outlook must be connected.

**3. Take the pre-migration backup (on H2).** Choose **Create pre-migration backup**. Writes stay
frozen from here on. Check that `bookie/backups/pre-migration/` holds the `.zip.bookiebak` and
`.sql.bookiebak` files and that `~/.bookie/pre-migration/<timestamp>/` holds the local copies.

**4. Switch vendor.** Quit Bookie, then set the variables in the environment that launches it:

```bash
export BOOKIE_DB_VENDOR=sqlserver
export BOOKIE_DB_URL='jdbc:sqlserver://<server>.database.windows.net:1433;database=<db>;encrypt=true;loginTimeout=120'
export BOOKIE_DB_USERNAME=bookie_app
```

Start Bookie. Flyway creates the schema in the empty database; writes are still frozen.

**5. Reconnect Outlook.** Outlook tokens live in the database, so the new one has none. Connect
Outlook (allowed while frozen) so Bookie can read OneDrive.

**6. Restore.** On **Backups**, restore the pre-migration `.zip.bookiebak`. The commit lifts the
freeze. Check transaction counts and totals, inbox items and settings against H2 before relying on
Azure. The H2 file and local copies stay untouched in `~/.bookie`.

**7. Clean up.** Once the migration is verified, delete the old plaintext `bookie-*.sql` dailies
from `bookie/backups` (keep the `pre-migration/` folder).

**Rollback to H2.** On **Backups** choose **Prepare rollback** (freezes Azure writes and uploads to
`rollback/`). Quit, unset `BOOKIE_DB_VENDOR`, `BOOKIE_DB_URL` and `BOOKIE_DB_USERNAME`, start on H2,
lift the stale freeze only if you need writes before restoring, then restore the rollback archive.

**Home IP changed.** Bookie fails to connect. Replace the firewall rule's address with the new IP
in the Azure portal; never widen it to a range or enable Azure services access.

**New Mac.** Import the backup key with
`security add-generic-password -s com.bookie.backup-key -a default -w`, store the database password
as in step 1, then confirm the key on **Backups** (the confirmation lives in the data directory).

# Bookie

A rental income and expense tracking app with a Spring Boot backend and a React + Tauri desktop UI.

## Contributor Docs

- `AGENTS.md` for coding-agent instructions, style rules, and testing requirements
- `ARCHITECTURE.md` for the architecture overview, conventions, and environment variables, with
  detailed topic docs under `docs/architecture/`

## Repo Layout

```
backend/              Spring Boot project (Gradle)
  src/main/java/
  src/main/resources/
  src/test/java/
frontend/             React + Vite + Tauri desktop app
  src/
  src-tauri/
diagrams/             Mermaid architecture, ERD, and major-flow sequences
docs/architecture/    Topic docs indexed from ARCHITECTURE.md
```

## Tech Stack

- **Backend:** Spring Boot 3.5, Java 21, H2, JPA/Hibernate, Flyway
- **Frontend:** React 19, React Router, Vite
- **Desktop delivery:** Tauri 2
- **AI integrations:** Copilot/Spring AI for assistant, email parsing, and OCR flows

## Prerequisites

- Java 21+
- Node.js 20+
- Rust toolchain (required for Tauri)

## Configuration

Copy `.env.example` and fill values as needed:

```bash
cp .env.example .env
```

For stable Outlook token persistence in local dev, use a fixed backend data directory so every
restart uses the same H2 file:

```bash
export BOOKIE_DATA_DIR="$HOME/.bookie-dev"
```

Optional: set `BOOKIE_TOKEN_ENCRYPTION_KEY` (base64 32-byte key). If unset on macOS, Bookie
stores the token-encryption key in Keychain automatically.

Reconnect Outlook after an upgrade adds Microsoft Graph permissions so the refreshed consent
includes them. Durable Outlook ID translation requires delegated `User.Read` in addition to the
mail and file scopes.

The unified ledger (`financial_transactions`) and durable inbox (`inbox_items`) are the only
financial read and write model; Flyway V20 retired the legacy expense, income and pending tables.
Flyway V22 lets a durable `MOVE_RECEIPT` job file a manually entered receipt without an inbox item,
and drops the short-lived Spring Modulith event registry (`EVENT_PUBLICATION`, V21) it replaces.
The intake worker is configured with:

| Variable | Default | Notes |
| --- | --- | --- |
| `BOOKIE_INTAKE_WORKER_ENABLED` | `true` | Set to `false` and restart to prevent engine startup |
| `BOOKIE_INTAKE_WORKER_ALLOWED_JOB_TYPES` | `TRANSLATE_OUTLOOK_ID,PARSE_OUTLOOK,PARSE_RECEIPT,MOVE_OUTLOOK,MOVE_RECEIPT` | Startup-immutable allowlist; empty disables all intake types |
| `BOOKIE_INTAKE_WORKER_INITIAL_DELAY_MS` | `5000` | Periodic publication begins this long after Ready; post-Ready hints may publish sooner |
| `BOOKIE_INTAKE_WORKER_POLL_INTERVAL_MS` | `5000` | Intent publication and business-state projection interval, not provider execution |
| `BOOKIE_INTAKE_WORKER_MAX_JOBS_PER_POLL` | `10` | Maximum newly bound business jobs per publication |
| `BOOKIE_INTAKE_WORKER_LEASE_SECONDS` | `300` | Deprecated heartbeat-timeout alias; rounds up to five-second polls, minimum four polls |

The intake worker executes only allowlisted job types. Translation, parsing, and OneDrive receipt
moves are enabled; Outlook move jobs remain blocked until their remote effects are separately
approved. Receipt moves verify the persisted SHA-256 checksum against the current remote
bytes before moving and are idempotent, so a repeated or replayed job cannot move the wrong file.
See `docs/architecture/financial-model.md` and `docs/architecture/automated-intake.md` for the
ledger and intake contracts.

Intake execution uses embedded **JobRunr OSS 8.8.1**, one worker and a five-second engine poll,
sharing the application's H2 database. Flyway V16 owns the vendor schema; dashboard and anonymous
usage reporting are disabled. Do not override JobRunr retry counts, backoff seed, worker count,
table prefix, or schema creation. Configuration rejects contradictory settings.

JobRunr owns retries and orphan recovery: ten retries after the initial execution, with delays of
**3, 9, 27, 81, 243, 729, 2187, 6561, 19683, and 59049 seconds** (3^n). There is no jitter,
cap, or separate crash-recovery allowance. These are due times, not punctual execution guarantees.
Business-terminal and manual-review outcomes are not automatically retried.

Disallowed work retains its actual ENQUEUED, SCHEDULED or orphan PROCESSING state and history.
It neither calls providers nor consumes retries until a later permitted startup. A retained orphan
may still appear in progress; re-enabling resumes native recovery, including final exhaustion.
The server filters candidate reads, while publication, inspection and restore see raw storage.
To avoid hiding allowed work behind disabled jobs with tied timestamps, selection expands complete
prefixes to a finite backlog ceiling. Worst-case reads and memory are O(backlog), and a large
disabled backlog can delay polls; this is a single-process desktop design, not a latency SLA.

Save still commits the financial record and durable intent together. Publication repairs lost hints
and missing never-started deliveries using the same execution UUID. Missing, deleted or succeeded
engine records with unfinished started work require manual review, never blind replay. Remote
effects remain at-least-once, protected by existing identity, checksum and destination-year guards.
Before upgrading, stop the old executor and retain a backup. For rollback, stop the current backend
and disable its worker; do not run old and new executors together. Downgrading V16 to an old binary
is unproven. Legacy lease columns and historical attempt/budget values remain preserved for backups.

Employers, vendors, tenants, and customers are **counterparties**, served at
`/api/counterparties` (types and keyword history under `/api/counterparties/types` and
`/api/counterparties/keywords`). V20 renamed the `payers*` tables to `counterparties*` in place,
so counterparty IDs are unchanged.

Optional backend local overrides:

```bash
touch backend/.env.local.properties
```

### Database: H2 or Azure SQL

Bookie stores data in a local H2 file by default. It can instead use an Azure SQL Database (the
free serverless offer is enough for a single user):

| Variable | Default | Notes |
| --- | --- | --- |
| `BOOKIE_DB_VENDOR` | `h2` | `h2` or `sqlserver`; any other value fails startup. Must be a real environment variable |
| `BOOKIE_DB_URL` | _(none)_ | `sqlserver` only; must contain `encrypt=true` and must not trust the server certificate |
| `BOOKIE_DB_USERNAME` | _(none)_ | `sqlserver` only; a contained database user |

The SQL Server password is read only from the macOS Keychain
(`security add-generic-password -s com.bookie.sqlserver -a <username> -w`), never from the
environment. In `sqlserver` mode intake runs on demand and auto-import runs at startup or from the
Inbox's **Check for new items** button, so the database can pause while the app is idle. Follow the
migration runbook in `docs/architecture/data-lifecycle.md` to move existing data without loss.

Where to set the variables:

- **Terminal** (`npm run dev:tauri`, `./gradlew bootRun`): the backend inherits your shell
  environment, so export them in `~/.zshrc` or the launching shell. If `BOOKIE_DB_VENDOR` is
  missing, Bookie silently starts on the local H2 file instead.
- **IntelliJ**: add them to the `Bookie Backend` run configuration's environment variables, which
  the `Bookie` compound also uses. Enter the URL as one `BOOKIE_DB_URL` value without quotes;
  pasting a whole `KEY=value;...` string splits the JDBC URL at its semicolons into separate,
  broken variables. Keep these edits local: the `.run/` files are committed, so don't commit your
  server hostname with them.

Backups go to OneDrive under `bookie/backups`. On H2 the nightly backup is an H2 `.sql` script as
before. On `sqlserver`, Bookie writes an AES-256-GCM encrypted portable archive (`.bookiebak`,
restorable into either vendor) at startup and once a day while running, keeping the newest 30. The
backup key lives in the macOS Keychain; save it from the **Backups** page before relying on the
encrypted backups, because they cannot be restored without it.

## Running

### Backend only

```bash
cd backend
./gradlew bootRun
```

Backend serves APIs at `http://localhost:48763`.

### Frontend (browser dev mode)

```bash
cd frontend
npm install
npm run dev
```

Vite runs at `http://localhost:5173` and proxies `/api` to `http://localhost:48763`.

### Desktop app (Tauri)

```bash
cd frontend
npm install
npm run dev:tauri
```

The Tauri wrapper starts the backend if needed, waits for readiness, then opens the app window.

In IntelliJ, the `Bookie` compound run configuration starts `Bookie Backend` (built and
debuggable from the IDE) alongside `Tauri Dev`, which runs `npm run dev:tauri:ide`. That script
applies `src-tauri/tauri.ide.conf.json` so Tauri starts only Vite and waits for the IDE's backend,
instead of launching a second backend via `gradlew bootRun` that would contend for the H2 file lock
and `build/classes`.

### Restoring a backup

The Backups page restores into an isolated shadow database first. It validates the backup with
Flyway and lossless integrity checks, then the packaged Tauri app restarts its managed backend to
atomically activate the candidate while retaining the previous database for rollback. Browser and
development modes cannot own the backend process; when prompted, stop the backend fully and start it
again. Never copy a backup over the live H2 file while Bookie is running.

Encrypted portable archives (`.bookiebak`) restore in place in either vendor, without a restart:
Bookie first takes a safety backup, then replaces all data and validates it. See
`docs/architecture/data-lifecycle.md` for details and the Azure SQL migration runbook.

## Tests

### Backend

```bash
cd backend
./gradlew test       # fast suite
./gradlew slowTest   # timer/poll-driven integration tests (@Tag("slow"))
./gradlew check      # both, plus formatting checks
./gradlew llmTest    # on-demand real-LLM smoke tests (@Tag("llm"))
./gradlew sqlServerTest  # SQL Server parity tests (@Tag("sqlserver"), needs Docker)
```

The fast suite includes the module-boundary check: `ModuleBoundaryTest` runs Spring Modulith's
`ApplicationModules.verify()` against each module's declared dependencies plus ArchUnit layer rules
(`./gradlew test --tests 'com.bookie.architecture.*'`).

### Frontend

```bash
cd frontend
npm run lint
npm test
npm run typecheck
cd src-tauri && cargo test --lib
```

Released migration checks can be run without a database:

```bash
cd backend
python3 scripts/verify_released_migrations.py
python3 -m unittest scripts/test_verify_released_migrations.py
```

## API Contract

- OpenAPI JSON: `http://localhost:48763/v3/api-docs`
- Swagger UI: `http://localhost:48763/swagger-ui/index.html`
- Error envelope: `{ "code": "...", "message": "...", "details": { ... } }`

## Diagrams

- [Architecture](diagrams/architecture.md)
- [Entity relationships](diagrams/erd.md)
- [Major flow sequences](diagrams/sequence-diagrams.md)

## Releasing the Desktop App

Releases are built and published by `.github/workflows/release.yml`, triggered by pushing a
`v*.*.*` tag:

```bash
# 1. Bump the version in frontend/src-tauri/tauri.conf.json, commit it.
# 2. Tag and push:
git tag v0.2.0
git push origin v0.2.0
```

For each OS (macOS, Windows, Linux) the workflow:

1. Builds the backend into a self-contained native app-image via `jpackage` (bundled JRE, no
   system Java required) — see `backend/build.gradle`'s `jpackageImage` task and
   `frontend/scripts/prepare-backend-runtime.mjs`.
2. Builds the Tauri app, bundling that app-image as a resource so the desktop app can start the
   backend without any external dependencies.
3. Signs the update artifact and creates a **draft** GitHub Release with installers for every
   platform plus `latest.json` (used by the in-app updater).

Review the draft release on GitHub and publish it manually — nothing is public until then. Once
published, existing installs auto-detect the update via the endpoint configured in
`tauri.conf.json`'s `plugins.updater.endpoints`.

Required repo secret: `TAURI_SIGNING_PRIVATE_KEY` (the Tauri updater signing private key;
generate a keypair with `npx tauri signer generate` and set the public key in
`tauri.conf.json`'s `plugins.updater.pubkey`). If the private key has a password, also set
`TAURI_SIGNING_PRIVATE_KEY_PASSWORD`.

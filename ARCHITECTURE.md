# Bookie Architecture

A household financial-activity tracking application built with Spring Boot and React. Bookie
supports rental, employment, self-employment, and unclassified activity contexts; it is not a
tax-filing engine.

## Architecture

- **Backend:** Spring Boot 3.5, Java 21, H2 (file-based at `~/.bookie/bookiedb`), JPA/Hibernate, Lombok, springdoc OpenAPI
- **Frontend:** React 19, React Router, Vite — delivered as a standalone **Tauri 2** desktop app (`frontend/src-tauri/`)
- **Desktop:** Tauri 2 (Rust) wraps the React frontend; on startup it spawns the Spring Boot backend if not already running, waits for it to be healthy, then shows the window. In release builds, a supervisor thread also watches for the backend process exiting unexpectedly and auto-restarts it (bounded retries, then a blocking error dialog). A single-instance guard focuses the existing window instead of spawning a duplicate backend if the app is launched twice (relevant since it also has autostart + a tray icon).
- **Build:** Gradle manages the backend only. The frontend is built and run via `npm run dev:tauri` / `npm run build:tauri` in the `frontend/` directory
- **AI Agent:** Integrated AI service (`gpt-5-mini` by default) - `AgentService` extracts a proposed income or expense from freeform chat; nothing is saved until the user reviews and explicitly saves it in the UI
- **Email Parsing:** Integrated AI service (`gpt-5-mini` by default) - used by `EmailParserService` for neutral structured extraction from Outlook emails before deterministic activity/category classification
- **Auto-Import Polling:** `AutoImportPollingService` runs on a schedule (default every 30 min, `bookie.auto-import.*`) to auto-queue new Outlook emails and OneDrive receipts for parsing, so items appear in the editable review queue without a manual "Parse" click

## Project Structure

```
backend/src/main/java/com/bookie/
  controller/   REST controllers + transport DTOs + SpaFilter
  model/        JPA entities + domain enums
  repository/   Spring Data JPA repositories
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
- `FinancialCategory` captures direction (`INCOME`/`EXPENSE`), tax treatment, stable key, and an
  optional tax line. The legacy `ExpenseCategory` enum remains only as a compatibility adapter for
  existing storage and clients.
- Rental activities have exactly one property. Non-rental activities cannot reference a property.
- New transaction writes validate that activity, direction, and category are compatible. These
  classifications organize records; they do not calculate or file taxes.
- `PropertyType` enum has a `label` field for display
- The frontend fetches activities and compatible categories from the backend instead of
  hardcoding the domain catalog.

## Financial Activity and Reporting Model

- `HouseholdMember` owns zero or more financial activities. The system creates a default household
  member during migration so existing installations remain usable.
- Creating a property creates its corresponding Schedule E rental activity. Deleting a property
  first reclassifies related transactions to `NEEDS_CLASSIFICATION`, then removes that rental
  activity.
- Transaction APIs accept flat `activityId` and `categoryId` references. For rental activities,
  the backend derives the property; clients do not independently choose an inconsistent property.
- Pending income and expense records carry the same activity/category context as finalized records
  so the review queue is the classification boundary.
- `GET /api/reports/cashflow?from=...&to=...` is the authoritative server-side cashflow
  aggregation. Optional `ownerId` and `activityId` filters scope income, expenses, net cashflow,
  and activity-level totals.
- `GET /api/reports/schedule-e?year=...` is the authoritative Schedule E aggregation by rental
  activity and financial category. It supports the same optional owner/activity filters, and
  negative net values are intentionally preserved.
- The Dashboard and Tax Report consume these report endpoints rather than reconstructing totals
  independently from transaction lists.

## Automated Financial Intake

- Agent chat, Outlook email, OneDrive receipts, and Venmo CSV imports all produce editable proposals
  or pending records; extraction never finalizes a transaction.
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
- Each script must be idempotent or irreversible-safe - Flyway checksums them and will refuse to restart if they change after being applied. Once a migration ships, do not touch it (even comment-only edits change the checksum); add a new V{n+1} script instead, or recover with `flyway.repair()` if a dev DB is stuck.
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

Do not write `ApplicationRunner` or `CommandLineRunner` beans to fix up the schema. That pattern is fragile, hard to test, and accumulates dead code once migrations complete.

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
| `OUTLOOK_CLIENT_ID` | Azure app client ID for Outlook integration |
| `OUTLOOK_CLIENT_SECRET` | Azure app client secret for Outlook integration |
| `OUTLOOK_TENANT_ID` | Azure tenant ID for Outlook integration |
| `OUTLOOK_REDIRECT_URI` | OAuth2 redirect URI (default: `http://localhost:48763/api/outlook/callback`) |

See `.env.example` for a template.

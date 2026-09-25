# Bookie Architecture

A household financial-activity tracking application built with Spring Boot and React. Bookie
supports rental, employment, self-employment, and unclassified activity contexts; it is not a
tax-filing engine.

## Architecture

- **Backend:** Spring Boot 3.5, Java 21, H2 (file-based at `~/.bookie/bookiedb`), JPA/Hibernate, Lombok, springdoc OpenAPI, Spring Modulith 1.4 (application modules checked by `ApplicationModules.verify()` in `ModuleBoundaryTest`; JDBC event publication registry)
- **Frontend:** React 19, React Router, Vite — delivered as a standalone **Tauri 2** desktop app (`frontend/src-tauri/`)
- **Desktop:** Tauri 2 (Rust) wraps the React frontend; on startup it spawns the Spring Boot backend if not already running, waits for it to be healthy, then shows the window. In release builds, a supervisor thread also watches for the backend process exiting unexpectedly and auto-restarts it (bounded retries, then a blocking error dialog). A controlled restore restart suppresses that supervisor, asks Spring to close gracefully, waits for the owned process and datasource to exit, then starts its replacement. A single-instance guard focuses the existing window instead of spawning a duplicate backend if the app is launched twice (relevant since it also has autostart + a tray icon).
- **Build:** Gradle manages the backend only. The frontend is built and run via `npm run dev:tauri` / `npm run build:tauri` in the `frontend/` directory
- **AI Agent:** Integrated AI service (`gpt-5-mini` by default) - `AgentService` extracts a proposed income or expense from freeform chat; nothing is saved until the user reviews and explicitly saves it in the UI
- **Email Parsing:** Integrated AI service (`gpt-5-mini` by default) - used by `EmailParserService` for neutral structured extraction from Outlook emails before deterministic activity/category classification
- **Auto-Import Polling:** `AutoImportPollingService` runs on a schedule (default every 30 min, `bookie.auto-import.*`) to discover Outlook emails and OneDrive receipts and create durable parsing jobs, so items appear in the editable review queue without a manual "Parse" click

## Topic Docs

Detailed contracts live in `docs/architecture/`. Read only the doc for the area you are changing:

| Doc | Covers |
| --- | --- |
| [`project-structure.md`](docs/architecture/project-structure.md) | Source tree of the backend packages, frontend and diagrams. |
| [`module-boundaries.md`](docs/architecture/module-boundaries.md) | Application modules, named interfaces, catalog and intake slices, and the enforced dependency rules. |
| [`financial-model.md`](docs/architecture/financial-model.md) | Activities, categories, report policy, the unified ledger, the inbox API and counterparties. |
| [`automated-intake.md`](docs/architecture/automated-intake.md) | Durable intake jobs, the embedded JobRunr engine, retries, and the worker allowlist. |
| [`integrations.md`](docs/architecture/integrations.md) | Outlook, OneDrive, LLM, document and Tauri plugin boundaries. |
| [`data-lifecycle.md`](docs/architecture/data-lifecycle.md) | Flyway migrations, released-migration rules, and backup restore safety. |

## Key Conventions

- All API routes are prefixed with `/api`
- API responses are transport DTOs (controllers do not expose JPA entities directly)
- API errors use a structured JSON envelope: `{ "code": "...", "message": "...", "details": { ... } }`
- OpenAPI docs are exposed at `/v3/api-docs` with Swagger UI at `/swagger-ui/index.html`
- `SpaFilter` (a `OncePerRequestFilter`) forwards non-API, non-file requests to `index.html` — present but unused in normal Tauri mode since the frontend is served by Tauri
- Every ledger transaction belongs to one `FinancialActivity` and one `NeutralCategory`; an inbox
  item proposes both until it is saved.
- `FinancialActivity` captures the household member, activity type, tax treatment, and optional
  rental property that provide transaction context. A system `NEEDS_CLASSIFICATION` activity
  holds unresolved transactions.
- `FinancialCategory` (`catalog.financialcategory`) keeps the profile-specific IDs, keys, treatment,
  and tax-line rows that report placement, classification history and inbox proposals still use.
  `NeutralCategory` owns the category label and direction; `LegacyCategoryMap` records every
  explicit old-key alias. The `ExpenseCategory` enum remains only for classification history.
- Rental activities have exactly one property. Non-rental activities cannot reference a property.
- New transaction writes validate that activity, direction, and category are compatible. These
  classifications organize records; they do not calculate or file taxes.
- `PropertyType` enum has a `label` field for display
- `Counterparty` is the domain term for an employer, vendor, tenant, customer, or other party. It is
  served at `/api/counterparties` and stored in the `counterparties*` tables (renamed from
  `payers*` by V20).
- The frontend fetches activities and compatible categories from the backend instead of
  hardcoding the domain catalog.

## Diagrams

Diagrams live in `diagrams/` as Markdown files with Mermaid source. Generated PNG previews under
`diagrams/rendered/` provide consistent rendering in GitHub and IntelliJ:

- [`diagrams/architecture.md`](diagrams/architecture.md) — runtime topology, module dependencies,
  and durable intake execution
- [`diagrams/erd.md`](diagrams/erd.md) — catalog, ledger, intake, classification, and
  integration-setting relationships
- [`diagrams/sequence-diagrams.md`](diagrams/sequence-diagrams.md) — desktop startup, AI proposal
  save, automated intake, inbox save, reporting, and backup restore flows

- After any schema change that adds, removes, or renames a table, column, key, or relationship,
  update `diagrams/erd.md` before considering the task complete
- After any change that materially alters a module boundary, runtime component, controller/service
  responsibility, external integration, or major end-to-end flow, update
  `diagrams/architecture.md` and the affected sequence in `diagrams/sequence-diagrams.md`

## Environment Variables

| Variable | Description |
| --- | --- |
| `AI_CLI_PATH` | Optional absolute path to the AI service CLI executable |
| `AI_USE_LOGGED_IN_USER` | Use local logged-in auth for the AI service (default: `true`) |
| `AI_AUTH_TOKEN` | Optional token auth for the AI service when not using logged-in auth |
| `AI_MODEL_AGENT` | Model for `/api/agent/transaction` proposals (default: `gpt-5-mini`) |
| `AI_MODEL_CHAT` | Model for email parsing (default: `gpt-5-mini`) |
| `AI_MODEL_VISION` | Model for receipt OCR (default: `gpt-5-mini`) |
| `AI_TOOLS_TRACE_EVENTS` | Enables tool execution event tracing for diagnostics/tests (default: `false`) |
| `AI_REQUEST_TIMEOUT_MS` | Request timeout in milliseconds for AI service calls (default: `180000`) |
| `BOOKIE_INTAKE_WORKER_ENABLED` | Global gate for every scheduled or direct durable-job execution path (default: `true`) |
| `BOOKIE_INTAKE_WORKER_ALLOWED_JOB_TYPES` | Comma-separated claim allowlist (default: `TRANSLATE_OUTLOOK_ID,PARSE_OUTLOOK,PARSE_RECEIPT,MOVE_OUTLOOK,MOVE_RECEIPT`) |
| `OUTLOOK_CLIENT_ID` | Azure app client ID for Outlook integration |
| `OUTLOOK_CLIENT_SECRET` | Azure app client secret for Outlook integration |
| `OUTLOOK_TENANT_ID` | Azure tenant ID for Outlook integration |
| `OUTLOOK_REDIRECT_URI` | OAuth2 redirect URI (default: `http://localhost:48763/api/outlook/callback`) |

See `.env.example` for a template.

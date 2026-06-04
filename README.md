# Bookie

A rental income and expense tracking application for managing rental properties, tracking income and expenses, and parsing utility/vendor emails into expense records.

## Contributor Docs

- See `AGENTS.md` for coding-agent instructions, code style rules, and testing requirements.
- See `ARCHITECTURE.md` for system architecture, conventions, migrations, diagrams, and environment details.

## Tech Stack

- **Backend:** Spring Boot 3.5, Java 21, H2 (file-based at `~/.bookie/bookiedb`), JPA/Hibernate, Lombok
- **Frontend:** React 18, React Router, Vite 6 — built into `frontend/dist/`, copied to `build/resources/main/static/`, and served by Spring Boot
- **Build:** Gradle with `buildFrontend` → `copyFrontend` tasks that run `npm run build` and stage the output before `processResources`
- **AI Agent:** Integrated AI service — natural-language expense assistant responses
- **Email Parsing:** Integrated AI service — structured extraction from Outlook emails
- **Receipt OCR:** Integrated AI service — vision OCR fallback for scanned/image PDFs with no text layer
- **Desktop:** Electron wrapper that starts the local Spring Boot backend and opens the app as a desktop window

## Setup

### Prerequisites

- Java 21+
- Node.js 18+
- AI service CLI binary available on `PATH` (or set `AI_CLI_PATH`)
- AI service authentication available via local login or token

### Configuration

Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

Optional: create a local Spring-loaded overrides file at `.env.local.properties` for machine-specific
secrets/settings. The app loads it automatically via `spring.config.import`.

```bash
touch .env.local.properties
```

Use standard Java properties format in `.env.local.properties` (for example
`AI_AUTH_TOKEN=...`, `AI_MODEL_CHAT=...`). Environment variables still work and can override
values if set at process runtime.

| Variable | Description |
| --- | --- |
| `AI_CLI_PATH` | Optional absolute path to the AI service CLI executable |
| `AI_USE_LOGGED_IN_USER` | Use local logged-in auth for the AI service (default: `true`) |
| `AI_AUTH_TOKEN` | Optional token auth for the AI service when not using logged-in auth |
| `AI_MODEL_AGENT` | Model for `/api/agent/expense` responses (default: `gpt-4.1`) |
| `AI_MODEL_CHAT` | Model for email parsing (default: `gpt-4.1`) |
| `AI_MODEL_VISION` | Model for receipt OCR (default: `gpt-4.1`) |
| `AI_REQUEST_TIMEOUT_MS` | Request timeout in milliseconds for AI service calls (default: `180000`) |
| `OUTLOOK_CLIENT_ID` | Azure app client ID for Outlook integration |
| `OUTLOOK_CLIENT_SECRET` | Azure app client secret for Outlook integration |
| `OUTLOOK_TENANT_ID` | Azure tenant ID for Outlook integration |
| `OUTLOOK_REDIRECT_URI` | OAuth2 redirect URI (default: `http://localhost:48763/api/outlook/callback`) |

## Running the App

```bash
./gradlew bootRun  # builds frontend and starts Spring Boot at http://localhost:48763
cd frontend && npm run dev  # dev server at http://localhost:5173 (proxies /api to 48763)
```

## Running Desktop App (Electron)

The desktop wrapper runs `./gradlew bootRun` (which builds the frontend and starts Spring Boot), waits for `http://localhost:48763`, then opens the app in an Electron window.

```bash
cd electron
npm install
npm run dev
```

Optional environment overrides:

- `BOOKIE_APP_URL` (default `http://localhost:48763`)

## Running Tests

```bash
./gradlew test
```

## Project Structure

```
src/main/java/com/bookie/
  controller/   REST controllers + SpaController (SPA fallback filter)
  model/        JPA entities + enums (ExpenseCategory, PropertyType)
  repository/   Spring Data JPA repositories
  service/      Business logic
src/main/resources/
  application.properties
frontend/
  src/          React source
  dist/         Built React app (git-ignored, copied to build/ by Gradle)
  vite.config.js  Builds to frontend/dist/
electron/
  main.cjs      Electron main process (starts backend + opens desktop window)
  package.json
diagrams/
  erd.drawio          Entity-relationship diagram
  architecture.drawio System architecture diagram
```

## Diagrams

The `diagrams/` directory contains draw.io files viewable with the diagrams.net IntelliJ plugin or by importing into Lucidchart. They are kept in sync with the code — ERD reflects the JPA entity model, architecture reflects controllers, services, and external integrations.
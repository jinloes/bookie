# Bookie

A rental income and expense tracking application for managing rental properties, tracking income and expenses, and parsing utility/vendor emails into expense records.

## Tech Stack

- **Backend:** Spring Boot 3.5, Java 21, H2 (file-based at `~/.bookie/bookiedb`), JPA/Hibernate, Lombok
- **Frontend:** React 18, React Router, Vite 6 — built into `frontend/dist/`, copied to `build/resources/main/static/`, and served by Spring Boot
- **Build:** Gradle with `buildFrontend` → `copyFrontend` tasks that run `npm run build` and stage the output before `processResources`
- **AI Agent:** Anthropic API (Claude) — natural-language expense creation
- **Email Parsing:** Spring AI + LM Studio (OpenAI-compatible API, default model `qwen/qwen3.6-35b-a3`) — structured extraction from Outlook emails. Uses `/think` in the system prompt for reliable multi-step tool calling
- **Receipt OCR:** Spring AI + LM Studio — OCR fallback for scanned/image PDFs with no text layer
- **Desktop:** Electron wrapper that starts the local Spring Boot backend and opens the app as a desktop window

## Setup

### Prerequisites

- Java 21+
- Node.js 18+
- [LM Studio](https://lmstudio.ai/) with local server enabled (OpenAI-compatible API at `http://localhost:1234` by default)

```bash
# In LM Studio, load a model such as qwen/qwen3.6-35b-a3 and start the local server
```

### Configuration

Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

| Variable | Description |
| --- | --- |
| `ANTHROPIC_API_KEY` | Required for the AI Agent feature |
| `LM_STUDIO_BASE_URL` | LM Studio OpenAI-compatible server URL (default: `http://localhost:1234`) |
| `LM_STUDIO_API_KEY` | API key header value for LM Studio (default: `lm-studio`) |
| `LM_STUDIO_MODEL` | Model for email parsing (default: `qwen/qwen3.6-35b-a3`) |
| `LM_STUDIO_VISION_MODEL` | Model for receipt OCR (default: `qwen/qwen3.6-35b-a3`) |
| `OUTLOOK_CLIENT_ID` | Azure app client ID for Outlook integration |
| `OUTLOOK_CLIENT_SECRET` | Azure app client secret for Outlook integration |
| `OUTLOOK_TENANT_ID` | Azure tenant ID for Outlook integration |
| `OUTLOOK_REDIRECT_URI` | OAuth2 redirect URI (default: `http://localhost:8080/api/outlook/callback`) |

## Running the App

```bash
./gradlew bootRun  # builds frontend, starts LM Studio model, then starts Spring Boot at http://localhost:8080
cd frontend && npm run dev  # dev server at http://localhost:5173 (proxies /api to 8080)
```

## Running Desktop App (Electron)

The desktop wrapper runs `./gradlew bootRun` (which builds the frontend and starts LM Studio), waits for `http://localhost:8080`, then opens the app in an Electron window.

```bash
cd electron
npm install
npm run dev
```

Optional environment overrides:

- `BOOKIE_APP_URL` (default `http://localhost:8080`)

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
# Bookie

A rental income and expense tracking app with a Spring Boot backend and a React + Tauri desktop UI.

## Contributor Docs

- `AGENTS.md` for coding-agent instructions, style rules, and testing requirements
- `ARCHITECTURE.md` for architecture, conventions, migrations, and environment details

## Repo Layout

```
backend/              Spring Boot project (Gradle)
  src/main/java/
  src/main/resources/
  src/test/java/
frontend/             React + Vite + Tauri desktop app
  src/
  src-tauri/
diagrams/             draw.io architecture and ERD
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

Optional backend local overrides:

```bash
touch backend/.env.local.properties
```

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

## Tests

### Backend

```bash
cd backend
./gradlew test
```

### Frontend

```bash
cd frontend
npm run lint
npm test
```

## API Contract

- OpenAPI JSON: `http://localhost:48763/v3/api-docs`
- Swagger UI: `http://localhost:48763/swagger-ui/index.html`
- Error envelope: `{ "code": "...", "message": "...", "details": { ... } }`

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

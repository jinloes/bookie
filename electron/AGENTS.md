# Electron Agent Guide

This file contains instructions for coding agents working on the Electron desktop wrapper. General instructions are in the root `AGENTS.md`.

## Code Style

- Use CommonJS (`const`/`module.exports`) as Electron uses Node.js (main.cjs is not ESM)
- Add comments only for non-obvious WHY — hidden constraints or workarounds
- Keep the Electron main process minimal — avoid heavy logic
- Use standard Node.js utilities over custom implementations
- Follow the same principles as the backend: simplicity, clarity, maintainability

## Architecture

The Electron wrapper is **minimal and purposeful**:
- Manages the desktop window and lifecycle
- Handles backend startup/health checks
- Loads the frontend React app from the backend URL or local build
- All business logic lives in the backend; Electron only orchestrates

**Key responsibilities:**
1. Create and manage the BrowserWindow
2. Start the backend Spring Boot server (if not already running)
3. Wait for backend health check before loading the app
4. Handle app lifecycle (quit, window-all-closed, activate)
5. Show error dialogs if startup fails

**Do NOT add:**
- IPC channels with complex logic
- Native modules unless absolutely necessary
- Hardcoded paths or environment-specific code

## Configuration

- Backend URL is controlled by `BOOKIE_APP_URL` env var (defaults to `http://localhost:48763`)
- Health check timeout: 3 minutes (180s)
- Health check interval: 1 second
- Window min size: 1100x700
- Cross-platform: Windows uses `gradlew.bat`, Unix uses `./gradlew`

## Testing

Electron code is minimal; manual testing is sufficient:
- Test startup with backend already running
- Test startup with backend needing to be started
- Test shutdown (verify backend process is killed)
- Test error dialog when backend fails to start

## Deployment

- Electron launcher: `main.cjs`
- Package.json defines the entry point
- Dependencies are minimal (only `electron`)
- Node version is pinned in `volta` config (currently 20.20.2)

## Dependencies

Keep Electron dependencies minimal:
- Only add packages that provide critical desktop functionality
- Prefer built-in Node.js modules (e.g., `http`, `child_process`, `path`)
- Avoid heavy frameworks — this is a thin wrapper, not an app


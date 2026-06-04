# Frontend Agent Guide

This file contains instructions for coding agents working on the frontend. General instructions are in the root `AGENTS.md`.

## Code Style

- All code is auto-formatted by **Prettier** (100 char line width, 2 spaces, single quotes, ES5 trailing commas)
- All code must pass **ESLint** with no errors (warnings are acceptable)
- Prettier and ESLint are integrated to avoid conflicting rules
- Use standard React patterns: hooks, functional components, composition
- Prefer explicit over implicit; component APIs should be clear from the JSX

## Testing

The frontend uses **Vitest + React Testing Library**.

**Write tests only for high-value targets:**
- Pure utility functions (`src/utils/`) — always test these; they are pure, fast, and high-confidence
- Custom hooks with non-trivial logic (`src/hooks/`) — use `renderHook` + `act` from React Testing Library

**Do NOT write tests for:**
- Pages or layout components that are primarily API calls + render — mocking fetch, Mantine modals, and SSE is more churn than value
- The `src/api/index.js` layer — thin `fetch` wrappers with no logic

**Timing:** Write or update tests **after all frontend code changes in a task are complete**, not after each individual file change. One test run at the end is sufficient.

## Code Quality Workflow

After any JavaScript/JSX code changes in `src/`, you **must** run the following before considering the task complete:

```bash
cd frontend
npm run format  # Auto-format code with Prettier
npm run lint    # Check for ESLint violations
npm test        # Run tests (if applicable)
```

**This is non-negotiable:**
- Run `npm run format` after every JavaScript change
- All code must pass `npm run lint` with no errors
- Do not commit unformatted code
- Do not skip linting checks

## Dependencies

Manage frontend dependencies carefully:
- Add new dependencies sparingly — prefer standard library / React utilities
- Run `npm audit` before committing to check for security vulnerabilities
- Keep `package.json` versions reasonable and document pinned versions


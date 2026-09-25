# Frontend Agent Guide

This file contains instructions for coding agents working on the frontend. General instructions are in the root `AGENTS.md`.

## Architecture

The frontend is a **React + Vite** app delivered as a **Tauri 2** desktop application:
- `src/` — React source code
- `src-tauri/` — Tauri Rust wrapper (window management, backend lifecycle)
- `vite.config.js` — builds to `dist/`, which Tauri bundles into the desktop app

**Running the app:**
- `npm run dev:tauri` — start in dev mode (Tauri + Vite hot reload, requires Rust)
- `npm run dev` — Vite dev server only (browser at `http://localhost:5173`, requires backend running separately)
- `npm run build:tauri` — build the desktop app bundle

**API calls:**
- In dev mode (`import.meta.env.DEV = true`): Vite proxies `/api` → `http://localhost:48763`
- In production (Tauri bundle): API calls go to `http://localhost:48763/api` directly

## Code Style

- All code is auto-formatted by **Prettier** (100 char line width, 2 spaces, single quotes, ES5 trailing commas)
- All code must pass **ESLint** with no errors (warnings are acceptable)
- Prettier and ESLint are integrated to avoid conflicting rules
- Use standard React patterns: hooks, functional components, composition
- Prefer explicit over implicit; component APIs should be clear from the JSX

## Testing

The frontend uses **Vitest + React Testing Library**.

**Write tests for high-value targets:**
- Pure utility functions (`src/utils/`) — always test these; they are pure, fast, and high-confidence
- Custom hooks with non-trivial logic (`src/hooks/`) — use `renderHook` + `act` from React Testing Library
- Critical page flows only — behavior-focused tests of a user journey whose failure would lose or corrupt data or block core work (e.g. reviewing and saving an AI proposal, saving an inbox item, restoring a backup). Assert on what the user sees and does, not on component internals, and mock only `src/api`

**Do NOT write tests for:**
- Presentational or layout-only components and pages with no critical flow — mocking fetch, Mantine modals, and SSE is more churn than value
- The `src/api/index.js` layer — thin `fetch` wrappers with no logic

**Test conventions (follow these in every new or edited test):**
- Global setup lives in `src/test/setup.js` (registered in `vitest.config.js`): jest-dom matchers and jsdom polyfills (`matchMedia`, `ResizeObserver`, `scrollTo`, `scrollIntoView`). Never re-declare polyfills in a test file; add any new one there
- Render through the shared helpers in `src/test/render.jsx` instead of hand-building providers:
  - `renderWithProviders(ui, { route, queryClient })` for components/pages (Mantine + React Query + `MemoryRouter`); it also returns `queryClient` and a `userEvent` `user`
  - `createQueryWrapper(queryClient)` as the `wrapper` for `renderHook`
  - `createTestQueryClient()` when a test needs to spy on `invalidateQueries`/`setQueryData`
- Mock the API with Vitest automocking: `vi.mock('../api/index.js')`, then `vi.mocked(api.getTransactions).mockResolvedValue(...)`. Do not write `fn: (...args) => mockFn(...args)` forwarding factories
- Assert with jest-dom matchers (`toBeInTheDocument`, `toHaveValue`, `toBeDisabled`, `toHaveTextContent`), not `toBeTruthy()` on DOM nodes
- Drive interactions with `userEvent` (`user.click`, `user.type`), not `fireEvent`; the only exception is a control userEvent cannot drive, with a one-line comment saying why
- Query by role or label (`getByRole`, `getByLabelText`); do not add `data-testid`
- Keep Vitest's default 5s timeout. Fix slow or flaky tests by awaiting the right `findBy*`/`waitFor` and using `user.paste` for long text (the helper's `user` already has `delay: null`), never by raising `testTimeout`. `maxWorkers: 2` in `vitest.config.js` is deliberate: heavier Mantine page tests time out under more parallel workers

**Mutation handlers must be extracted into hooks:** Any handler that does more than a single API call — e.g. an API call followed by cache invalidation (`queryClient.invalidateQueries`/`setQueryData`), navigation, or a notification — must be extracted out of the page/component into a custom hook in `src/hooks/` (e.g. `useParseReceipt`, `useParseEmail`), even if it's only used in one place. This is not optional: this exact class of logic (missing cache invalidation) has caused a real production bug that page-level tests would never have caught. The extracted hook must have tests asserting the correct query keys are invalidated/updated, following the "custom hooks with non-trivial logic" rule above. The page/component should be reduced to wiring the hook's returned state/handlers into JSX; a critical-flow page test (if any) exercises the user journey, while cache behavior is asserted in the hook test.

**Any SSE-invalidated query key needs a poll-while-pending backstop:** If a query's cache is invalidated by a server-sent event (see `usePendingSSE.js`, which listens for the inbox `inbox-updated` event), that event is not guaranteed to arrive — dropped connections, reconnect races, and backgrounded tabs can all cause it to be missed, leaving stale data on screen indefinitely. Every such query must have a self-healing polling fallback following the `usePendingQueue.js` pattern: poll on a short interval (e.g. 5s) only while an item is in a transient/pending state, and stop polling once it resolves. This is not optional — a missed SSE event with no fallback caused a real "stuck in PROCESSING" production bug. Do not add a new real-time channel without also adding its polling backstop.

**AI-extracted records must be confirmed before they're saved:** Any feature that uses the AI model to extract a financial record from freeform input (the Agent chat page, email/receipt parsing) must show the user an editable proposal and require an explicit "Save" action before anything is written to the database. Never call a create/save endpoint directly from an AI response — a misheard amount or wrong category becoming a committed record with no undo/review step is a trust failure, not just a UX nicety. See `AgentService.ProposedTransaction` (backend) and `ProposalCard` in `Agent.jsx` (frontend) for the pattern.

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
- Rust dependencies for the Tauri wrapper live in `src-tauri/Cargo.toml` — keep those minimal too


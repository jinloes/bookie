# Automated Financial Intake

[← Architecture index](../../ARCHITECTURE.md)

## Automated Financial Intake

- Agent chat, Outlook email, OneDrive receipts, Venmo CSV imports, and bank statement (OFX/QFX)
  imports all produce editable proposals or inbox items; extraction never finalizes a transaction.
- Bank statements are uploaded exports, not an aggregator connection, so Bookie holds no bank
  credentials or tokens. `OfxStatementInputAdapter` reads OFX 1.x SGML, OFX 2.x XML and QFX with a
  lexical tokenizer (no XML parser, so no DTD or entity processing), keeps only `STMTRS` bank
  accounts, and replaces the account number with a one-way hash in each row's source ID. The file
  is neither stored nor logged; the import logs counts and, for a row that fails, only its index
  and exception type, and keeps Spring's 1 MB upload limit.
- `inbox_items` (with `inbox_artifacts` and `background_jobs`) is the only intake store. Every
  producer creates or reuses an item by its `(origin, source id)` identity. Historically, Flyway V14
  copied every legacy pending row into it and V20 verified parity before dropping the legacy pending
  tables (see [`data-lifecycle.md`](data-lifecycle.md)).
- The durable lifecycle is `RECEIVED -> QUEUED -> PROCESSING -> READY -> SAVE_PENDING -> SAVED`,
  with explicit `FAILED` and `DISMISSED` transitions. External synchronization has a separate state
  so a completed financial save is never represented as lost merely because Graph or OneDrive is
  unavailable.
- Parsing, Outlook moves, receipt moves, and legacy-ID translation execute directly over
  `background_jobs` via `DurableBackgroundJobWorker` and `BackgroundJobService` (one worker, five-
  second poll). `claimDue` claims due `AVAILABLE` rows and expired `LEASED` orphans under
  pessimistic database locks and stamps a unique lease token (`lease_owner`) and expiry
  (`lease_expires_at`). Retry `n` is scheduled after `3^n` seconds (3, 9, 27, 81, 243, 729, 2187,
  6561, 19683, 59049) up to `max_attempts` (default 11); attempt 11 becomes `TERMINAL` with reason
  `MAX_ATTEMPTS`. Flyway V25 retired the embedded JobRunr engine and its `jobrunr_*` tables.
- Saving a reviewed item (`POST /api/v2/inbox/{id}/save`) writes the ledger row, the SAVED inbox
  state, and the external-sync job in one database transaction. The save never files a receipt or
  calls Graph inline: Graph and OneDrive effects run only as durable jobs after that transaction
  commits, and are idempotent or fail visibly.
- Job kickoffs are Spring application events published inside the transaction that scheduled the
  job: `InboxItemSaved` (EV-1) when a save schedules an external-sync job, `InboxItemQueued` (EV-2)
  when an item is created or requeued for parsing, reparsed, or a terminal job is explicitly
  retried, and `BackgroundJobQueued` when a job without an inbox item (a manually entered receipt's
  move) is scheduled or retried. `IntakeJobKickoffListener` is an `@Async`
  `@TransactionalEventListener(AFTER_COMMIT)` and kicks the relay after commit without a wrapping
  transaction. A kick is only a latency optimization: the durable job row is the source of truth, a
  rolled-back transaction schedules and kicks nothing, and the five-second relay recovers any lost
  kick.
- `BOOKIE_INTAKE_WORKER_ENABLED` is a global execution gate for scheduled polling, post-save and
  parse kickoffs, and explicit retries. It defaults to `true` after the validated ID-translation
  rollout. `BOOKIE_INTAKE_WORKER_ALLOWED_JOB_TYPES` defaults to `TRANSLATE_OUTLOOK_ID`,
  `PARSE_OUTLOOK`, `PARSE_RECEIPT`, `MOVE_OUTLOOK`, and `MOVE_RECEIPT`. Outlook moves retain their
  parent-folder identity and replay safeguards. `MOVE_RECEIPT` is allowlisted because an inbox
  receipt's move refuses to act without a durable artifact whose persisted SHA-256 still matches the
  current remote bytes, and its destination folder move is idempotent and preserves the OneDrive
  item ID. A manually entered receipt's move has no inbox item or parsed artifact: its job names the
  OneDrive item (`source_external_id`) the user attached, and moves it without a checksum guard.
- `/api/v2/inbox` exposes durable item, external-sync, error, and projected job status, while terminal
  jobs can be explicitly retried (see the inbox API in
  [`financial-model.md`](financial-model.md)). Outlook ID translation sets
  `inbox_items.immutable_source_id`; saved financial source IDs remain unchanged. Because the
  post-save move changes a message's REST ID, the Outlook email list (and so the auto-import poll)
  also treats a message as saved when a SAVED inbox item with the same immutable ID points at a
  pre-move ID that still has an active ledger transaction.
- `AutomatedIntakeClassificationService` applies one deterministic classification policy after
  extraction: an explicitly configured activity wins, followed by unique confirmed
  activity-scoped keyword history, followed by deterministic rental-property resolution.
  Unresolved or conflicting evidence stays on `NEEDS_CLASSIFICATION` with an ambiguity warning.
- Outlook watched folders may carry an optional activity. A configured activity includes all dated
  messages from that folder and is copied into the inbox item; folders without one keep the
  Rental-category filter. Saving an empty watched-folder selection disables that feed.
- Confirmed keyword history stores activity and category together, preventing a shared counterparty
  or keyword from leaking a classification between employment, self-employment, and rental work.
- Review screens allow the user to correct direction, activity, category, and source-specific
  details. Only the explicit **Save** action calls the inbox save or transaction create API.

### Outbox polling, leases and lifecycle

**Execution mode.** `bookie.intake.execution-mode` is `CONTINUOUS` by default on H2: the poller
becomes ready at `ApplicationReadyEvent` and polls `background_jobs` every five seconds all
session. The `sqlserver` vendor always uses `ON_DEMAND` so Azure SQL serverless can auto-pause. In
that mode `OnDemandIntakeDrainer` runs passes only when there is work: after Ready (to catch up on
work queued while the app was closed), after a save or import hint, and on `POST /api/intake/drain`
(202), which the app calls on window focus as its recovery check. There is no timed recovery drain,
so a lost kickoff or a retry that falls due after draining stopped waits for the next startup,
kickoff or focus. It polls every poll interval until no job is due or leased for a quiet period,
then stops the worker and holds no database session. Outlook and OneDrive auto-import run once at
startup (`bookie.auto-import.startup-only`) and on demand through `POST /api/auto-import/run` (the
Inbox "Check for new items" action) instead of on a timer. Frontend polls are bounded to two minutes
after the last change and resume on window focus or an inbox invalidation.

`BackgroundJobService.claimDue` claims up to `maxJobsPerPoll` allowed due rows (`AVAILABLE` with
`available_at <= now`, or `LEASED` with `lease_expires_at <= now`) in one short transaction before
any provider call. Multiple claimed `TRANSLATE_OUTLOOK_ID` rows are executed together through
`IntakeJobDispatcher.executeBatch`; all other job types run individually outside database locks.
For parsing jobs, `enterExecution` binds the claimed job ID and `lease_owner` token in a
`ThreadLocal` so `requireCurrentParse` rejects stale proposals if a previous attempt was reset or
superseded before committing.

`BackgroundJobService.finish` verifies that the row still carries the caller's `lease_owner` token
before recording `COMPLETED`, scheduling a `3^n`-second retry (`AVAILABLE`), or marking `TERMINAL`
(`MAX_ATTEMPTS`, `MANUAL_REVIEW`, or `TERMINAL`). Dismissing an Outlook item still runs its Outlook
ID translation and move; any other work for a dismissed item is terminalized with reason `DISMISSED`
when encountered and is never resurrected. Explicit `POST /api/v2/inbox/jobs/{jobId}/retry` resets a
non-dismissed `TERMINAL` job back to `AVAILABLE` with `attempts = 0` and `max_attempts = 11`.
Remote effects remain at-least-once and rely on immutable identity, SHA-256 checksum, and
destination-year guards.

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
- Parsing, Outlook moves, receipt moves, and legacy-ID translation execute through embedded
  JobRunr OSS 8.8.1, one worker with five-second pickup. JobRunr alone owns execution, ten retries
  after the first attempt, and orphan recovery. Retry n is due after 3^n seconds (3, 9, 27, 81,
  243, 729, 2187, 6561, 19683, 59049); failure 11 remains FAILED. No jitter, cap, override or
  separate orphan allowance is applied.
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

### Engine storage, bindings and lifecycle

**Execution mode.** `bookie.intake.execution-mode` is `CONTINUOUS` by default on H2: the engine
starts at Ready and polls every five seconds all session. The `sqlserver` vendor always uses
`ON_DEMAND` so Azure SQL serverless can auto-pause. In that mode `OnDemandIntakeDrainer` starts the
engine only when there is work: after Ready (to catch up on work queued while the app was closed),
after a save or import hint, and on `POST /api/intake/drain` (202), which the app calls on window
focus as its recovery check. There is no timed recovery drain, so a lost kickoff or a retry that
falls due after the engine stopped waits for the next startup, kickoff or focus.
It relays every poll interval until no job has been due or bound for a quiet period, then stops the
engine and holds no database session. Outlook and OneDrive auto-import run once at startup
(`bookie.auto-import.startup-only`) and on demand through `POST /api/auto-import/run` (the Inbox
"Check for new items" action) instead of on a timer. Frontend polls are bounded to two minutes after
the last change and resume on window focus or an inbox invalidation, which also requests a
throttled recovery drain. Retry due times are unchanged, but a retry that comes due while the
engine is stopped runs at the next drain rather than punctually.

Flyway V16 adds `execution_id` (indexed nullable UUID), `execution_attempt_base`,
`execution_previous_max_attempts`, and `execution_started` to `background_jobs`, plus the pinned
PUBLIC JobRunr tables, indexes, stats view and counter seed. V1-V14 and historical cells remain
unchanged. One engine UUID binds a singleton or a bounded translation batch; later arrivals cannot
join it. The UUID reference is logical, not a foreign key: vendor retention may remove old records.
The persisted payload is `DurableBackgroundJobWorker.executeV1(String type, JobContext)` with
`JobContext.Null` at enqueue; context supplies the UUID, and locked business bindings supply members.

`relay()` only binds due intent, publishes missing never-started UUIDs, and projects raw engine
state. It is not an executor. Start evidence commits before provider calls; callbacks and outcome
commits validate the current generation. Finished members keep their counters and are skipped by
batch retries. Retryable outcomes commit independently before the worker throws for native retry.
Unexpected dispatcher errors remain business-terminal; infrastructure/persistence errors escape.
Unsupported callback contracts are native nonretryable failures.

The scheduler, relay and restore use the single raw H2 `StorageProvider` bean. Only the
Ready-created server receives `JobRunrIntakeStorageProvider`, behind the engine's thread-safe wrapper.
It filters exactly ENQUEUED list reads, due SCHEDULED reads, and cutoff PROCESSING orphan reads
before native mutations. Ordinary pages, by-ID reads, counts, stats, metadata and persistence remain
raw. The decorator inherits public native claims and optimistic-conflict handling instead of
forwarding to H2's optimized claim override (which bypasses candidate reads). No election filter
replaces or supplements JobRunr's default retry chain.

Startup-immutable disallowed types keep state, due time, version and failure history; unknown
callback contracts still follow native failure handling. Disabled orphans receive neither synthetic
heartbeats nor failures. Re-enabling the same UUID lets native orphan handling apply its remaining
retry policy, including final exhaustion. Healthy workers retain native heartbeats.
`BOOKIE_INTAKE_WORKER_LEASE_SECONDS` is now a deprecated heartbeat-timeout alias:
`max(4, ceil(seconds / 5))` polls, not an application lease. Legacy lease columns remain in backups;
current LEASED projections have null owner/expiry.

Candidate selection reads expanding complete prefixes, retaining the caller's order and cutoff,
from `min(count, max(64, limit))` up to the captured state count. Each prefix is refiltered; overlapping
prefixes are never concatenated. Timestamp ties need no unsupported ID sort or unsafe offset cursor.
Selection terminates at sufficient allowed rows, a short prefix, or the finite ceiling; fresh calls
observe concurrent growth. Unrepresentable counts fail explicitly. Worst-case rows and memory are
O(backlog); an individual scan can exceed the poll interval. This deliberately retains a single
desktop server, not a multi-process queue or pickup-latency guarantee.

Historical attempts are snapshotted at adoption; 2/5 becomes a fresh 11-execution generation reported
against cumulative ceiling 13, not three remaining retries. Already exhausted legacy rows remain
terminal. SCHEDULED projects the engine due time/error, PROCESSING projects LEASED, and final FAILED
projects MAX_ATTEMPTS. Retained disabled orphans can remain LEASED. Started missing executions and
unfinished DELETED/SUCCEEDED anomalies require manual review. Explicit permitted retry clears
binding/start evidence; ensure/read paths never reset a generation. Dismissing an Outlook item still
runs its Outlook ID translation and move; any other work for a dismissed item is terminalized with
reason DISMISSED when the relay meets it and is never resurrected.

Raw storage follows Flyway with `skip-create=true`. PostRestoreValidator reconciles all PUBLIC rows,
including vendor metadata/history, at Started before Ready permits server creation, adoption,
publication or execution. Dashboard and telemetry are off. Server shutdown precedes the
container-owned raw storage close and datasource closure. Stop old executors before cutover and
retain backups; rollback requires disabling execution, and V16 downgrade is unproven. Remote
effects are at-least-once, not exactly-once.

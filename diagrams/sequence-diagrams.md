# Bookie Major Flow Sequence Diagrams

These Mermaid sequence diagrams cover the system's major end-to-end flows. Generated PNG previews
are included for Markdown viewers that do not execute Mermaid. The diagrams emphasize transaction
boundaries, explicit user confirmation, asynchronous execution, and failure-safe restore behavior.
The PNG previews may lag the Mermaid source; the source is authoritative.

## Desktop Startup and Supervision

![Desktop startup and supervision sequence](rendered/sequence-desktop-startup.png)

<details>
<summary>Mermaid source</summary>

```mermaid
sequenceDiagram
  actor User
  participant Tauri as Tauri host
  participant Backend as Spring Boot
  participant Restore as Restore bootstrap
  participant DB as H2
  participant UI as React WebView
  participant Supervisor as Backend supervisor

  User->>Tauri: Launch Bookie
  Tauri->>Tauri: Enforce single instance
  alt backend is not already available
    Tauri->>Backend: Spawn bundled runtime
  end
  Backend->>Restore: Check durable restore journal before datasource startup
  opt validated restore is pending
    Restore->>DB: Atomically retain live DB and activate shadow DB
  end
  Backend->>DB: Flyway migrate and validate schema
  Backend->>Restore: Post-start integrity validation
  Tauri->>Backend: Poll port / restore status until ready
  Backend-->>Tauri: Ready
  Tauri->>UI: Show main window
  Tauri->>Supervisor: Start release-mode supervision

  loop every 5 seconds
    Supervisor->>Backend: Check owned child process
    alt unexpected exit
      Supervisor->>Backend: Restart, up to bounded retry limit
    end
  end
```

</details>

## AI Proposal and Explicit Save

![AI proposal and explicit save sequence](rendered/sequence-ai-proposal-save.png)

<details>
<summary>Mermaid source</summary>

```mermaid
sequenceDiagram
  actor User
  participant UI as Agent page
  participant Agent as AgentController / AgentService
  participant LLM as LLM gateway
  participant Catalog as Catalog + classification
  participant API as TransactionController (/api/v2/transactions)
  participant Ledger as LedgerTransactionService
  participant DB as H2

  User->>UI: Describe a transaction
  UI->>Agent: POST /api/agent/transaction
  Agent->>LLM: Extract neutral transaction fields
  LLM-->>Agent: Direction, amount, date, description, counterparty
  Agent->>Catalog: Resolve stored activity, category, property, counterparty
  Catalog-->>Agent: Proposed classification + ambiguity flag
  Agent-->>UI: Editable proposal
  UI-->>User: Show proposal without saving

  User->>UI: Review, edit, and click Save
  UI->>API: POST /api/v2/transactions (activityId, neutralCategoryId)
  API->>Ledger: create(input)
  rect rgb(235, 248, 235)
    Note over Ledger,DB: One database transaction
    Ledger->>Catalog: Resolve and validate activity/category/direction/counterparty
    Ledger->>DB: Insert financial transaction + import reference + attachment
    Ledger->>Catalog: Record confirmed classification history
  end
  API-->>UI: Saved transaction
  UI->>UI: Invalidate transactions, inbox, and report queries
```

</details>

## Automated Intake Discovery and Parsing

![Automated intake discovery and parsing sequence](rendered/sequence-automated-intake.png)

<details>
<summary>Mermaid source</summary>

```mermaid
sequenceDiagram
  participant Poller as AutoImportPollingService
  participant Graph as Microsoft Graph
  participant Intake as Outlook/receipt intake service
  participant DB as Durable inbox
  participant Relay as Durable job relay
  participant JobRunr
  participant Worker as DurableBackgroundJobWorker
  participant Parser as EmailParserService
  participant LLM as LLM gateway
  participant UI as Review queue

  Poller->>Graph: List watched emails and configured receipt folders
  Graph-->>Poller: Candidate messages, attachments, and files
  loop each unseen target within poll budget
    Poller->>Intake: Queue source identity
    rect rgb(235, 248, 235)
      Note over Intake,DB: One database transaction
      Intake->>DB: Create or reuse inbox item by (origin, source id)
      Intake->>DB: Persist artifacts and parse intent
      Intake->>DB: Record InboxItemQueued in EVENT_PUBLICATION
    end
    Intake->>Relay: Post-commit kickoff hint (async listener; may be lost)
  end

  Relay->>DB: Bind due intents to execution UUIDs
  Relay->>JobRunr: Publish missing executions
  JobRunr->>Worker: executeV1(type, JobContext)
  Worker->>DB: Persist execution start
  Worker->>Parser: Fetch and parse source
  Parser->>Graph: Read immutable email/attachment or receipt bytes
  Parser->>LLM: Extract neutral financial fields
  LLM-->>Parser: Structured suggestion
  Parser->>DB: Persist READY proposal or visible failure
  Worker->>DB: Commit business outcome
  Parser-->>UI: SSE inbox-updated (/api/v2/inbox/events)
  Note over UI,DB: Poll-while-transient is the fallback if SSE is missed
```

</details>

## Review Queue Save and External Synchronization

![Review queue save and external synchronization sequence](rendered/sequence-review-save.png)

<details>
<summary>Mermaid source</summary>

```mermaid
sequenceDiagram
  actor User
  participant UI as Review queue
  participant Controller as InboxController
  participant Save as InboxCommandService
  participant Ledger as LedgerTransactionService
  participant Inbox as Durable inbox/job store
  participant Worker as JobRunr worker
  participant Graph as Graph / OneDrive

  User->>UI: Correct direction, activity, category, and details
  User->>UI: Click Save
  UI->>Controller: POST /api/v2/inbox/{id}/save
  Controller->>Save: save(id, command)
  rect rgb(235, 248, 235)
    Note over Save,Inbox: One database transaction
    Save->>Ledger: createFromInbox (validate, insert transaction; never files a receipt)
    Save->>Inbox: Mark item SAVED and persist MOVE_OUTLOOK / MOVE_RECEIPT intent
    Save->>Inbox: Record InboxItemSaved in EVENT_PUBLICATION
  end
  Save-->>Worker: After commit, the async kickoff listener hints the committed job
  Save-->>UI: Return saved inbox item
  UI->>UI: Refresh inbox, transactions, and reports

  Worker->>Graph: Move Outlook message or receipt
  alt remote effect succeeds or is already complete
    Worker->>Inbox: Mark external sync complete
  else retryable provider failure
    Worker->>Inbox: Persist visible error
    Worker-->>Worker: Let JobRunr schedule native retry
  else ambiguous or unsafe outcome
    Worker->>Inbox: Mark manual review required
  end
```

</details>

## Manual Receipt Filing

<details>
<summary>Mermaid source</summary>

```mermaid
sequenceDiagram
  actor User
  participant UI as Transaction form
  participant API as TransactionController
  participant Ledger as LedgerTransactionService
  participant Registry as EVENT_PUBLICATION registry
  participant Filing as ReceiptFilingListener (intake)
  participant Resubmitter as EventPublicationResubmitter
  participant OneDrive

  User->>UI: Save an expense with origin RECEIPT and an attachment
  UI->>API: POST /api/v2/transactions
  API->>Ledger: create(input)
  rect rgb(235, 248, 235)
    Note over Ledger,Registry: One database transaction
    Ledger->>Ledger: Insert transaction + attachment, record classification
    Ledger->>Registry: Publish TransactionRecorded (incomplete publication)
  end
  API-->>UI: Saved transaction (receipt may not be filed yet)
  Registry-->>Filing: After commit, asynchronously, no wrapping transaction
  Filing->>OneDrive: Move file into the tax-year folder (idempotent)
  alt move succeeds
    Filing->>Registry: Complete (publication deleted)
  else move fails
    Filing-->>Registry: Throw; publication stays incomplete
    loop after Ready, while incomplete and older than 5 minutes
      Resubmitter->>Filing: Resubmit TransactionRecorded
      Filing->>OneDrive: Retry the move
    end
  end
```

</details>

Only the manual create path publishes `TransactionRecorded`; a rolled-back create records and files
nothing. An inbox receipt save records the transaction without the event; its durable
`MOVE_RECEIPT` job moves the file after commit (see above).

## Reporting

![Reporting sequence](rendered/sequence-reporting.png)

<details>
<summary>Mermaid source</summary>

```mermaid
sequenceDiagram
  actor User
  participant UI as Dashboard / Tax Report
  participant API as ReportController
  participant Service as ReportService
  participant Query as UnifiedLedgerReportQuery
  participant Policy as ReportPolicyResolver

  User->>UI: Open report or change filters
  UI->>API: GET /api/reports/cashflow or /schedule-e
  API->>Service: Request period + owner/activity filters
  Service->>Query: Query immutable aggregate totals
  Query->>Query: Aggregate active financial_transactions
  Query-->>Service: Totals by activity/category
  Service->>Policy: Resolve effective profile and category mappings
  Policy-->>Service: Included report lines or explicit parity error
  Service-->>API: Cashflow or Schedule E result
  API-->>UI: Authoritative server-side report
```

</details>

## Backup Restore

![Backup restore sequence](rendered/sequence-backup-restore.png)

<details>
<summary>Mermaid source</summary>

```mermaid
sequenceDiagram
  actor User
  participant UI as Backup page
  participant API as BackupController
  participant OneDrive
  participant Shadow as ShadowRestoreService
  participant Journal as Durable restore journal
  participant Tauri as Tauri host
  participant Backend as Spring Boot
  participant Bootstrap as RestoreBootstrap
  participant Validator as PostRestoreValidator

  User->>UI: Select backup and restore
  UI->>API: POST /api/backup/restore/{fileId}
  API->>OneDrive: Validate metadata and download bounded backup
  API->>Shadow: Stage isolated shadow database
  Shadow->>Shadow: Run Flyway (V20 prechecks reject a drifted pre-V20 backup) and integrity verification
  Shadow->>Journal: Persist checksummed VALIDATED journal
  API-->>UI: VALIDATED, restartRequired=true

  alt packaged Tauri owns backend
    UI->>Tauri: restart_backend_after_restore(restoreId)
    Tauri->>Backend: POST /api/backup/restore/shutdown
    Backend-->>Tauri: 202 Accepted, close datasource, exit
    Tauri->>Backend: Start replacement process
    Backend->>Bootstrap: Read journal before datasource creation
    Bootstrap->>Bootstrap: Atomic live -> rollback, shadow -> live
    Backend->>Validator: Capture and reconcile active integrity manifest
    alt validation succeeds
      Validator->>Journal: POST_START_VALIDATED
      Backend-->>Tauri: Restore status ready
      Tauri-->>UI: Restart complete
    else validation fails
      Validator->>Journal: ROLLBACK_REQUIRED
      Backend--xTauri: Startup exits
      Tauri->>Backend: Start bounded recovery attempt
      Backend->>Bootstrap: Preserve failed candidate and reactivate rollback
      Bootstrap->>Journal: ROLLED_BACK
    end
  else browser or development mode
    UI-->>User: Leave candidate staged and require manual backend restart
  end
```

</details>

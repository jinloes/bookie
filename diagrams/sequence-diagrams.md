# Bookie Major Flow Sequence Diagrams

These Mermaid sequence diagrams cover the system's major end-to-end flows. Generated PNG previews
are included for Markdown viewers that do not execute Mermaid. The diagrams emphasize transaction
boundaries, explicit user confirmation, compatibility writes, asynchronous execution, and
failure-safe restore behavior.

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
  participant API as Income or Expense API
  participant Legacy as Legacy income/expense store
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
  UI->>API: POST /api/incomes or /api/expenses
  rect rgb(235, 248, 235)
    Note over API,DB: One database transaction
    API->>Catalog: Validate activity/category/direction
    API->>Legacy: Write compatibility row
    Legacy->>Ledger: Synchronize exact saved legacy snapshot
    Ledger->>DB: Upsert unified transaction + durable legacy map
  end
  API-->>UI: Saved transaction
  UI->>UI: Invalidate ledger, totals, activity, and report queries
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
  participant DB as Legacy pending + durable inbox
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
      Intake->>DB: Create/deduplicate legacy pending row
      Intake->>DB: Mirror inbox item, artifacts, and parse intent
    end
    Intake->>Relay: Post-commit kickoff hint
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
  Parser-->>UI: SSE pending-updated
  Note over UI,DB: Poll-while-pending is the fallback if SSE is missed
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
  participant Controller as PendingExpenseController
  participant Save as InboxSaveOrchestrator
  participant Catalog as Catalog validation
  participant Legacy as Legacy financial store
  participant Ledger as Unified ledger
  participant Inbox as Durable inbox/job store
  participant Worker as JobRunr worker
  participant Graph as Graph / OneDrive

  User->>UI: Correct direction, activity, category, and details
  User->>UI: Click Save
  UI->>Controller: POST pending item save endpoint
  Controller->>Save: Save as income or expense
  rect rgb(235, 248, 235)
    Note over Save,Inbox: One database transaction
    Save->>Catalog: Resolve and validate classification
    Save->>Legacy: Write confirmed financial row
    Legacy->>Ledger: Synchronize unified transaction + map
    Save->>Inbox: Mark durable item SAVED and persist move intent
    Save->>Legacy: Remove compatibility pending row
  end
  Save->>Worker: Hint already-committed external-sync job
  Save-->>UI: Return saved transaction
  UI->>UI: Refresh pending, ledger, totals, and reports

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
  participant Selector as ComparingLedgerReportQuery
  participant Unified as Unified ledger provider
  participant Legacy as Legacy aggregate provider
  participant Policy as ReportPolicyResolver

  User->>UI: Open report or change filters
  UI->>API: GET /api/reports/cashflow or /schedule-e
  API->>Service: Request period + owner/activity filters
  Service->>Selector: Query immutable aggregate totals
  alt UNIFIED mode
    Selector->>Unified: Aggregate active financial_transactions
    Unified-->>Selector: Totals by activity/category
  else LEGACY mode
    Selector->>Legacy: Aggregate incomes and expenses
    Legacy-->>Selector: Compatibility totals
  else COMPARE mode
    Selector->>Legacy: Aggregate compatibility rows
    Selector->>Unified: Aggregate unified rows
    Selector->>Selector: Require exact grouping and monetary parity
    Selector-->>Service: Legacy-compatible totals
  end
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
  Shadow->>Shadow: Run Flyway and integrity verification
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

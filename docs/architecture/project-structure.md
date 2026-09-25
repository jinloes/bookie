# Project Structure

[← Architecture index](../../ARCHITECTURE.md)

## Project Structure

```
backend/src/main/java/com/bookie/
  BookieApplication.java  The only class in the root package; every sub-package is a module
  catalog/
    api/                  Shared catalog transport values
    activity/
      api/             Activity REST contract and transport DTOs
      application/     ActivityCatalog use cases and property lookup port
      domain/          FinancialActivity, ActivityType, TaxTreatment
      infrastructure/  JPA store and property lifecycle adapter
    category/
      api/             Neutral-category transport values
      application/     CategoryCatalog (neutral categories and legacy-key aliases)
      domain/          Neutral categories, aliases, and TransactionDirection
      infrastructure/  Neutral category repositories
    classification/
      api/             /api/counterparties/keywords and /api/properties/keywords
      application/     ClassificationHistory port and history hints
      domain/          Keyword and counterparty/property history entities, ExpenseCategory
      infrastructure/  History repositories and JpaClassificationHistory
    counterparty/
      api/             /api/counterparties transport contract
      application/     CounterpartyCatalog use cases and lifecycle ports
      domain/          Counterparty and CounterpartyType
      infrastructure/  JPA store over the counterparties* tables
    financialcategory/
      api/             /api/financial-categories and FinancialCategoryMapper
      application/     FinancialCategoryService and FinancialCategoryStore port
      domain/          FinancialCategory (profile-specific category rows)
      infrastructure/  JPA store, repository, and JpaCategoryCatalog
    household/
      api/             Household-member REST contract and transport DTOs
      application/     HouseholdCatalog use cases and persistence port
      domain/          HouseholdMember
      infrastructure/  JPA store
    property/
      api/             Property REST contract and transport DTOs
      application/     PropertyCatalog and lossless lifecycle orchestration
      domain/          Property and PropertyType
      infrastructure/  Property JPA store
    reportpolicy/
      application/     Effective policy resolution
      domain/          Reporting profiles, assignments, and category mappings
      infrastructure/  JPA resolver, assignment writer, and overlap constraint
  ledger/
    api/             /api/v2/transactions transport contract
    application/     Transaction commands, report/query ports, invariants, lifecycle ports, and
                     the TransactionRecorded event
    domain/          FinancialTransaction, attachments, import references, ExpenseSource
    infrastructure/  JPA stores, unified report query, and catalog reference adapters
  reporting/
    api/             /api/reports transport contract and report DTOs
    application/     Cashflow and Schedule E use cases over ledger/report-policy ports
    domain/          Immutable report results
  intake/
    api/             /api/v2/inbox: list, save, dismiss, reparse, SSE, Venmo import, job retry
    application/     Inbox commands/queries, durable jobs, JobRunr worker, kickoff events and
                     listener, and persistence ports
    domain/          Inbox items, artifacts, background jobs, and states
    extraction/      AI proposals (/api/agent), email parsing, and deterministic classification
    sources/         Outlook, receipt, and Venmo intake; inbox save/dismiss; job dispatch;
                     receipt filing listener and event publication resubmitter
    infrastructure/  JPA stores, raw H2 JobRunr storage, catalog reference adapters, lifecycle
  integrations/
    outlook/      Microsoft Graph mail and MSAL adapters, immutable message identities
    onedrive/     Typed OneDrive storage port and Graph adapter
    llm/          Provider-neutral LLM gateway plus Copilot and Spring AI adapters
    documents/    Document text extraction port and PDF/OCR adapter
    venmo/        Deterministic Venmo statement input port
    receipts/     Deterministic, checksum-aware receipt file port
  config/       Web, async, loopback host, request-correlation, Clock, error envelope, SpaFilter
  datalifecycle/
    backup/     OneDrive backups and restore entry points
    migration/  Versioned integrity manifests and lossless reconciliation
    restore/    Shadow staging, durable restore journal, activation, rollback
  util/         Small shared parsing helpers (dates, account numbers)
backend/src/main/resources/
  application.properties
frontend/
  src/          React source
  src-tauri/    Tauri desktop wrapper (Rust)
    src/lib.rs  Backend lifecycle + window management
    tauri.conf.json
  vite.config.js  Builds to frontend/dist (consumed by Tauri)
diagrams/
  architecture.md       Mermaid runtime, module, and intake architecture
  erd.md                Mermaid entity-relationship diagrams by bounded context
  sequence-diagrams.md  Mermaid sequence diagrams for major end-to-end flows
```

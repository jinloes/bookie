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
      application/     CategoryCatalog, CategoryService, and CategoryStore port
      domain/          NeutralCategory and TransactionDirection
      infrastructure/  NeutralCategoryRepository
    classification/
      api/             /api/counterparties/keywords and /api/properties/keywords
      application/     ClassificationHistory port and history hints
      domain/          Keyword and counterparty/property history entities
      infrastructure/  History repositories and JpaClassificationHistory
    counterparty/
      api/             /api/counterparties transport contract
      application/     CounterpartyCatalog use cases and CounterpartyDeleting event
      domain/          Counterparty and CounterpartyType
      infrastructure/  JPA store over the counterparties* tables
    financialcategory/
      api/             /api/financial-categories compatibility contract
      application/     FinancialCategoryService policy facade over NeutralCategory + report policy
    household/
      api/             Household-member REST contract and transport DTOs
      application/     HouseholdCatalog use cases and persistence port
      domain/          HouseholdMember
      infrastructure/  JPA store
    property/
      api/             Property REST contract and transport DTOs
      application/     PropertyCatalog, PropertyDeleting event, and lossless lifecycle orchestration
      domain/          Property and PropertyType
      infrastructure/  PropertyRepository implementing PropertyStore
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
    api/             /api/v2/inbox: list, save, dismiss, reparse, SSE, Venmo and bank imports,
                     job retry
    application/     Inbox commands/queries, durable jobs, outbox poller worker, kickoff events and
                     listener, and persistence ports
    domain/          Inbox items, artifacts, background jobs, and states
    extraction/      AI proposals (/api/agent), email parsing, and deterministic classification
    sources/         Outlook, receipt, Venmo, and bank statement intake; inbox save/dismiss;
                     job dispatch; receipt filing listener
    infrastructure/  JPA stores, intake worker configuration, on-demand drainer, reference adapters
  integrations/
    outlook/      Microsoft Graph mail and MSAL adapters, immutable message identities
    onedrive/     Typed OneDrive storage port and Graph adapter
    llm/          Provider-neutral LLM gateway and GitHub Copilot SDK adapter
    documents/    Document text extraction port and PDF/OCR adapter
    venmo/        Deterministic Venmo statement input port
    bank/         Deterministic OFX/QFX bank statement input port
    receipts/     Deterministic, checksum-aware receipt file port
  config/       Web, async, loopback host, request-correlation, Clock, error envelope, DB vendor
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

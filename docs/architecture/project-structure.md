# Project Structure

[← Architecture index](../../ARCHITECTURE.md)

## Project Structure

```
backend/src/main/java/com/bookie/
  catalog/
    api/                  Shared catalog transport values
    activity/
      api/             Existing activity REST contract and transport DTOs
      application/     ActivityCatalog use cases and property lookup port
      domain/          FinancialActivity, ActivityType, TaxTreatment
      infrastructure/  JPA store and property lifecycle adapter
    category/
      application/     Legacy-to-neutral category catalog
      domain/          Neutral categories and explicit legacy aliases
      infrastructure/  Module-owned neutral category repositories
    classification/
      api/             Legacy history routes preserved at their existing paths
      application/     ClassificationHistory compatibility port
      infrastructure/  Legacy history repositories and adapter
    counterparty/
      api/             Legacy /api/payers transport contract
      application/     CounterpartyCatalog use cases and lifecycle ports
      domain/          Counterparty and CounterpartyType
      infrastructure/  Legacy payer store plus normalized compatibility mirror
    household/
      api/             Existing household-member REST contract and transport DTOs
      application/     HouseholdCatalog use cases and persistence port
      domain/          HouseholdMember
      infrastructure/  JPA store
    property/
      api/             Existing property REST contract and transport DTOs
      application/     PropertyCatalog and lossless lifecycle orchestration
      domain/          Property and PropertyType
      infrastructure/  Property JPA store
    reportpolicy/
      application/     Effective policy resolution and comparison modes
      domain/          Reporting profiles, assignments, and category mappings
      infrastructure/  JPA resolver, assignment writer, and overlap constraint
  ledger/
    api/             Unified `/api/v2/transactions` transport contract
    application/     Transaction commands, report/query ports, invariants, and lifecycle ports
    compatibility/   Same-transaction legacy writes and legacy report/read adapters
    domain/          Unified transaction, normalized metadata, and durable legacy map
    infrastructure/  JPA stores, unified report provider, and catalog persistence adapters
  reporting/
    api/             Existing `/api/reports` transport contract and module-local report DTOs
    application/     Cashflow and Schedule E use cases over ledger/report-policy ports
    domain/          Immutable report results
    infrastructure/  Legacy/unified comparison and rollout selection
  intake/
    api/             Durable inbox/job inspection and explicit retry transport
    application/     Transactional intent, engine callbacks/projections, and persistence ports
    compatibility/   Same-transaction legacy pending synchronization and job dispatch
    domain/          Inbox items, artifacts, background jobs, states, and legacy maps
    infrastructure/  JPA stores, raw H2 JobRunr storage, server-only candidate gate and lifecycle
  integrations/
    outlook/      Microsoft Graph mail and MSAL adapters, immutable message identities
    onedrive/     Typed OneDrive storage port and Graph adapter
    llm/          Provider-neutral LLM gateway plus Copilot and Spring AI adapters
    documents/    Document text extraction port and PDF/OCR adapter
    venmo/        Deterministic Venmo statement input port
    receipts/     Deterministic, checksum-aware receipt file port
  compatibility/
    api/         Shared legacy response mappers and error envelope
    catalog/     Cross-capability catalog/ledger/intake composition adapters
    intake/      Legacy pending synchronization and integration job dispatch
  config/       Web, async, loopback host, request-correlation, and shared Clock configuration
  controller/   Retained legacy REST entry points + SpaFilter
  datalifecycle/
    migration/  Versioned integrity manifests and lossless reconciliation
    restore/    Shadow staging, durable restore journal, activation, rollback
  model/        JPA entities + domain enums
  repository/   Legacy ledger/intake repositories and compatibility port adapters
  service/      Business logic
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

# Bookie Architecture Diagrams

These Mermaid diagrams show the runtime topology, modular-monolith boundaries, and durable intake
execution model. Generated PNG previews are included for Markdown viewers that do not execute
Mermaid, including IntelliJ installations without Mermaid support. See
[`../ARCHITECTURE.md`](../ARCHITECTURE.md) for the detailed contracts. The PNG previews may lag the
Mermaid source; the source is authoritative.

## Runtime Architecture

![Bookie runtime architecture](rendered/architecture-runtime.png)

<details>
<summary>Mermaid source</summary>

```mermaid
flowchart TB
  subgraph Desktop["Tauri 2 desktop application"]
    Host["Rust host<br/>single instance, tray, updater"]
    WebView["React 19 + Vite WebView"]
    Query["React Router + TanStack Query<br/>generated OpenAPI client"]
    Host -->|renders| WebView
    WebView --> Query
  end

  subgraph Backend["Spring Boot 4.1 modular monolith · Java 21 · :48763"]
    API["REST controllers<br/>/api catalog, reports, intake sources<br/>/api/v2 transactions + inbox"]
    Catalog["Catalog<br/>household, activity, property, counterparty,<br/>neutral category, report policy"]
    Ledger["Ledger<br/>financial transactions"]
    Intake["Intake<br/>inbox, extraction, sources, outbox poller"]
    Reporting["Reporting<br/>cashflow + Schedule E"]
    Lifecycle["Data lifecycle<br/>Flyway, portable backup, shadow restore"]
    Integrations["Typed integration ports"]

    API --> Catalog
    API --> Ledger
    API --> Intake
    API --> Reporting
    API --> Lifecycle
    Ledger --> Catalog
    Intake --> Ledger
    Intake --> Catalog
    Reporting --> Ledger
    Reporting --> Catalog
    Intake --> Integrations
    Lifecycle --> Integrations
  end

  subgraph Data["Database & local restore state"]
    H2[("Live H2 or Azure SQL database<br/>application + background_jobs tables")]
    Restore["Staged H2 shadow + retained rollback<br/>journals + integrity manifests"]
  end

  subgraph External["External services"]
    Graph["Microsoft Graph<br/>Outlook + OneDrive"]
    AI["LLM provider<br/>GitHub Copilot SDK"]
  end

  Query -->|HTTP REST /api/**| API
  API -->|JSON + SSE| Query
  Host -.->|spawn, health check, supervise| Backend
  Backend -->|JPA / Hibernate| H2
  Lifecycle -->|stage, verify, atomic rotate| Restore
  Restore --> H2
  Integrations -->|OAuth2 / MSAL| Graph
  Integrations -->|local login or token| AI
```

</details>

In development, Vite serves the UI on port `5173` and proxies `/api` to port `48763`. In packaged
builds, Tauri serves the frontend assets, owns the backend process, waits for readiness before
showing the window, and performs bounded restart supervision.

## Module Dependencies

![Bookie module dependencies](rendered/architecture-capability-dependencies.png)

<details>
<summary>Mermaid source</summary>

```mermaid
flowchart LR
  Catalog["catalog<br/>named interfaces per slice"]
  Ledger["ledger<br/>application, domain"]
  Intake["intake"]
  Reporting["reporting"]
  DataLifecycle["datalifecycle<br/>restore"]
  Integrations["integrations<br/>typed provider ports"]
  Util["util"]
  Config["config"]
  Providers["Graph, MSAL, LLM, PDF/OCR,<br/>CSV and receipt adapters"]

  Catalog --> Util
  Ledger -->|"catalog :: *"| Catalog
  Intake -->|"catalog :: *"| Catalog
  Intake -->|"ledger :: *"| Ledger
  Intake --> Integrations
  Intake --> Util
  Reporting -->|"catalog :: *"| Catalog
  Reporting -->|"ledger :: *"| Ledger
  DataLifecycle --> Integrations
  Integrations --> Providers
```

</details>

The arrows are the `@ApplicationModule(allowedDependencies = …)` declarations; `module :: *`
admits only that module's named interfaces. `config` and `util` depend on no other module, and
provider SDK types remain inside integration adapters.

## Durable Intake Execution

![Bookie durable intake execution](rendered/architecture-durable-intake.png)

<details>
<summary>Mermaid source</summary>

```mermaid
flowchart TB
  Save["Explicit reviewed Save / Intake create<br/>ledger row + inbox state + background_jobs row<br/>one database transaction"]
  Hint["InboxItemSaved / InboxItemQueued / BackgroundJobQueued<br/>post-commit async kickoff; may be lost"]
  Drainer["OnDemandIntakeDrainer / 5-second poller<br/>continuous on H2, on-demand on sqlserver"]
  Claim["BackgroundJobService.claimDue<br/>claim allowed due or expired-lease rows<br/>stamp lease_owner + lease_expires_at"]
  Worker["DurableBackgroundJobWorker<br/>batch TRANSLATE_OUTLOOK_ID + singleton execution"]
  Providers["Graph / OneDrive / parser ports<br/>at-least-once effects with replay guards"]
  Finish["BackgroundJobService.finish<br/>verify lease_owner, mark COMPLETED/TERMINAL<br/>or schedule 3^n retry"]

  Save --> Hint
  Hint --> Drainer
  Drainer --> Claim
  Claim --> Worker
  Worker -->|outside database locks| Providers
  Providers --> Finish
  Finish -->|updates| Save
```

</details>

Disallowed job types remain `AVAILABLE` without consuming attempts. Expired `LEASED` rows are
reclaimed automatically on subsequent polls, and `finish` verifies the active `lease_owner` token so
stale attempts cannot overwrite a reset or superseded job. Remote effects are at-least-once and rely
on immutable identity, checksum, destination-year, and idempotency guards.

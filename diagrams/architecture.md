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

  subgraph Backend["Spring Boot 3.5 modular monolith · Java 21 · :48763"]
    API["REST controllers<br/>/api catalog, reports, intake sources<br/>/api/v2 transactions + inbox"]
    Catalog["Catalog<br/>household, activity, property, counterparty,<br/>category, financial category, report policy"]
    Ledger["Ledger<br/>financial transactions"]
    Intake["Intake<br/>inbox, extraction, sources, durable jobs"]
    Reporting["Reporting<br/>cashflow + Schedule E"]
    Lifecycle["Data lifecycle<br/>Flyway, backup, shadow restore"]
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

  subgraph Data["Local data"]
    H2[("Live H2 database<br/>application + JobRunr tables")]
    Restore["Staged shadow + retained rollback<br/>journals + integrity manifests"]
  end

  subgraph External["External services"]
    Graph["Microsoft Graph<br/>Outlook + OneDrive"]
    AI["LLM provider<br/>Copilot SDK or Spring AI"]
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
  Save["Explicit reviewed Save<br/>ledger row + inbox state + intent<br/>one database transaction"]
  Hint["InboxItemSaved / InboxItemQueued event<br/>recorded in EVENT_PUBLICATION in the same transaction<br/>post-commit kickoff; may be lost"]
  Relay["Relay on Ready + periodic poll<br/>bind UUID, publish, project<br/>never calls providers"]
  Raw[("Raw H2 JobRunr StorageProvider<br/>scheduler, relay, inspection, restore")]
  Gate["Server-only candidate gate<br/>filters disallowed job types before claim"]
  Engine["JobRunr OSS 8.8.1<br/>one worker, 5-second poll<br/>native retries + orphan recovery"]
  Callback["executeV1(type, JobContext)<br/>validate UUID + generation<br/>persist start and each outcome"]
  Providers["Graph / OneDrive / parser ports<br/>at-least-once effects with replay guards"]

  Save --> Hint
  Hint --> Relay
  Relay -->|enqueue or repair missing delivery| Raw
  Engine -->|candidate reads and native claims| Gate
  Gate -->|allowed candidates; all writes forwarded| Raw
  Engine -->|native performer callback| Callback
  Callback -->|outside database locks| Providers
  Callback -->|project business outcome| Save
```

</details>

Disallowed work remains in its real JobRunr state without consuming retries. The raw provider stays
visible to publication, inspection, and restore; only the worker server receives the candidate
filter. Remote effects are at-least-once and rely on immutable identity, checksum, destination-year,
and idempotency guards.

# Module Boundaries

[← Architecture index](../../ARCHITECTURE.md)

## Application Modules

Every direct sub-package of `com.bookie` is a Spring Modulith application module. Only
`BookieApplication` lives in `com.bookie` itself. Each module's `package-info.java` declares
`@ApplicationModule(allowedDependencies = …)`:

| Module | Allowed dependencies |
| --- | --- |
| `catalog` | `util` |
| `ledger` | `catalog :: *` |
| `intake` | `catalog :: *`, `ledger :: *`, `integrations`, `integrations :: *`, `util` |
| `reporting` | `catalog :: *`, `ledger :: *` |
| `datalifecycle` | `integrations`, `integrations :: *` |
| `integrations`, `config`, `util` | none |

In Spring Modulith 2.1, a bare module name admits only that module's base package, `module :: *`
admits only its explicitly declared named interfaces, and `{}` admits nothing. A module exposes a
package to others by annotating that package's `package-info.java` with `@NamedInterface`; do not
widen `allowedDependencies` to reach an internal package.

Named interfaces:

- `catalog`: the `api`, `application`, and `domain` packages of `activity` and `category`;
  `application` of `classification` and `financialcategory`; `application` and `domain` of
  `counterparty`, `property`, and `reportpolicy`; and `household.domain` (reached through
  `FinancialActivity.getOwner()`).
- `ledger`: `application` and `domain`.
- `datalifecycle`: `restore`.
- `integrations`: `bank`, `documents`, `llm`, `onedrive`, `outlook`, `receipts`, and `venmo`.

`ModuleBoundaryTest` runs `ApplicationModules.of(BookieApplication.class).verify()`: it fails on any
dependency that its module's `allowedDependencies` and the target's named interfaces do not admit,
and on cycles between modules. It is the module-boundary check; the ArchUnit rules below cover the
layers and slices inside a module. `ModuleDocumentationTest` writes the Spring Modulith
documentation (PlantUML component and module diagrams plus module canvases) to
`backend/build/spring-modulith-docs`.

## Events

Events are plain Spring application events published inside the committing transaction. Durable
work is never carried by an event: it is a `background_jobs` row written in the same transaction and
run by the `DurableBackgroundJobWorker` outbox poller.

- `InboxItemSaved`, `InboxItemQueued` and `BackgroundJobQueued` (intake-internal) kick the outbox
  poller from `@Async` `@TransactionalEventListener(AFTER_COMMIT)` listeners; the scheduled poller
  recovers any lost kick.
- `TransactionRecorded` (ledger → intake) schedules a manually entered receipt's `MOVE_RECEIPT` job
  inside the ledger's create transaction via intake's synchronous `ReceiptFilingListener`.
- `PropertyDeleting` and `CounterpartyDeleting` (catalog → catalog/ledger/intake) are synchronous
  `@EventListener` notifications inside `PropertyService.delete` and `CounterpartyService.delete`:
  classification history, ledger, and inbox adapters reassign or detach their references and fail
  closed inside the deleting transaction if any references remain.

Everything else between modules stays a synchronous call inside the caller's transaction.

## Layer and Slice Rules

`ModuleBoundaryTest` (ArchUnit) enforces the rules inside modules:

- Catalog slices (`activity`, `category`, `classification`, `counterparty`, `financialcategory`,
  `household`, `property`, `reportpolicy`), ledger, intake, reporting, and integration adapters are
  free of slice cycles.
- Domain code does not use its module's application, API, or infrastructure code; application code
  does not use API or infrastructure code; API code does not use infrastructure code. Ledger,
  intake, reporting, and catalog infrastructure is internal to its module.
- `@RestController` public methods do not return or embed JPA `@Entity` types.
- Intake `application` and `domain` do not use the intake feature packages `extraction` (AI
  proposal and parsing) and `sources` (Outlook, receipts, Venmo, bank statements, inbox commands);
  `extraction` does not use `sources`.
- Provider SDKs (Microsoft Graph, Copilot, PDFBox, Jsoup, Commons CSV) are confined to
  `integrations`.

## Catalog Slices

- Household members, financial activities, properties, counterparties, classification history,
  neutral categories, the financial-category policy facade, and report policy are catalog slices.
  Code outside a slice depends on its application ports (`HouseholdCatalog`, `ActivityCatalog`,
  `PropertyCatalog`, `CounterpartyCatalog`, `CategoryCatalog`, `ClassificationHistory`,
  `FinancialCategoryService`), never on a repository.
- Activity depends on household and property domain identities; household and property core do not
  reverse that dependency. Property lifecycle invokes an activity-owned adapter through
  `PropertyActivityLifecycle`.
- Property and counterparty deletion publish `PropertyDeleting` and `CounterpartyDeleting` inside
  the deleting transaction. Synchronous `@EventListener` adapters in classification history, ledger
  (`LedgerPropertyReferences`, `LedgerCounterpartyReferences`), and intake
  (`InboxPropertyReferences`, `InboxCounterpartyReferences`) reassign or detach references and fail
  closed if any references remain.
- Classification history repositories are reachable only through `ClassificationHistory` or its
  `PropertyDeleting` / `CounterpartyDeleting` listeners.
- `catalog.financialcategory` is a stateless policy facade over `CategoryCatalog`,
  `ActivityCatalog`, and `ReportPolicyResolver`.

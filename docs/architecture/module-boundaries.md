# Module Boundaries

[← Architecture index](../../ARCHITECTURE.md)

## Catalog Module Boundaries

- Household members, financial activities, properties, counterparties, classification history,
  neutral categories, and report policy are catalog slices in the modular monolith. Existing HTTP
  paths, JSON fields, legacy table names, legacy category IDs, and stored enum values are unchanged.
- Module APIs depend on application interfaces, application code depends on domain types and
  persistence/lookup ports, and infrastructure implements those ports. Code outside a catalog
  slice depends on `HouseholdCatalog`, `ActivityCatalog`, `PropertyCatalog`,
  `CounterpartyCatalog`, or `ClassificationHistory`, never a catalog repository.
- Activity depends on household and property domain identities; household and property core do not
  reverse that dependency. Property lifecycle invokes an activity-owned adapter through
  `PropertyActivityLifecycle`. Legacy ledger and intake repositories implement explicit
  reassign/detach/reference-count ports rather than being injected into `PropertyService`.
- Classification history repositories are reachable only through `ClassificationHistory` or the
  property/counterparty cleanup ports. The legacy keyword-history HTTP paths are served by the
  classification adapter without making property or counterparty APIs depend on its infrastructure.
- `ModuleBoundaryTest` uses ArchUnit to reject catalog cycles, outer-layer imports, infrastructure
  leakage, and reversed `api -> application -> domain` dependencies.

## Module Dependency and Compatibility Boundary

- The capability dependency graph is directional: catalog has no capability dependency; ledger may
  use catalog; intake may use ledger and catalog; reporting may use ledger and catalog. Capability
  cycles and cross-module repository access are rejected by `ModuleBoundaryTest`.
- A capability's API and application layers expose module-owned values and ports, not legacy JPA
  entities or repositories. Ledger, intake, and reporting infrastructure is internal to its owning
  capability.
- Module-local `compatibility` packages adapt retained endpoint and persistence shapes. The
  top-level `com.bookie.compatibility` package is the composition boundary for adapters that must
  coordinate more than one capability or translate integration failures into application-owned
  outcomes. Module-local compatibility adapters may use this shared boundary; normal capability
  code cannot import it.
- Root `controller`, `service`, `model`, and `repository` packages remain only where old HTTP or
  storage contracts require them. Compatibility readers and writers preserve those contracts for
  rollback; they are not new application entry points.
- Compatibility code can be retired only in a separately approved change after its legacy
  endpoint/table contract is no longer needed and parity has been proved. Removing legacy tables,
  endpoints, or records is never part of a package-boundary cleanup.

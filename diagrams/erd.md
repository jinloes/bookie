# Bookie Entity-Relationship Diagrams

The schema is split into bounded views so the Mermaid diagrams remain readable. These diagrams show
application-owned tables and their important columns and relationships as of Flyway V25 (V20 retired
the legacy expense, income, pending and payer-mirror tables; V25 retired `financial_categories`,
`legacy_category_map`, legacy category-history tables, and the embedded JobRunr tables). Generated
PNG previews are included for Markdown viewers that do not execute Mermaid.

## Catalog and Report Policy

![Catalog and report policy ERD](rendered/erd-catalog-report-policy.png)

<details>
<summary>Mermaid source</summary>

```mermaid
erDiagram
  HOUSEHOLD_MEMBERS {
    BIGINT id PK
    VARCHAR name UK
    BOOLEAN active
    VARCHAR system_key UK
  }

  PROPERTIES {
    BIGINT id PK
    VARCHAR name
    VARCHAR address
    VARCHAR type
    VARCHAR notes
  }

  PROPERTY_ACCOUNTS {
    BIGINT property_id FK
    VARCHAR account_number
  }

  FINANCIAL_ACTIVITIES {
    BIGINT id PK
    VARCHAR name UK
    VARCHAR activity_type
    VARCHAR tax_treatment
    BIGINT owner_id FK
    BIGINT property_id FK
    BOOLEAN active
    VARCHAR system_key UK
  }

  NEUTRAL_CATEGORIES {
    BIGINT id PK
    VARCHAR category_key UK
    VARCHAR label
    VARCHAR direction
    BOOLEAN active
    BOOLEAN system
  }

  REPORTING_PROFILES {
    BIGINT id PK
    VARCHAR profile_key UK
    VARCHAR label
    BOOLEAN active
  }

  ACTIVITY_REPORTING_PROFILE_ASSIGNMENTS {
    BIGINT id PK
    BIGINT activity_id FK
    BIGINT reporting_profile_id FK
    DATE effective_from
    DATE effective_to
  }

  CATEGORY_REPORTING_MAPPINGS {
    BIGINT id PK
    BIGINT neutral_category_id FK
    BIGINT reporting_profile_id FK
    VARCHAR report_line
    BOOLEAN active
  }

  COUNTERPARTIES {
    BIGINT id PK
    VARCHAR name
    VARCHAR type
  }

  COUNTERPARTY_ALIASES {
    BIGINT counterparty_id FK
    VARCHAR alias
  }

  COUNTERPARTY_ACCOUNTS {
    BIGINT counterparty_id FK
    VARCHAR account_number
  }

  HOUSEHOLD_MEMBERS ||--o{ FINANCIAL_ACTIVITIES : owns
  PROPERTIES o|--o| FINANCIAL_ACTIVITIES : contextualizes
  PROPERTIES ||--o{ PROPERTY_ACCOUNTS : has
  FINANCIAL_ACTIVITIES ||--o{ ACTIVITY_REPORTING_PROFILE_ASSIGNMENTS : receives
  REPORTING_PROFILES ||--o{ ACTIVITY_REPORTING_PROFILE_ASSIGNMENTS : assigns
  NEUTRAL_CATEGORIES ||--o{ CATEGORY_REPORTING_MAPPINGS : classifies
  REPORTING_PROFILES ||--o{ CATEGORY_REPORTING_MAPPINGS : scopes
  COUNTERPARTIES ||--o{ COUNTERPARTY_ALIASES : has
  COUNTERPARTIES ||--o{ COUNTERPARTY_ACCOUNTS : has
```

</details>

V20 renamed `payers`, `payer_aliases` and `payer_accounts` to `counterparties`,
`counterparty_aliases` and `counterparty_accounts` (column `payer_id` → `counterparty_id`) after
dropping the V12 mirror, so counterparty IDs and native identity generation are unchanged. V25
retired `financial_categories` and `legacy_category_map` so `neutral_categories` and
`category_reporting_mappings` are the sole category and report-line tables.

## Unified Ledger

![Unified ledger ERD](rendered/erd-unified-ledger.png)

<details>
<summary>Mermaid source</summary>

```mermaid
erDiagram
  FINANCIAL_ACTIVITIES {
    BIGINT id PK
  }

  NEUTRAL_CATEGORIES {
    BIGINT id PK
  }

  COUNTERPARTIES {
    BIGINT id PK
  }

  FINANCIAL_TRANSACTIONS {
    BIGINT id PK
    NUMERIC amount
    VARCHAR direction
    DATE transaction_date
    VARCHAR description
    BIGINT activity_id FK
    BIGINT neutral_category_id FK
    BIGINT counterparty_id FK
    TIMESTAMP created_at
    TIMESTAMP updated_at
    BIGINT version
    TIMESTAMP deleted_at
  }

  TRANSACTION_ATTACHMENTS {
    BIGINT id PK
    BIGINT transaction_id FK
    VARCHAR storage_provider
    VARCHAR external_id
    VARCHAR file_name
    VARCHAR sha256
  }

  TRANSACTION_IMPORT_REFERENCES {
    BIGINT id PK
    BIGINT transaction_id FK
    VARCHAR origin
    VARCHAR external_id
    VARCHAR source_label
    VARCHAR parent_external_id
  }

  FINANCIAL_ACTIVITIES ||--o{ FINANCIAL_TRANSACTIONS : classifies
  NEUTRAL_CATEGORIES ||--o{ FINANCIAL_TRANSACTIONS : categorizes
  COUNTERPARTIES o|--o{ FINANCIAL_TRANSACTIONS : identifies
  FINANCIAL_TRANSACTIONS ||--o{ TRANSACTION_ATTACHMENTS : has
  FINANCIAL_TRANSACTIONS ||--o{ TRANSACTION_IMPORT_REFERENCES : imports
```

</details>

`financial_transactions` is the only financial record table. Deletes tombstone the row
(`deleted_at`). Historically, V13 copied the legacy `incomes` and `expenses` rows into it through
`legacy_transaction_map`; V20 verified exact parity and dropped all three.

## Durable Intake

![Durable intake ERD](rendered/erd-durable-intake.png)

<details>
<summary>Mermaid source</summary>

```mermaid
erDiagram
  FINANCIAL_TRANSACTIONS {
    BIGINT id PK
  }

  FINANCIAL_ACTIVITIES {
    BIGINT id PK
  }

  NEUTRAL_CATEGORIES {
    BIGINT id PK
  }

  COUNTERPARTIES {
    BIGINT id PK
  }

  INBOX_ITEMS {
    BIGINT id PK
    VARCHAR origin
    VARCHAR legacy_source_id
    VARCHAR immutable_source_id
    VARCHAR state
    VARCHAR external_sync_state
    VARCHAR proposed_direction
    NUMERIC proposed_amount
    DATE proposed_date
    BIGINT selected_activity_id FK
    BIGINT neutral_category_id FK
    BIGINT selected_counterparty_id FK
    BIGINT financial_transaction_id FK
    BIGINT version
  }

  INBOX_ARTIFACTS {
    BIGINT id PK
    BIGINT inbox_item_id FK
    VARCHAR type
    VARCHAR text_value
    VARCHAR external_id
    VARCHAR file_name
    VARCHAR sha256
  }

  INBOX_IDENTITY_EVIDENCE {
    BIGINT inbox_item_id PK,FK
    INTEGER snapshot_order PK
    VARCHAR target_kind
    INTEGER candidate_rank
    BIGINT target_id
    VARCHAR evidence_type
    VARCHAR evidence_strength
    INTEGER occurrences
  }

  BACKGROUND_JOBS {
    BIGINT id PK
    BIGINT inbox_item_id FK
    VARCHAR source_external_id
    INTEGER target_year
    VARCHAR type
    VARCHAR idempotency_key UK
    VARCHAR state
    INTEGER attempts
    INTEGER max_attempts
    TIMESTAMP available_at
    VARCHAR lease_owner
    TIMESTAMP lease_expires_at
    BIGINT version
  }

  FINANCIAL_TRANSACTIONS o|--o| INBOX_ITEMS : finalized_as
  FINANCIAL_ACTIVITIES o|--o{ INBOX_ITEMS : selected_for
  NEUTRAL_CATEGORIES o|--o{ INBOX_ITEMS : proposes
  COUNTERPARTIES o|--o{ INBOX_ITEMS : selected_counterparty
  INBOX_ITEMS ||--o{ INBOX_ARTIFACTS : has
  INBOX_ITEMS ||--o{ INBOX_IDENTITY_EVIDENCE : explains
  INBOX_ITEMS o|--o{ BACKGROUND_JOBS : executes
```

</details>

`inbox_items` is the only intake table. `neutral_category_id` references `neutral_categories` and
`selected_counterparty_id` references `counterparties`. `background_jobs` is the single durable
outbox table: each job references either `inbox_item_id` or `source_external_id` (enforced by
`ck_background_jobs_subject`), and active claims stamp `lease_owner` and `lease_expires_at`. V22
dropped the V21 `EVENT_PUBLICATION` registry, and V25 dropped `background_jobs.execution_*` and the
`jobrunr_*` tables.

## Classification and Integration Settings

![Classification and integration settings ERD](rendered/erd-classification-settings.png)

<details>
<summary>Mermaid source</summary>

```mermaid
erDiagram
  PROPERTIES {
    BIGINT id PK
  }

  COUNTERPARTIES {
    BIGINT id PK
  }

  FINANCIAL_ACTIVITIES {
    BIGINT id PK
  }

  NEUTRAL_CATEGORIES {
    BIGINT id PK
  }

  OUTLOOK_SETTINGS {
    BIGINT id PK
    VARCHAR receipts_folder_base
    BOOLEAN auto_move_enabled
    VARCHAR move_destination_folder_id
  }

  OUTLOOK_SETTINGS_FOLDER {
    BIGINT settings_id FK
    VARCHAR folder_id
    BOOLEAN expand_subfolders
    BIGINT activity_id FK
  }

  OUTLOOK_SETTINGS_RECEIPTS_IMPORT_FOLDER {
    BIGINT settings_id FK
    VARCHAR folder_path
  }

  OUTLOOK_TOKENS {
    BIGINT id PK
    VARCHAR cache_data
  }

  RECEIPT_HASHES {
    BIGINT id PK
    VARCHAR drive_item_id
    VARCHAR sha256 UK
    TIMESTAMP uploaded_at
  }

  EMAIL_KEYWORD_CLASSIFICATION_HISTORY {
    BIGINT id PK
    VARCHAR keyword
    BIGINT activity_id FK
    BIGINT neutral_category_id FK
    INTEGER occurrences
    BIGINT version
  }

  PAYER_PROPERTY_HISTORY {
    BIGINT id PK
    BIGINT payer_id FK
    BIGINT property_id FK
    INTEGER occurrences
  }

  EMAIL_KEYWORD_PROPERTY_HISTORY {
    BIGINT id PK
    VARCHAR keyword
    BIGINT property_id FK
    INTEGER occurrences
  }

  EMAIL_KEYWORD_PAYER_HISTORY {
    BIGINT id PK
    VARCHAR keyword
    BIGINT payer_id FK
    INTEGER occurrences
  }

  OUTLOOK_SETTINGS ||--o{ OUTLOOK_SETTINGS_FOLDER : watches
  OUTLOOK_SETTINGS ||--o{ OUTLOOK_SETTINGS_RECEIPTS_IMPORT_FOLDER : scans
  FINANCIAL_ACTIVITIES o|--o{ OUTLOOK_SETTINGS_FOLDER : configures
  FINANCIAL_ACTIVITIES ||--o{ EMAIL_KEYWORD_CLASSIFICATION_HISTORY : learned_for
  NEUTRAL_CATEGORIES ||--o{ EMAIL_KEYWORD_CLASSIFICATION_HISTORY : categorizes
  COUNTERPARTIES ||--o{ PAYER_PROPERTY_HISTORY : associates
  PROPERTIES ||--o{ PAYER_PROPERTY_HISTORY : associates
  PROPERTIES ||--o{ EMAIL_KEYWORD_PROPERTY_HISTORY : inferred_from
  COUNTERPARTIES ||--o{ EMAIL_KEYWORD_PAYER_HISTORY : inferred_from
```

</details>

The history tables keep their `payer_*` table and column names, but their foreign keys follow the
renamed `counterparties` table. V25 retired `payer_category_history` and
`email_keyword_category_history` and migrated `email_keyword_classification_history` directly to
`neutral_category_id`. `outlook_tokens` and `receipt_hashes` stand alone because their identities
belong to external systems rather than another local table.

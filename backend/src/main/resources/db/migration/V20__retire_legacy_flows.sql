-- Retire the legacy ledger, inbox and payer mirror tables. Every precheck runs before any DDL:
-- H2 DDL auto-commits, so a failed precheck must leave the V19 database untouched. Nothing is
-- repaired or merged; a database that is not a faithful dual-write of the unified model aborts.

-- P1: every legacy expense maps to an equal active unified transaction.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM expenses legacy
LEFT JOIN legacy_transaction_map legacy_map
  ON legacy_map.legacy_table = 'EXPENSES'
 AND legacy_map.legacy_id = legacy.id
LEFT JOIN financial_transactions transaction ON transaction.id = legacy_map.transaction_id
LEFT JOIN legacy_category_map category_map
  ON category_map.legacy_category_id = legacy.category_id
WHERE transaction.id IS NULL
   OR transaction.deleted_at IS NOT NULL
   OR transaction.direction <> 'EXPENSE'
   OR transaction.amount <> legacy.amount
   OR transaction.transaction_date <> legacy.date
   OR transaction.description IS DISTINCT FROM legacy.description
   OR transaction.activity_id IS DISTINCT FROM legacy.activity_id
   OR category_map.neutral_category_id IS NULL
   OR transaction.neutral_category_id <> category_map.neutral_category_id
   OR transaction.counterparty_id IS DISTINCT FROM legacy.payer_id;

-- P2: every legacy income maps to an equal active unified transaction.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM incomes legacy
LEFT JOIN legacy_transaction_map legacy_map
  ON legacy_map.legacy_table = 'INCOMES'
 AND legacy_map.legacy_id = legacy.id
LEFT JOIN financial_transactions transaction ON transaction.id = legacy_map.transaction_id
LEFT JOIN legacy_category_map category_map
  ON category_map.legacy_category_id = legacy.category_id
WHERE transaction.id IS NULL
   OR transaction.deleted_at IS NOT NULL
   OR transaction.direction <> 'INCOME'
   OR transaction.amount <> legacy.amount
   OR transaction.transaction_date <> legacy.date
   OR transaction.description IS DISTINCT FROM legacy.description
   OR transaction.activity_id IS DISTINCT FROM legacy.activity_id
   OR category_map.neutral_category_id IS NULL
   OR transaction.neutral_category_id <> category_map.neutral_category_id
   OR transaction.counterparty_id IS DISTINCT FROM legacy.payer_id;

-- P3: no legacy row was ever quarantined instead of migrated.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM ledger_migration_quarantine;

-- P4: every pending expense maps to an equal active inbox item.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM pending_expenses pending
LEFT JOIN legacy_inbox_map legacy_map
  ON legacy_map.legacy_table = 'PENDING_EXPENSES'
 AND legacy_map.legacy_id = pending.id
LEFT JOIN inbox_items item ON item.id = legacy_map.inbox_item_id
WHERE item.id IS NULL
   OR item.state IN ('SAVED', 'DISMISSED')
   OR item.proposed_amount IS DISTINCT FROM pending.amount
   OR item.proposed_date IS DISTINCT FROM pending.date
   OR item.proposed_description IS DISTINCT FROM pending.description
   OR item.legacy_activity_id IS DISTINCT FROM pending.activity_id
   OR item.legacy_category_id IS DISTINCT FROM pending.category_id;

-- P5: every pending income maps to an equal active inbox item.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM pending_incomes pending
LEFT JOIN legacy_inbox_map legacy_map
  ON legacy_map.legacy_table = 'PENDING_INCOMES'
 AND legacy_map.legacy_id = pending.id
LEFT JOIN inbox_items item ON item.id = legacy_map.inbox_item_id
WHERE item.id IS NULL
   OR item.state IN ('SAVED', 'DISMISSED')
   OR item.proposed_amount IS DISTINCT FROM pending.amount
   OR item.proposed_date IS DISTINCT FROM pending.date
   OR item.proposed_description IS DISTINCT FROM pending.description
   OR item.legacy_activity_id IS DISTINCT FROM pending.activity_id
   OR item.legacy_category_id IS DISTINCT FROM pending.category_id;

-- P6: the unrecognized aliases of every pending expense equal its inbox item's alias artifacts,
-- compared as multisets in both directions.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    (
        SELECT legacy_map.inbox_item_id, alias.alias AS alias_value, COUNT(*) AS occurrences
        FROM pending_expense_aliases alias
        JOIN legacy_inbox_map legacy_map
          ON legacy_map.legacy_table = 'PENDING_EXPENSES'
         AND legacy_map.legacy_id = alias.pending_expense_id
        GROUP BY legacy_map.inbox_item_id, alias.alias
        EXCEPT
        SELECT artifact.inbox_item_id, artifact.text_value, COUNT(*)
        FROM inbox_artifacts artifact
        WHERE artifact.type = 'UNRECOGNIZED_ALIAS'
        GROUP BY artifact.inbox_item_id, artifact.text_value
    )
    UNION ALL
    (
        SELECT artifact.inbox_item_id, artifact.text_value, COUNT(*)
        FROM inbox_artifacts artifact
        WHERE artifact.type = 'UNRECOGNIZED_ALIAS'
          AND EXISTS (
              SELECT 1
              FROM legacy_inbox_map legacy_map
              JOIN pending_expenses pending ON pending.id = legacy_map.legacy_id
              WHERE legacy_map.legacy_table = 'PENDING_EXPENSES'
                AND legacy_map.inbox_item_id = artifact.inbox_item_id
          )
        GROUP BY artifact.inbox_item_id, artifact.text_value
        EXCEPT
        SELECT legacy_map.inbox_item_id, alias.alias, COUNT(*)
        FROM pending_expense_aliases alias
        JOIN legacy_inbox_map legacy_map
          ON legacy_map.legacy_table = 'PENDING_EXPENSES'
         AND legacy_map.legacy_id = alias.pending_expense_id
        GROUP BY legacy_map.inbox_item_id, alias.alias
    )
) alias_drift;

-- P7: the counterparty mirror is an exact identity copy of the payer tables.
SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM payers) = (SELECT COUNT(*) FROM counterparties)
    AND (SELECT COUNT(*) FROM payers) = (SELECT COUNT(*) FROM legacy_payer_map)
    AND (SELECT COUNT(*) FROM payer_aliases) = (SELECT COUNT(*) FROM counterparty_aliases)
    AND (SELECT COUNT(*) FROM payer_accounts) = (SELECT COUNT(*) FROM counterparty_accounts)
THEN 1 ELSE 0 END;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM payers legacy
LEFT JOIN legacy_payer_map mapping ON mapping.payer_id = legacy.id
LEFT JOIN counterparties counterparty ON counterparty.id = legacy.id
WHERE mapping.payer_id IS NULL
   OR mapping.counterparty_id <> legacy.id
   OR counterparty.id IS NULL
   OR counterparty.name IS DISTINCT FROM legacy.name
   OR counterparty.type IS DISTINCT FROM CAST(legacy.type AS VARCHAR);

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    (
        SELECT payer_id AS owner_id, alias AS alias_value, COUNT(*) AS occurrences
        FROM payer_aliases
        GROUP BY payer_id, alias
        EXCEPT
        SELECT counterparty_id, alias, COUNT(*)
        FROM counterparty_aliases
        GROUP BY counterparty_id, alias
    )
    UNION ALL
    (
        SELECT counterparty_id, alias, COUNT(*)
        FROM counterparty_aliases
        GROUP BY counterparty_id, alias
        EXCEPT
        SELECT payer_id, alias, COUNT(*)
        FROM payer_aliases
        GROUP BY payer_id, alias
    )
) alias_mirror_drift;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    (
        SELECT payer_id AS owner_id, account_number, COUNT(*) AS occurrences
        FROM payer_accounts
        GROUP BY payer_id, account_number
        EXCEPT
        SELECT counterparty_id, account_number, COUNT(*)
        FROM counterparty_accounts
        GROUP BY counterparty_id, account_number
    )
    UNION ALL
    (
        SELECT counterparty_id, account_number, COUNT(*)
        FROM counterparty_accounts
        GROUP BY counterparty_id, account_number
        EXCEPT
        SELECT payer_id, account_number, COUNT(*)
        FROM payer_accounts
        GROUP BY payer_id, account_number
    )
) account_mirror_drift;

-- P8: pending identity evidence was copied verbatim to the mapped inbox item.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT evidence.pending_expense_id AS legacy_id
    FROM pending_expense_identity_evidence evidence
    LEFT JOIN legacy_inbox_map legacy_map
      ON legacy_map.legacy_table = 'PENDING_EXPENSES'
     AND legacy_map.legacy_id = evidence.pending_expense_id
    LEFT JOIN inbox_identity_evidence target
      ON target.inbox_item_id = legacy_map.inbox_item_id
     AND target.snapshot_order = evidence.snapshot_order
    WHERE target.inbox_item_id IS NULL
       OR target.target_kind IS DISTINCT FROM evidence.target_kind
       OR target.candidate_rank IS DISTINCT FROM evidence.candidate_rank
       OR target.target_id IS DISTINCT FROM evidence.target_id
       OR target.target_name IS DISTINCT FROM evidence.target_name
       OR target.property_id IS DISTINCT FROM evidence.property_id
       OR target.property_name IS DISTINCT FROM evidence.property_name
       OR target.evidence_rank IS DISTINCT FROM evidence.evidence_rank
       OR target.evidence_type IS DISTINCT FROM evidence.evidence_type
       OR target.evidence_strength IS DISTINCT FROM evidence.evidence_strength
       OR target.occurrences IS DISTINCT FROM evidence.occurrences
       OR target.total_occurrences IS DISTINCT FROM evidence.total_occurrences
    UNION ALL
    SELECT evidence.pending_income_id
    FROM pending_income_identity_evidence evidence
    LEFT JOIN legacy_inbox_map legacy_map
      ON legacy_map.legacy_table = 'PENDING_INCOMES'
     AND legacy_map.legacy_id = evidence.pending_income_id
    LEFT JOIN inbox_identity_evidence target
      ON target.inbox_item_id = legacy_map.inbox_item_id
     AND target.snapshot_order = evidence.snapshot_order
    WHERE target.inbox_item_id IS NULL
       OR target.target_kind IS DISTINCT FROM evidence.target_kind
       OR target.candidate_rank IS DISTINCT FROM evidence.candidate_rank
       OR target.target_id IS DISTINCT FROM evidence.target_id
       OR target.target_name IS DISTINCT FROM evidence.target_name
       OR target.property_id IS DISTINCT FROM evidence.property_id
       OR target.property_name IS DISTINCT FROM evidence.property_name
       OR target.evidence_rank IS DISTINCT FROM evidence.evidence_rank
       OR target.evidence_type IS DISTINCT FROM evidence.evidence_type
       OR target.evidence_strength IS DISTINCT FROM evidence.evidence_strength
       OR target.occurrences IS DISTINCT FROM evidence.occurrences
       OR target.total_occurrences IS DISTINCT FROM evidence.total_occurrences
) uncopied_identity_evidence;

-- DDL. The unified ledger's counterparty key moves from the mirror to the renamed payer table,
-- which keeps its identity column so new counterparty IDs continue past the existing maximum.
ALTER TABLE financial_transactions DROP CONSTRAINT fk_financial_transactions_counterparty;

DROP TABLE pending_expense_identity_evidence;
DROP TABLE pending_income_identity_evidence;
DROP TABLE pending_expense_aliases;
DROP TABLE pending_expenses;
DROP TABLE pending_incomes;
DROP TABLE legacy_inbox_map;
DROP TABLE legacy_transaction_map;
DROP TABLE ledger_migration_quarantine;
DROP TABLE expenses;
DROP TABLE incomes;
DROP TABLE legacy_payer_map;
DROP TABLE counterparty_aliases;
DROP TABLE counterparty_accounts;
DROP TABLE counterparties;

-- H2 keeps a foreign key's original column name in its metadata (and in SCRIPT backups taken by
-- the same session) when the column is renamed, so the alias and account keys are recreated.
-- Databases whose payer tables were created by Hibernate before Flyway carry its hashed key
-- names instead of V1's, so each key is dropped under whichever of the two names exists.
ALTER TABLE payer_aliases DROP CONSTRAINT IF EXISTS fk_payer_aliases_payer;
ALTER TABLE payer_aliases DROP CONSTRAINT IF EXISTS FK8F74KNMKL4TNHFKFY6A880D9Q;
ALTER TABLE payer_accounts DROP CONSTRAINT IF EXISTS fk_payer_accounts_payer;
ALTER TABLE payer_accounts DROP CONSTRAINT IF EXISTS FKHPIGNNVLT6FM3BKLEDDNDD9F3;

ALTER TABLE payers RENAME TO counterparties;
ALTER TABLE payer_aliases RENAME TO counterparty_aliases;
ALTER TABLE counterparty_aliases ALTER COLUMN payer_id RENAME TO counterparty_id;
ALTER TABLE payer_accounts RENAME TO counterparty_accounts;
ALTER TABLE counterparty_accounts ALTER COLUMN payer_id RENAME TO counterparty_id;

ALTER TABLE counterparty_aliases
    ADD CONSTRAINT fk_counterparty_alias_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES counterparties(id);
ALTER TABLE counterparty_accounts
    ADD CONSTRAINT fk_counterparty_account_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES counterparties(id);

ALTER TABLE financial_transactions
    ADD CONSTRAINT fk_financial_transactions_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES counterparties(id);

CREATE INDEX idx_counterparty_alias_value ON counterparty_aliases(alias);
CREATE INDEX idx_counterparty_account_value ON counterparty_accounts(account_number);

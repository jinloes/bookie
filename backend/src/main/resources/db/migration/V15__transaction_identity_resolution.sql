-- Reject stale configured activities before assigning authoritative identity state.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM pending_expenses pending
LEFT JOIN financial_activities activity ON activity.id = pending.configured_activity_id
LEFT JOIN household_members owner ON owner.id = activity.owner_id
WHERE pending.configured_activity_id IS NOT NULL
  AND (
      activity.id IS NULL
      OR activity.active IS NOT TRUE
      OR owner.id IS NULL
      OR owner.active IS NOT TRUE
      OR pending.activity_id IS DISTINCT FROM pending.configured_activity_id
  );

ALTER TABLE pending_expenses ADD COLUMN payer_id BIGINT;
ALTER TABLE pending_expenses
    ADD COLUMN activity_resolution_state VARCHAR(30) DEFAULT 'UNRESOLVED' NOT NULL;
ALTER TABLE pending_expenses
    ADD COLUMN counterparty_resolution_state VARCHAR(30) DEFAULT 'UNRESOLVED' NOT NULL;
ALTER TABLE pending_expenses
    ADD CONSTRAINT fk_pending_expenses_identity_payer
        FOREIGN KEY (payer_id) REFERENCES payers(id);
CREATE INDEX idx_pending_expenses_identity_payer ON pending_expenses(payer_id);

ALTER TABLE pending_incomes
    ADD COLUMN activity_resolution_state VARCHAR(30) DEFAULT 'UNRESOLVED' NOT NULL;
ALTER TABLE pending_incomes
    ADD COLUMN counterparty_resolution_state VARCHAR(30) DEFAULT 'UNRESOLVED' NOT NULL;

UPDATE pending_expenses
SET activity_resolution_state = 'RESOLVED'
WHERE configured_activity_id IS NOT NULL;

ALTER TABLE inbox_items ADD COLUMN selected_activity_id BIGINT;
ALTER TABLE inbox_items ADD COLUMN selected_counterparty_id BIGINT;
ALTER TABLE inbox_items
    ADD COLUMN activity_resolution_state VARCHAR(30) DEFAULT 'UNRESOLVED' NOT NULL;
ALTER TABLE inbox_items
    ADD COLUMN counterparty_resolution_state VARCHAR(30) DEFAULT 'UNRESOLVED' NOT NULL;
ALTER TABLE inbox_items ADD COLUMN confirmed_activity_id BIGINT;
ALTER TABLE inbox_items ADD COLUMN confirmed_counterparty_id BIGINT;
ALTER TABLE inbox_items ADD COLUMN identity_confirmation_source VARCHAR(30);
ALTER TABLE inbox_items
    ADD CONSTRAINT fk_inbox_items_selected_activity
        FOREIGN KEY (selected_activity_id) REFERENCES financial_activities(id) ON DELETE SET NULL;
ALTER TABLE inbox_items
    ADD CONSTRAINT fk_inbox_items_selected_counterparty
        FOREIGN KEY (selected_counterparty_id) REFERENCES payers(id) ON DELETE SET NULL;
CREATE INDEX idx_inbox_items_selected_activity ON inbox_items(selected_activity_id);
CREATE INDEX idx_inbox_items_selected_counterparty ON inbox_items(selected_counterparty_id);

UPDATE inbox_items item
SET selected_activity_id = (
        SELECT pending.configured_activity_id
        FROM pending_expenses pending
        WHERE pending.id = item.migration_legacy_id
    ),
    activity_resolution_state = 'RESOLVED'
WHERE item.migration_legacy_table = 'PENDING_EXPENSES'
  AND EXISTS (
      SELECT 1
      FROM pending_expenses pending
      WHERE pending.id = item.migration_legacy_id
        AND pending.configured_activity_id IS NOT NULL
  );

CREATE TABLE pending_expense_identity_evidence (
    pending_expense_id BIGINT NOT NULL,
    snapshot_order INTEGER NOT NULL,
    target_kind VARCHAR(30) NOT NULL,
    candidate_rank INTEGER NOT NULL,
    target_id BIGINT NOT NULL,
    target_name VARCHAR(500) NOT NULL,
    property_id BIGINT,
    property_name VARCHAR(500),
    evidence_rank INTEGER NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    evidence_strength VARCHAR(30) NOT NULL,
    occurrences INTEGER NOT NULL,
    total_occurrences INTEGER NOT NULL,
    CONSTRAINT pk_pending_expense_identity_evidence
        PRIMARY KEY (pending_expense_id, snapshot_order),
    CONSTRAINT fk_pending_expense_identity_evidence
        FOREIGN KEY (pending_expense_id) REFERENCES pending_expenses(id) ON DELETE CASCADE,
    CONSTRAINT ck_pending_expense_identity_occurrences
        CHECK (occurrences > 0 AND total_occurrences >= occurrences)
);
CREATE INDEX idx_pending_expense_identity_target
    ON pending_expense_identity_evidence(target_kind, target_id);

CREATE TABLE pending_income_identity_evidence (
    pending_income_id BIGINT NOT NULL,
    snapshot_order INTEGER NOT NULL,
    target_kind VARCHAR(30) NOT NULL,
    candidate_rank INTEGER NOT NULL,
    target_id BIGINT NOT NULL,
    target_name VARCHAR(500) NOT NULL,
    property_id BIGINT,
    property_name VARCHAR(500),
    evidence_rank INTEGER NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    evidence_strength VARCHAR(30) NOT NULL,
    occurrences INTEGER NOT NULL,
    total_occurrences INTEGER NOT NULL,
    CONSTRAINT pk_pending_income_identity_evidence
        PRIMARY KEY (pending_income_id, snapshot_order),
    CONSTRAINT fk_pending_income_identity_evidence
        FOREIGN KEY (pending_income_id) REFERENCES pending_incomes(id) ON DELETE CASCADE,
    CONSTRAINT ck_pending_income_identity_occurrences
        CHECK (occurrences > 0 AND total_occurrences >= occurrences)
);
CREATE INDEX idx_pending_income_identity_target
    ON pending_income_identity_evidence(target_kind, target_id);

CREATE TABLE inbox_identity_evidence (
    inbox_item_id BIGINT NOT NULL,
    snapshot_order INTEGER NOT NULL,
    target_kind VARCHAR(30) NOT NULL,
    candidate_rank INTEGER NOT NULL,
    target_id BIGINT NOT NULL,
    target_name VARCHAR(500) NOT NULL,
    property_id BIGINT,
    property_name VARCHAR(500),
    evidence_rank INTEGER NOT NULL,
    evidence_type VARCHAR(50) NOT NULL,
    evidence_strength VARCHAR(30) NOT NULL,
    occurrences INTEGER NOT NULL,
    total_occurrences INTEGER NOT NULL,
    CONSTRAINT pk_inbox_identity_evidence PRIMARY KEY (inbox_item_id, snapshot_order),
    CONSTRAINT fk_inbox_identity_evidence
        FOREIGN KEY (inbox_item_id) REFERENCES inbox_items(id) ON DELETE CASCADE,
    CONSTRAINT ck_inbox_identity_occurrences
        CHECK (occurrences > 0 AND total_occurrences >= occurrences)
);
CREATE INDEX idx_inbox_identity_target
    ON inbox_identity_evidence(target_kind, target_id);

-- A pre-existing configured activity is the only identity safe enough to backfill as resolved.
INSERT INTO pending_expense_identity_evidence (
    pending_expense_id,
    snapshot_order,
    target_kind,
    candidate_rank,
    target_id,
    target_name,
    property_id,
    property_name,
    evidence_rank,
    evidence_type,
    evidence_strength,
    occurrences,
    total_occurrences
)
SELECT
    pending.id,
    0,
    'ACTIVITY',
    1,
    activity.id,
    activity.name,
    property.id,
    property.name,
    1,
    'CONFIGURED_ACTIVITY_ID',
    'AUTHORITATIVE',
    1,
    1
FROM pending_expenses pending
JOIN financial_activities activity ON activity.id = pending.configured_activity_id
LEFT JOIN properties property ON property.id = activity.property_id
WHERE pending.configured_activity_id IS NOT NULL
ORDER BY pending.id;

INSERT INTO inbox_identity_evidence (
    inbox_item_id,
    snapshot_order,
    target_kind,
    candidate_rank,
    target_id,
    target_name,
    property_id,
    property_name,
    evidence_rank,
    evidence_type,
    evidence_strength,
    occurrences,
    total_occurrences
)
SELECT
    map.inbox_item_id,
    evidence.snapshot_order,
    evidence.target_kind,
    evidence.candidate_rank,
    evidence.target_id,
    evidence.target_name,
    evidence.property_id,
    evidence.property_name,
    evidence.evidence_rank,
    evidence.evidence_type,
    evidence.evidence_strength,
    evidence.occurrences,
    evidence.total_occurrences
FROM pending_expense_identity_evidence evidence
JOIN legacy_inbox_map map
  ON map.legacy_table = 'PENDING_EXPENSES'
 AND map.legacy_id = evidence.pending_expense_id
ORDER BY map.inbox_item_id, evidence.snapshot_order;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM pending_expenses pending
JOIN legacy_inbox_map map
  ON map.legacy_table = 'PENDING_EXPENSES'
 AND map.legacy_id = pending.id
JOIN inbox_items item ON item.id = map.inbox_item_id
WHERE item.selected_activity_id IS DISTINCT FROM
          CASE WHEN pending.activity_resolution_state = 'RESOLVED'
               THEN pending.configured_activity_id ELSE NULL END
   OR item.selected_counterparty_id IS NOT NULL
   OR item.activity_resolution_state IS DISTINCT FROM pending.activity_resolution_state
   OR item.counterparty_resolution_state IS DISTINCT FROM pending.counterparty_resolution_state;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM pending_incomes pending
JOIN legacy_inbox_map map
  ON map.legacy_table = 'PENDING_INCOMES'
 AND map.legacy_id = pending.id
JOIN inbox_items item ON item.id = map.inbox_item_id
WHERE item.selected_activity_id IS NOT NULL
   OR item.selected_counterparty_id IS NOT NULL
   OR item.activity_resolution_state <> 'UNRESOLVED'
   OR item.counterparty_resolution_state <> 'UNRESOLVED';

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM pending_expense_identity_evidence)
        = (SELECT COUNT(*) FROM pending_expenses WHERE configured_activity_id IS NOT NULL)
    AND (SELECT COUNT(*) FROM inbox_identity_evidence)
        = (SELECT COUNT(*) FROM pending_expenses WHERE configured_activity_id IS NOT NULL)
THEN 1 ELSE 0 END;

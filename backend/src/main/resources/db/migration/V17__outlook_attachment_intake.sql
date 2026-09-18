ALTER TABLE pending_expenses ADD COLUMN outlook_message_id VARCHAR(255);
ALTER TABLE pending_expenses ADD COLUMN outlook_attachment_id VARCHAR(255);
ALTER TABLE pending_expenses ADD COLUMN outlook_attachment_name VARCHAR(500);

UPDATE pending_expenses
SET outlook_message_id = source_id
WHERE source_type = 'OUTLOOK_EMAIL'
  AND source_id IS NOT NULL;

CREATE INDEX idx_pending_expenses_outlook_message
    ON pending_expenses(outlook_message_id);
CREATE UNIQUE INDEX uk_pending_expenses_outlook_attachment
    ON pending_expenses(outlook_message_id, outlook_attachment_id);

ALTER TABLE expenses ADD COLUMN outlook_message_id VARCHAR(255);
ALTER TABLE incomes ADD COLUMN outlook_message_id VARCHAR(255);

UPDATE expenses
SET outlook_message_id = source_id
WHERE source_type = 'OUTLOOK_EMAIL'
  AND source_id IS NOT NULL;

UPDATE incomes
SET outlook_message_id = source_id
WHERE source_type = 'OUTLOOK_EMAIL'
  AND source_id IS NOT NULL;

CREATE INDEX idx_expenses_outlook_message ON expenses(outlook_message_id);
CREATE INDEX idx_incomes_outlook_message ON incomes(outlook_message_id);

INSERT INTO inbox_artifacts (
    inbox_item_id,
    type,
    text_value,
    external_id,
    file_name,
    sha256
)
SELECT
    item.id,
    'OUTLOOK_EMAIL',
    pending.source_id,
    NULL,
    NULL,
    NULL
FROM inbox_items item
JOIN pending_expenses pending
  ON item.migration_legacy_table = 'PENDING_EXPENSES'
 AND item.migration_legacy_id = pending.id
WHERE pending.source_type = 'OUTLOOK_EMAIL'
  AND pending.source_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM inbox_artifacts artifact
      WHERE artifact.inbox_item_id = item.id
        AND artifact.type = 'OUTLOOK_EMAIL'
  );

CREATE INDEX idx_inbox_artifacts_outlook_parent
    ON inbox_artifacts(type, text_value);

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM pending_expenses
WHERE source_type = 'OUTLOOK_EMAIL'
  AND source_id IS NOT NULL
  AND outlook_message_id IS DISTINCT FROM source_id;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM expenses
WHERE source_type = 'OUTLOOK_EMAIL'
  AND source_id IS NOT NULL
  AND outlook_message_id IS DISTINCT FROM source_id;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM incomes
WHERE source_type = 'OUTLOOK_EMAIL'
  AND source_id IS NOT NULL
  AND outlook_message_id IS DISTINCT FROM source_id;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM pending_expenses pending
JOIN legacy_inbox_map map
  ON map.legacy_table = 'PENDING_EXPENSES'
 AND map.legacy_id = pending.id
WHERE pending.source_type = 'OUTLOOK_EMAIL'
  AND pending.source_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM inbox_artifacts artifact
      WHERE artifact.inbox_item_id = map.inbox_item_id
        AND artifact.type = 'OUTLOOK_EMAIL'
        AND artifact.text_value = pending.source_id
  );

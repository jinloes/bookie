UPDATE inbox_artifacts artifact
SET external_id = (
        SELECT pending.outlook_message_id
        FROM legacy_inbox_map legacy_map
        JOIN pending_expenses pending
          ON pending.id = legacy_map.legacy_id
        WHERE legacy_map.legacy_table = 'PENDING_EXPENSES'
          AND legacy_map.inbox_item_id = artifact.inbox_item_id
    ),
    text_value = NULL
WHERE artifact.type = 'OUTLOOK_EMAIL'
  AND artifact.external_id IS NULL
  AND artifact.file_name IS NULL
  AND EXISTS (
      SELECT 1
      FROM legacy_inbox_map legacy_map
      JOIN pending_expenses pending
        ON pending.id = legacy_map.legacy_id
      WHERE legacy_map.legacy_table = 'PENDING_EXPENSES'
        AND legacy_map.inbox_item_id = artifact.inbox_item_id
        AND pending.outlook_message_id IS NOT NULL
        AND artifact.text_value = pending.outlook_message_id
  );

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM inbox_artifacts artifact
JOIN legacy_inbox_map legacy_map
  ON legacy_map.inbox_item_id = artifact.inbox_item_id
 AND legacy_map.legacy_table = 'PENDING_EXPENSES'
JOIN pending_expenses pending ON pending.id = legacy_map.legacy_id
WHERE artifact.type = 'OUTLOOK_EMAIL'
  AND artifact.external_id IS NULL
  AND artifact.file_name IS NULL
  AND pending.outlook_message_id IS NOT NULL
  AND artifact.text_value = pending.outlook_message_id;

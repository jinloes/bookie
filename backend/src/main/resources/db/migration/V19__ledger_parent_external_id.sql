ALTER TABLE transaction_import_references ADD COLUMN parent_external_id VARCHAR(255);

UPDATE transaction_import_references import_reference
SET parent_external_id = (
    SELECT expense.outlook_message_id
    FROM legacy_transaction_map legacy_map
    JOIN expenses expense
      ON legacy_map.legacy_table = 'EXPENSES'
     AND legacy_map.legacy_id = expense.id
    WHERE legacy_map.transaction_id = import_reference.transaction_id
)
WHERE EXISTS (
    SELECT 1
    FROM legacy_transaction_map legacy_map
    JOIN expenses expense
      ON legacy_map.legacy_table = 'EXPENSES'
     AND legacy_map.legacy_id = expense.id
    WHERE legacy_map.transaction_id = import_reference.transaction_id
      AND expense.outlook_message_id IS NOT NULL
);

UPDATE transaction_import_references import_reference
SET parent_external_id = (
    SELECT income.outlook_message_id
    FROM legacy_transaction_map legacy_map
    JOIN incomes income
      ON legacy_map.legacy_table = 'INCOMES'
     AND legacy_map.legacy_id = income.id
    WHERE legacy_map.transaction_id = import_reference.transaction_id
)
WHERE EXISTS (
    SELECT 1
    FROM legacy_transaction_map legacy_map
    JOIN incomes income
      ON legacy_map.legacy_table = 'INCOMES'
     AND legacy_map.legacy_id = income.id
    WHERE legacy_map.transaction_id = import_reference.transaction_id
      AND income.outlook_message_id IS NOT NULL
);

CREATE INDEX idx_tir_origin_parent
    ON transaction_import_references(origin, parent_external_id);

-- Mapped legacy rows without an import reference (all source fields null) have nowhere to store
-- the parent identity, so only rows that own a reference are expected. A transaction holding more
-- than one reference makes the counts diverge and aborts the migration.
SELECT 1 / CASE WHEN expected.total = actual.total THEN 1 ELSE 0 END
FROM (
    SELECT COUNT(*) AS total
    FROM (
        SELECT legacy_map.transaction_id
        FROM legacy_transaction_map legacy_map
        JOIN expenses expense
          ON legacy_map.legacy_table = 'EXPENSES'
         AND legacy_map.legacy_id = expense.id
        WHERE expense.outlook_message_id IS NOT NULL
          AND EXISTS (
              SELECT 1
              FROM transaction_import_references import_reference
              WHERE import_reference.transaction_id = legacy_map.transaction_id
          )
        UNION ALL
        SELECT legacy_map.transaction_id
        FROM legacy_transaction_map legacy_map
        JOIN incomes income
          ON legacy_map.legacy_table = 'INCOMES'
         AND legacy_map.legacy_id = income.id
        WHERE income.outlook_message_id IS NOT NULL
          AND EXISTS (
              SELECT 1
              FROM transaction_import_references import_reference
              WHERE import_reference.transaction_id = legacy_map.transaction_id
          )
    ) mapped_parent
) expected
CROSS JOIN (
    SELECT COUNT(*) AS total
    FROM transaction_import_references
    WHERE parent_external_id IS NOT NULL
) actual;

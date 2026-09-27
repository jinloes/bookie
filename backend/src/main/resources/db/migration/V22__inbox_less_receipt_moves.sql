-- Durable background jobs replace the Spring Modulith event publication registry. A receipt filing
-- that had not completed becomes an equivalent MOVE_RECEIPT job so its move is not lost; the other
-- publications only kicked the job relay, which its scheduled poll already recovers.
--
-- Precheck before any DDL (H2 DDL auto-commits): every unfinished receipt filing must be readable.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM event_publication
WHERE completion_date IS NULL
  AND event_type = 'com.bookie.ledger.application.TransactionRecorded'
  AND (
      REGEXP_SUBSTR(serialized_event, '"transactionId":([0-9]+)', 1, 1, '', 1) IS NULL
      OR REGEXP_SUBSTR(serialized_event, '"attachmentExternalId":"([^"]+)"', 1, 1, '', 1) IS NULL
      OR COALESCE(
          REGEXP_SUBSTR(serialized_event, '"date":"([0-9]{4})-', 1, 1, '', 1),
          REGEXP_SUBSTR(serialized_event, '"date":\[([0-9]{4}),', 1, 1, '', 1)) IS NULL);

-- A manually entered receipt transaction has no inbox item, so its MOVE_RECEIPT job names the
-- OneDrive item directly. Every job identifies its work through exactly one of the two.
ALTER TABLE background_jobs ALTER COLUMN inbox_item_id SET NULL;
ALTER TABLE background_jobs ADD source_external_id VARCHAR(500);
ALTER TABLE background_jobs ADD CONSTRAINT ck_background_jobs_subject
    CHECK (
        (inbox_item_id IS NOT NULL AND source_external_id IS NULL)
        OR (inbox_item_id IS NULL AND source_external_id IS NOT NULL AND type = 'MOVE_RECEIPT')
    );

INSERT INTO background_jobs (
    type, idempotency_key, state, attempts, max_attempts, available_at, target_year,
    source_external_id, execution_attempt_base, execution_started, created_at, updated_at,
    version)
SELECT DISTINCT
    'MOVE_RECEIPT',
    'move_receipt:transaction:' || pending.transaction_id,
    'AVAILABLE', 0, 11, LOCALTIMESTAMP, CAST(pending.target_year AS INTEGER),
    pending.attachment_external_id, 0, FALSE, LOCALTIMESTAMP, LOCALTIMESTAMP, 0
FROM (
    SELECT
        REGEXP_SUBSTR(serialized_event, '"transactionId":([0-9]+)', 1, 1, '', 1) AS transaction_id,
        REGEXP_SUBSTR(serialized_event, '"attachmentExternalId":"([^"]+)"', 1, 1, '', 1)
            AS attachment_external_id,
        COALESCE(
            REGEXP_SUBSTR(serialized_event, '"date":"([0-9]{4})-', 1, 1, '', 1),
            REGEXP_SUBSTR(serialized_event, '"date":\[([0-9]{4}),', 1, 1, '', 1)) AS target_year
    FROM event_publication
    WHERE completion_date IS NULL
      AND event_type = 'com.bookie.ledger.application.TransactionRecorded'
) pending;

DROP TABLE event_publication;

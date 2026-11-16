-- SQL Server counterpart to H2 V25__retire_legacy_categories_and_jobrunr.sql.
-- Uses GO batch separators where T-SQL compiles column references per batch.

IF EXISTS (
    SELECT 1
    FROM inbox_items item
    LEFT JOIN legacy_category_map category_map
      ON category_map.legacy_category_id = item.legacy_category_id
    WHERE item.legacy_category_id IS NOT NULL
      AND category_map.neutral_category_id IS NULL
)
BEGIN
    THROW 50025, 'V25 precheck failed: inbox_items references an unmapped legacy_category_id', 1;
END;

IF EXISTS (
    SELECT 1
    FROM email_keyword_classification_history history
    LEFT JOIN legacy_category_map category_map
      ON category_map.legacy_category_id = history.financial_category_id
    WHERE category_map.neutral_category_id IS NULL
)
BEGIN
    THROW 50026, 'V25 precheck failed: email_keyword_classification_history references an unmapped financial_category_id', 1;
END;

IF EXISTS (
    SELECT 1
    FROM email_keyword_classification_history history
    JOIN legacy_category_map category_map
      ON category_map.legacy_category_id = history.financial_category_id
    GROUP BY history.keyword, history.activity_id, category_map.neutral_category_id
    HAVING COUNT(*) > 1
)
BEGIN
    THROW 50027, 'V25 precheck failed: duplicate neutral category classification history key', 1;
END;
GO

ALTER TABLE inbox_items ADD neutral_category_id BIGINT NULL;
GO

UPDATE item
SET item.neutral_category_id = category_map.neutral_category_id
FROM inbox_items item
JOIN legacy_category_map category_map
  ON category_map.legacy_category_id = item.legacy_category_id;

IF EXISTS (
    SELECT 1
    FROM inbox_items
    WHERE (legacy_category_id IS NOT NULL AND neutral_category_id IS NULL)
       OR (legacy_category_id IS NULL AND neutral_category_id IS NOT NULL)
)
BEGIN
    THROW 50028, 'V25 postcheck failed: inbox_items neutral_category_id backfill mismatch', 1;
END;

ALTER TABLE inbox_items
    ADD CONSTRAINT fk_inbox_items_neutral_category
        FOREIGN KEY (neutral_category_id) REFERENCES neutral_categories (id);
CREATE INDEX idx_inbox_items_neutral_category ON inbox_items (neutral_category_id);
ALTER TABLE inbox_items DROP COLUMN legacy_category_id;
GO

ALTER TABLE email_keyword_classification_history ADD neutral_category_id BIGINT NULL;
GO

UPDATE history
SET history.neutral_category_id = category_map.neutral_category_id
FROM email_keyword_classification_history history
JOIN legacy_category_map category_map
  ON category_map.legacy_category_id = history.financial_category_id;

IF EXISTS (
    SELECT 1
    FROM email_keyword_classification_history
    WHERE neutral_category_id IS NULL
)
BEGIN
    THROW 50029, 'V25 postcheck failed: email_keyword_classification_history neutral_category_id is null', 1;
END;

ALTER TABLE email_keyword_classification_history ALTER COLUMN neutral_category_id BIGINT NOT NULL;
ALTER TABLE email_keyword_classification_history DROP CONSTRAINT uk_email_keyword_classification;
ALTER TABLE email_keyword_classification_history DROP CONSTRAINT fk_email_keyword_classification_category;
ALTER TABLE email_keyword_classification_history DROP COLUMN financial_category_id;
ALTER TABLE email_keyword_classification_history
    ADD CONSTRAINT uk_email_keyword_classification
        UNIQUE (keyword, activity_id, neutral_category_id);
ALTER TABLE email_keyword_classification_history
    ADD CONSTRAINT fk_email_keyword_classification_category
        FOREIGN KEY (neutral_category_id) REFERENCES neutral_categories (id) ON DELETE CASCADE;
GO

ALTER TABLE category_reporting_mappings DROP CONSTRAINT fk_category_reporting_mapping_legacy;
ALTER TABLE category_reporting_mappings DROP CONSTRAINT uk_category_reporting_mapping_legacy;
ALTER TABLE category_reporting_mappings DROP COLUMN legacy_category_id;

DROP TABLE payer_category_history;
DROP TABLE email_keyword_category_history;
DROP TABLE legacy_category_map;
DROP TABLE financial_categories;
GO

UPDATE background_jobs
SET max_attempts = CASE
        WHEN COALESCE(execution_previous_max_attempts, max_attempts) <= attempts
        THEN attempts + 1
        ELSE COALESCE(execution_previous_max_attempts, max_attempts)
    END
WHERE state IN (N'AVAILABLE', N'LEASED');

UPDATE background_jobs
SET state = N'AVAILABLE',
    lease_owner = NULL,
    lease_expires_at = NULL
WHERE state = N'LEASED';

DROP INDEX idx_background_jobs_execution ON background_jobs;
ALTER TABLE background_jobs DROP CONSTRAINT ck_background_jobs_attempts;
ALTER TABLE background_jobs DROP CONSTRAINT df_background_jobs_execution_attempt_base;
ALTER TABLE background_jobs DROP CONSTRAINT df_background_jobs_execution_started;
ALTER TABLE background_jobs DROP COLUMN execution_id;
ALTER TABLE background_jobs DROP COLUMN execution_attempt_base;
ALTER TABLE background_jobs DROP COLUMN execution_previous_max_attempts;
ALTER TABLE background_jobs DROP COLUMN execution_started;
ALTER TABLE background_jobs
    ADD CONSTRAINT ck_background_jobs_attempts
        CHECK (attempts >= 0 AND max_attempts > 0);

DROP VIEW IF EXISTS jobrunr_jobs_stats;
DROP TABLE IF EXISTS jobrunr_jobs;
DROP TABLE IF EXISTS jobrunr_recurring_jobs;
DROP TABLE IF EXISTS jobrunr_backgroundjobservers;
DROP TABLE IF EXISTS jobrunr_metadata;
DROP TABLE IF EXISTS jobrunr_migrations;
GO

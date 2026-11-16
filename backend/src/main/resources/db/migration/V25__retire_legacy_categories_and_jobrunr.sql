-- Retire legacy financial_categories, legacy_category_map, rental-enum category history tables,
-- and embedded JobRunr storage in favor of NeutralCategory + CategoryReportingMapping and direct
-- background_jobs outbox polling. All prechecks run before any DDL because H2 DDL auto-commits.

-- P1: every inbox item with a legacy category maps to a neutral category.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM inbox_items item
LEFT JOIN legacy_category_map category_map
  ON category_map.legacy_category_id = item.legacy_category_id
WHERE item.legacy_category_id IS NOT NULL
  AND category_map.neutral_category_id IS NULL;

-- P2: every activity-scoped keyword classification history row maps to a neutral category.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM email_keyword_classification_history history
LEFT JOIN legacy_category_map category_map
  ON category_map.legacy_category_id = history.financial_category_id
WHERE category_map.neutral_category_id IS NULL;

-- P3: mapping keyword classification history to neutral_category_id produces no duplicate keys.
SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM (
    SELECT history.keyword, history.activity_id, category_map.neutral_category_id
    FROM email_keyword_classification_history history
    JOIN legacy_category_map category_map
      ON category_map.legacy_category_id = history.financial_category_id
    GROUP BY history.keyword, history.activity_id, category_map.neutral_category_id
    HAVING COUNT(*) > 1
) duplicate_neutral_history;

-- 1. Migrate inbox_items.legacy_category_id -> neutral_category_id.
ALTER TABLE inbox_items ADD COLUMN neutral_category_id BIGINT;

UPDATE inbox_items item
SET neutral_category_id = (
    SELECT category_map.neutral_category_id
    FROM legacy_category_map category_map
    WHERE category_map.legacy_category_id = item.legacy_category_id
)
WHERE item.legacy_category_id IS NOT NULL;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM inbox_items
WHERE (legacy_category_id IS NOT NULL AND neutral_category_id IS NULL)
   OR (legacy_category_id IS NULL AND neutral_category_id IS NOT NULL);

ALTER TABLE inbox_items
    ADD CONSTRAINT fk_inbox_items_neutral_category
        FOREIGN KEY (neutral_category_id) REFERENCES neutral_categories(id);
CREATE INDEX idx_inbox_items_neutral_category ON inbox_items(neutral_category_id);
ALTER TABLE inbox_items DROP COLUMN legacy_category_id;

-- 2. Migrate email_keyword_classification_history.financial_category_id -> neutral_category_id.
ALTER TABLE email_keyword_classification_history ADD COLUMN neutral_category_id BIGINT;

UPDATE email_keyword_classification_history history
SET neutral_category_id = (
    SELECT category_map.neutral_category_id
    FROM legacy_category_map category_map
    WHERE category_map.legacy_category_id = history.financial_category_id
);

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM email_keyword_classification_history
WHERE neutral_category_id IS NULL;

ALTER TABLE email_keyword_classification_history ALTER COLUMN neutral_category_id SET NOT NULL;
ALTER TABLE email_keyword_classification_history DROP CONSTRAINT uk_email_keyword_classification;
ALTER TABLE email_keyword_classification_history DROP CONSTRAINT fk_email_keyword_classification_category;
ALTER TABLE email_keyword_classification_history DROP COLUMN financial_category_id;
ALTER TABLE email_keyword_classification_history
    ADD CONSTRAINT uk_email_keyword_classification
        UNIQUE (keyword, activity_id, neutral_category_id);
ALTER TABLE email_keyword_classification_history
    ADD CONSTRAINT fk_email_keyword_classification_category
        FOREIGN KEY (neutral_category_id) REFERENCES neutral_categories(id) ON DELETE CASCADE;

-- 3. Drop legacy_category_id from category_reporting_mappings and retire legacy category tables.
ALTER TABLE category_reporting_mappings DROP CONSTRAINT fk_category_reporting_mapping_legacy;
ALTER TABLE category_reporting_mappings DROP CONSTRAINT uk_category_reporting_mapping_legacy;
ALTER TABLE category_reporting_mappings DROP COLUMN legacy_category_id;

DROP TABLE payer_category_history;
DROP TABLE email_keyword_category_history;
DROP TABLE legacy_category_map;
DROP TABLE financial_categories;

-- 4. Release any mid-flight JobRunr bindings on background_jobs and drop JobRunr columns/tables.
UPDATE background_jobs
SET max_attempts = CASE
        WHEN COALESCE(execution_previous_max_attempts, max_attempts) <= attempts
        THEN attempts + 1
        ELSE COALESCE(execution_previous_max_attempts, max_attempts)
    END
WHERE state IN ('AVAILABLE', 'LEASED');

UPDATE background_jobs
SET state = 'AVAILABLE',
    lease_owner = NULL,
    lease_expires_at = NULL
WHERE state = 'LEASED';

DROP INDEX idx_background_jobs_execution;
ALTER TABLE background_jobs DROP CONSTRAINT ck_background_jobs_attempts;
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

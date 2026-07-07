-- Backfill NULL @Version columns on history tables.
-- Hibernate adds the column via ddl-auto=update with no default, leaving pre-existing rows at NULL.
-- On the next update Hibernate evaluates `current.longValue() + 1` and throws NPE on null versions.
-- Idempotent: the WHERE clause matches nothing on re-run.

UPDATE payer_property_history SET version = 0 WHERE version IS NULL;
UPDATE payer_category_history SET version = 0 WHERE version IS NULL;
UPDATE email_keyword_property_history SET version = 0 WHERE version IS NULL;
UPDATE email_keyword_payer_history SET version = 0 WHERE version IS NULL;
UPDATE email_keyword_category_history SET version = 0 WHERE version IS NULL;

-- Dedupe parsed_email_keywords before adding the unique constraint that ddl-auto=update never
-- applied. Pre-fix retries inserted the same (source_id, keyword) multiple times. Keep the lowest id
-- per group; the table is a transient cache (rows are deleted after the resulting expense is saved)
-- so losing the duplicate copies has no behavioral effect.
DELETE FROM parsed_email_keywords
 WHERE id NOT IN (SELECT MIN(id) FROM parsed_email_keywords GROUP BY source_id, keyword);

-- Add unique constraints declared on entities that ddl-auto=update did not retroactively apply.
-- IF NOT EXISTS makes this safe on databases where Hibernate already created them.
ALTER TABLE pending_expenses ADD CONSTRAINT IF NOT EXISTS uk_pending_expenses_source_id UNIQUE (source_id);
ALTER TABLE parsed_email_keywords ADD CONSTRAINT IF NOT EXISTS uk_parsed_email_keywords UNIQUE (source_id, keyword);

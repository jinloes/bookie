ALTER TABLE household_members
    ADD COLUMN IF NOT EXISTS system_key VARCHAR(100);

UPDATE household_members
SET system_key = 'DEFAULT_HOUSEHOLD'
WHERE id = (SELECT MIN(id) FROM household_members)
  AND system_key IS NULL;

ALTER TABLE household_members
    ADD CONSTRAINT IF NOT EXISTS uk_household_members_system_key UNIQUE (system_key);

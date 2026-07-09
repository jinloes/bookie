-- Income now links to payer for both manual entries and imports (e.g. Venmo).
ALTER TABLE incomes ALTER COLUMN source_type VARCHAR(255);
ALTER TABLE incomes ADD COLUMN IF NOT EXISTS payer_id BIGINT;
ALTER TABLE incomes
  ADD CONSTRAINT IF NOT EXISTS fk_incomes_payer FOREIGN KEY (payer_id) REFERENCES payers(id);

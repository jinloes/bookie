-- V5: Add unique constraint on (sourceType, sourceId) to prevent duplicate emails
-- If same Outlook email appears in multiple folders, only one instance is kept

ALTER TABLE expenses ADD CONSTRAINT unique_source_per_type UNIQUE (source_type, source_id);

ALTER TABLE pending_expenses ADD CONSTRAINT unique_pending_source_per_type UNIQUE (source_type, source_id);

-- Add index on sourceType + sourceId for query performance
CREATE INDEX idx_expense_source_type_id ON expenses(source_type, source_id);
CREATE INDEX idx_pending_expense_source_type_id ON pending_expenses(source_type, source_id);

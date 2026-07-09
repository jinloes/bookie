CREATE TABLE pending_incomes (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  source_id VARCHAR(255) UNIQUE,
  source_type VARCHAR(50),
  status VARCHAR(50) NOT NULL,
  source VARCHAR(255),
  amount DECIMAL(19, 2),
  description TEXT,
  date DATE,
  property_id BIGINT,
  payer_id BIGINT,
  receipt_one_drive_id VARCHAR(255),
  receipt_file_name VARCHAR(255),
  error_message VARCHAR(2000),
  created_at TIMESTAMP NOT NULL,
  FOREIGN KEY (property_id) REFERENCES properties(id),
  FOREIGN KEY (payer_id) REFERENCES payers(id)
);

CREATE INDEX idx_pending_incomes_status ON pending_incomes(status);
CREATE INDEX idx_pending_incomes_created_at ON pending_incomes(created_at);

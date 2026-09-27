-- One statement file (such as a monthly Venmo CSV) is the attachment for every transaction it
-- lists, so a OneDrive item may back more than one transaction. Keep a plain index for the
-- attachment-to-transaction lookups that the unique key used to serve.
ALTER TABLE transaction_attachments DROP CONSTRAINT uk_transaction_attachments_external;

CREATE INDEX ix_transaction_attachments_external
    ON transaction_attachments (storage_provider, external_id);

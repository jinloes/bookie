-- Additional OneDrive folders to scan for receipts that already exist there (e.g. saved by a
-- phone scanning app), separate from the app-managed {receiptsFolderBase}/pending upload folder.
CREATE TABLE outlook_settings_receipts_import_folder (
    settings_id BIGINT NOT NULL,
    folder_path VARCHAR(500) NOT NULL,
    CONSTRAINT fk_osrif_settings FOREIGN KEY (settings_id) REFERENCES outlook_settings(id)
);

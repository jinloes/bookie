-- SQL Server baseline equivalent to the end state of the H2 migrations V1-V24 in db/migration.
-- New databases start here; later versions must be added to both migration locations.
-- JobRunr creates its own tables at startup in sqlserver mode, so they are not part of this file.

CREATE TABLE properties (
    id BIGINT IDENTITY(1,1) NOT NULL,
    address NVARCHAR(255) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    notes NVARCHAR(255) NULL,
    type NVARCHAR(50) NULL,
    CONSTRAINT pk_properties PRIMARY KEY (id)
);

CREATE TABLE property_accounts (
    property_id BIGINT NOT NULL,
    account_number NVARCHAR(255) NOT NULL
);

CREATE TABLE counterparties (
    id BIGINT IDENTITY(1,1) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    type NVARCHAR(50) NULL,
    CONSTRAINT pk_counterparties PRIMARY KEY (id)
);

CREATE TABLE counterparty_accounts (
    counterparty_id BIGINT NOT NULL,
    account_number NVARCHAR(255) NOT NULL
);

CREATE TABLE counterparty_aliases (
    counterparty_id BIGINT NOT NULL,
    alias NVARCHAR(255) NOT NULL
);

CREATE TABLE inbox_items (
    id BIGINT IDENTITY(1,1) NOT NULL,
    origin NVARCHAR(50) NOT NULL,
    legacy_source_type NVARCHAR(50) NULL,
    legacy_source_id NVARCHAR(255) NULL,
    immutable_source_id NVARCHAR(255) NULL,
    state NVARCHAR(30) NOT NULL,
    external_sync_state NVARCHAR(30) NOT NULL,
    proposed_direction NVARCHAR(20) NULL,
    raw_status NVARCHAR(50) NOT NULL,
    subject NVARCHAR(1000) NULL,
    proposed_amount DECIMAL(38, 2) NULL,
    proposed_description NVARCHAR(2000) NULL,
    proposed_date DATE NULL,
    proposed_category NVARCHAR(255) NULL,
    proposed_property_name NVARCHAR(255) NULL,
    proposed_counterparty_name NVARCHAR(255) NULL,
    proposed_source_label NVARCHAR(500) NULL,
    legacy_activity_id BIGINT NULL,
    legacy_category_id BIGINT NULL,
    legacy_property_id BIGINT NULL,
    legacy_counterparty_id BIGINT NULL,
    configured_activity_id BIGINT NULL,
    classification_ambiguous BIT NOT NULL,
    error_message NVARCHAR(2000) NULL,
    receipt_external_id NVARCHAR(255) NULL,
    receipt_file_name NVARCHAR(500) NULL,
    financial_transaction_id BIGINT NULL,
    migration_legacy_table NVARCHAR(30) NULL,
    migration_legacy_id BIGINT NULL,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    version BIGINT CONSTRAINT df_inbox_items_version DEFAULT 0 NOT NULL,
    selected_activity_id BIGINT NULL,
    selected_counterparty_id BIGINT NULL,
    activity_resolution_state NVARCHAR(30) CONSTRAINT df_inbox_items_activity_resolution_state DEFAULT N'UNRESOLVED' NOT NULL,
    counterparty_resolution_state NVARCHAR(30) CONSTRAINT df_inbox_items_counterparty_resolution_state DEFAULT N'UNRESOLVED' NOT NULL,
    confirmed_activity_id BIGINT NULL,
    confirmed_counterparty_id BIGINT NULL,
    identity_confirmation_source NVARCHAR(30) NULL,
    CONSTRAINT pk_inbox_items PRIMARY KEY (id)
);

CREATE TABLE email_keyword_classification_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    keyword NVARCHAR(255) NOT NULL,
    activity_id BIGINT NOT NULL,
    financial_category_id BIGINT NOT NULL,
    occurrences INT NOT NULL,
    version BIGINT NULL,
    CONSTRAINT pk_email_keyword_classification_history PRIMARY KEY (id)
);

CREATE TABLE payer_property_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    occurrences INT NOT NULL,
    payer_id BIGINT NOT NULL,
    property_id BIGINT NOT NULL,
    version BIGINT NULL,
    CONSTRAINT pk_payer_property_history PRIMARY KEY (id)
);

CREATE TABLE payer_category_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    category NVARCHAR(50) NOT NULL,
    occurrences INT NOT NULL,
    payer_id BIGINT NOT NULL,
    version BIGINT NULL,
    CONSTRAINT pk_payer_category_history PRIMARY KEY (id)
);

CREATE TABLE email_keyword_property_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    keyword NVARCHAR(255) NOT NULL,
    occurrences INT NOT NULL,
    property_id BIGINT NOT NULL,
    version BIGINT NULL,
    CONSTRAINT pk_email_keyword_property_history PRIMARY KEY (id)
);

CREATE TABLE email_keyword_payer_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    keyword NVARCHAR(255) NOT NULL,
    occurrences INT NOT NULL,
    payer_id BIGINT NOT NULL,
    version BIGINT NULL,
    CONSTRAINT pk_email_keyword_payer_history PRIMARY KEY (id)
);

CREATE TABLE email_keyword_category_history (
    id BIGINT IDENTITY(1,1) NOT NULL,
    category NVARCHAR(50) NOT NULL,
    keyword NVARCHAR(255) NOT NULL,
    occurrences INT NOT NULL,
    version BIGINT NULL,
    CONSTRAINT pk_email_keyword_category_history PRIMARY KEY (id)
);

CREATE TABLE parsed_email_keywords (
    id BIGINT IDENTITY(1,1) NOT NULL,
    keyword NVARCHAR(255) NOT NULL,
    source_id NVARCHAR(255) NOT NULL,
    CONSTRAINT pk_parsed_email_keywords PRIMARY KEY (id)
);

CREATE TABLE background_jobs (
    id BIGINT IDENTITY(1,1) NOT NULL,
    inbox_item_id BIGINT NULL,
    type NVARCHAR(40) NOT NULL,
    idempotency_key NVARCHAR(500) NOT NULL,
    state NVARCHAR(30) NOT NULL,
    attempts INT CONSTRAINT df_background_jobs_attempts DEFAULT 0 NOT NULL,
    max_attempts INT CONSTRAINT df_background_jobs_max_attempts DEFAULT 5 NOT NULL,
    available_at DATETIME2(6) NOT NULL,
    lease_owner NVARCHAR(255) NULL,
    lease_expires_at DATETIME2(6) NULL,
    last_error NVARCHAR(2000) NULL,
    terminal_reason NVARCHAR(100) NULL,
    legacy_pending_table NVARCHAR(30) NULL,
    legacy_pending_id BIGINT NULL,
    target_year INT NULL,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    version BIGINT CONSTRAINT df_background_jobs_version DEFAULT 0 NOT NULL,
    execution_id UNIQUEIDENTIFIER NULL,
    execution_attempt_base INT CONSTRAINT df_background_jobs_execution_attempt_base DEFAULT 0 NOT NULL,
    execution_previous_max_attempts INT NULL,
    execution_started BIT CONSTRAINT df_background_jobs_execution_started DEFAULT 0 NOT NULL,
    source_external_id NVARCHAR(500) NULL,
    CONSTRAINT pk_background_jobs PRIMARY KEY (id)
);

CREATE TABLE outlook_tokens (
    id BIGINT NOT NULL,
    cache_data NVARCHAR(MAX) NULL,
    CONSTRAINT pk_outlook_tokens PRIMARY KEY (id)
);

CREATE TABLE outlook_settings (
    id BIGINT NOT NULL,
    receipts_folder_base NVARCHAR(255) NULL,
    move_destination_folder_id NVARCHAR(255) NULL,
    auto_move_enabled BIT CONSTRAINT df_outlook_settings_auto_move_enabled DEFAULT 0 NULL,
    CONSTRAINT pk_outlook_settings PRIMARY KEY (id)
);

CREATE TABLE receipt_hashes (
    id BIGINT IDENTITY(1,1) NOT NULL,
    drive_item_id NVARCHAR(255) NOT NULL,
    sha256 NVARCHAR(64) NOT NULL,
    uploaded_at DATETIME2(6) NOT NULL,
    CONSTRAINT pk_receipt_hashes PRIMARY KEY (id)
);

CREATE TABLE reporting_profiles (
    id BIGINT IDENTITY(1,1) NOT NULL,
    profile_key NVARCHAR(50) NOT NULL,
    label NVARCHAR(255) NOT NULL,
    active BIT CONSTRAINT df_reporting_profiles_active DEFAULT 1 NOT NULL,
    CONSTRAINT pk_reporting_profiles PRIMARY KEY (id)
);

CREATE TABLE outlook_settings_folder (
    settings_id BIGINT NOT NULL,
    expand_subfolders BIT NULL,
    folder_id NVARCHAR(255) NULL,
    activity_id BIGINT NULL
);

CREATE TABLE outlook_settings_receipts_import_folder (
    settings_id BIGINT NOT NULL,
    folder_path NVARCHAR(500) NOT NULL
);

CREATE TABLE household_members (
    id BIGINT IDENTITY(1,1) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    active BIT CONSTRAINT df_household_members_active DEFAULT 1 NOT NULL,
    system_key NVARCHAR(100) NULL,
    CONSTRAINT pk_household_members PRIMARY KEY (id)
);

CREATE TABLE financial_activities (
    id BIGINT IDENTITY(1,1) NOT NULL,
    name NVARCHAR(255) NOT NULL,
    activity_type NVARCHAR(50) NOT NULL,
    tax_treatment NVARCHAR(50) NOT NULL,
    owner_id BIGINT NOT NULL,
    property_id BIGINT NULL,
    active BIT CONSTRAINT df_financial_activities_active DEFAULT 1 NOT NULL,
    system_key NVARCHAR(100) NULL,
    CONSTRAINT pk_financial_activities PRIMARY KEY (id)
);

CREATE TABLE transaction_import_references (
    id BIGINT IDENTITY(1,1) NOT NULL,
    transaction_id BIGINT NOT NULL,
    origin NVARCHAR(50) NOT NULL,
    external_id NVARCHAR(255) NULL,
    source_label NVARCHAR(500) NULL,
    parent_external_id NVARCHAR(255) NULL,
    CONSTRAINT pk_transaction_import_references PRIMARY KEY (id)
);

CREATE TABLE financial_categories (
    id BIGINT IDENTITY(1,1) NOT NULL,
    category_key NVARCHAR(100) NOT NULL,
    label NVARCHAR(255) NOT NULL,
    direction NVARCHAR(20) NOT NULL,
    tax_treatment NVARCHAR(50) NOT NULL,
    tax_line NVARCHAR(255) NULL,
    active BIT CONSTRAINT df_financial_categories_active DEFAULT 1 NOT NULL,
    system BIT CONSTRAINT df_financial_categories_system DEFAULT 1 NOT NULL,
    CONSTRAINT pk_financial_categories PRIMARY KEY (id)
);

CREATE TABLE neutral_categories (
    id BIGINT IDENTITY(1,1) NOT NULL,
    category_key NVARCHAR(100) NOT NULL,
    label NVARCHAR(255) NOT NULL,
    direction NVARCHAR(20) NOT NULL,
    active BIT CONSTRAINT df_neutral_categories_active DEFAULT 1 NOT NULL,
    system BIT CONSTRAINT df_neutral_categories_system DEFAULT 1 NOT NULL,
    CONSTRAINT pk_neutral_categories PRIMARY KEY (id)
);

CREATE TABLE legacy_category_map (
    legacy_category_id BIGINT NOT NULL,
    neutral_category_id BIGINT NOT NULL,
    legacy_category_key NVARCHAR(100) NOT NULL,
    CONSTRAINT pk_legacy_category_map PRIMARY KEY (legacy_category_id)
);

CREATE TABLE activity_reporting_profile_assignments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    activity_id BIGINT NOT NULL,
    reporting_profile_id BIGINT NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    open_activity_id AS (case when effective_to is null then activity_id else null end) PERSISTED,
    CONSTRAINT pk_activity_reporting_profile_assignments PRIMARY KEY (id)
);

CREATE TABLE category_reporting_mappings (
    id BIGINT IDENTITY(1,1) NOT NULL,
    neutral_category_id BIGINT NOT NULL,
    reporting_profile_id BIGINT NOT NULL,
    legacy_category_id BIGINT NOT NULL,
    report_line NVARCHAR(255) NULL,
    active BIT CONSTRAINT df_category_reporting_mappings_active DEFAULT 1 NOT NULL,
    CONSTRAINT pk_category_reporting_mappings PRIMARY KEY (id)
);

CREATE TABLE financial_transactions (
    id BIGINT IDENTITY(1,1) NOT NULL,
    amount DECIMAL(38, 2) NOT NULL,
    direction NVARCHAR(50) NOT NULL,
    transaction_date DATE NOT NULL,
    description NVARCHAR(2000) NOT NULL,
    activity_id BIGINT NOT NULL,
    neutral_category_id BIGINT NOT NULL,
    counterparty_id BIGINT NULL,
    created_at DATETIME2(6) CONSTRAINT df_financial_transactions_created_at DEFAULT SYSDATETIME() NOT NULL,
    updated_at DATETIME2(6) CONSTRAINT df_financial_transactions_updated_at DEFAULT SYSDATETIME() NOT NULL,
    version BIGINT CONSTRAINT df_financial_transactions_version DEFAULT 0 NOT NULL,
    deleted_at DATETIME2(6) NULL,
    CONSTRAINT pk_financial_transactions PRIMARY KEY (id)
);

CREATE TABLE transaction_attachments (
    id BIGINT IDENTITY(1,1) NOT NULL,
    transaction_id BIGINT NOT NULL,
    storage_provider NVARCHAR(50) NOT NULL,
    external_id NVARCHAR(255) NULL,
    file_name NVARCHAR(500) NULL,
    sha256 NVARCHAR(64) NULL,
    CONSTRAINT pk_transaction_attachments PRIMARY KEY (id)
);

CREATE TABLE inbox_artifacts (
    id BIGINT IDENTITY(1,1) NOT NULL,
    inbox_item_id BIGINT NOT NULL,
    type NVARCHAR(40) NOT NULL,
    text_value NVARCHAR(2000) NULL,
    external_id NVARCHAR(255) NULL,
    file_name NVARCHAR(500) NULL,
    sha256 NVARCHAR(64) NULL,
    CONSTRAINT pk_inbox_artifacts PRIMARY KEY (id)
);

CREATE TABLE inbox_identity_evidence (
    inbox_item_id BIGINT NOT NULL,
    snapshot_order INT NOT NULL,
    target_kind NVARCHAR(30) NOT NULL,
    candidate_rank INT NOT NULL,
    target_id BIGINT NOT NULL,
    target_name NVARCHAR(500) NOT NULL,
    property_id BIGINT NULL,
    property_name NVARCHAR(500) NULL,
    evidence_rank INT NOT NULL,
    evidence_type NVARCHAR(50) NOT NULL,
    evidence_strength NVARCHAR(30) NOT NULL,
    occurrences INT NOT NULL,
    total_occurrences INT NOT NULL,
    CONSTRAINT pk_inbox_identity_evidence PRIMARY KEY (inbox_item_id, snapshot_order)
);

-- Unique keys. SQL Server treats NULLs as equal in UNIQUE constraints, so keys on nullable
-- columns become filtered unique indexes to keep H2 NULLS DISTINCT semantics.

ALTER TABLE background_jobs ADD CONSTRAINT uk_background_jobs_idempotency UNIQUE (idempotency_key);

ALTER TABLE email_keyword_payer_history ADD CONSTRAINT uk_ekpayh_keyword_payer UNIQUE (keyword, payer_id);

CREATE UNIQUE INDEX uk_inbox_items_migration_source ON inbox_items (migration_legacy_table, migration_legacy_id) WHERE migration_legacy_table IS NOT NULL AND migration_legacy_id IS NOT NULL;

ALTER TABLE activity_reporting_profile_assignments ADD CONSTRAINT uk_activity_reporting_profile_start UNIQUE (activity_id, effective_from);

CREATE UNIQUE INDEX uk_inbox_items_origin_identity ON inbox_items (origin, legacy_source_id) WHERE origin IS NOT NULL AND legacy_source_id IS NOT NULL;

ALTER TABLE email_keyword_property_history ADD CONSTRAINT uk_ekph_keyword_property UNIQUE (keyword, property_id);

ALTER TABLE receipt_hashes ADD CONSTRAINT uk_receipt_hashes_sha256 UNIQUE (sha256);

ALTER TABLE email_keyword_classification_history ADD CONSTRAINT uk_email_keyword_classification UNIQUE (keyword, activity_id, financial_category_id);

ALTER TABLE category_reporting_mappings ADD CONSTRAINT uk_category_reporting_mapping_policy UNIQUE (neutral_category_id, reporting_profile_id);

CREATE UNIQUE INDEX uk_financial_activities_system_key ON financial_activities (system_key) WHERE system_key IS NOT NULL;

ALTER TABLE legacy_category_map ADD CONSTRAINT uk_legacy_category_map_key UNIQUE (legacy_category_key);

-- Filtered indexes cannot filter on a computed column; effective_to IS NULL selects exactly the
-- rows whose open_activity_id is non-null.
CREATE UNIQUE INDEX uk_activity_reporting_profile_open ON activity_reporting_profile_assignments (open_activity_id) WHERE effective_to IS NULL;

CREATE UNIQUE INDEX uk_transaction_import_references_external ON transaction_import_references (origin, external_id) WHERE origin IS NOT NULL AND external_id IS NOT NULL;

ALTER TABLE category_reporting_mappings ADD CONSTRAINT uk_category_reporting_mapping_legacy UNIQUE (legacy_category_id);

ALTER TABLE reporting_profiles ADD CONSTRAINT uk_reporting_profiles_key UNIQUE (profile_key);

CREATE UNIQUE INDEX uk_household_members_system_key ON household_members (system_key) WHERE system_key IS NOT NULL;

ALTER TABLE financial_categories ADD CONSTRAINT uk_financial_categories_key UNIQUE (category_key);

ALTER TABLE payer_property_history ADD CONSTRAINT uk_payer_property_history UNIQUE (payer_id, property_id);

ALTER TABLE financial_activities ADD CONSTRAINT uk_financial_activities_name UNIQUE (name);

CREATE UNIQUE INDEX uk_financial_activities_property ON financial_activities (property_id) WHERE property_id IS NOT NULL;

ALTER TABLE email_keyword_category_history ADD CONSTRAINT uk_ekch_keyword_category UNIQUE (keyword, category);

ALTER TABLE household_members ADD CONSTRAINT uk_household_members_name UNIQUE (name);

ALTER TABLE neutral_categories ADD CONSTRAINT uk_neutral_categories_key UNIQUE (category_key);

ALTER TABLE parsed_email_keywords ADD CONSTRAINT uk_parsed_email_keywords UNIQUE (source_id, keyword);

ALTER TABLE payer_category_history ADD CONSTRAINT uk_payer_category_history UNIQUE (payer_id, category);

-- Check constraints

ALTER TABLE properties ADD CONSTRAINT ck_properties_type CHECK (type IN (N'COMMERCIAL', N'CONDO', N'MULTI_FAMILY', N'OTHER', N'SINGLE_FAMILY', N'TOWNHOUSE'));

ALTER TABLE counterparties ADD CONSTRAINT ck_counterparties_type CHECK (type IN (N'COMPANY', N'PERSON'));

ALTER TABLE payer_category_history ADD CONSTRAINT ck_payer_category_history_category CHECK (category IN (N'ADVERTISING', N'AUTO_AND_TRAVEL', N'CLEANING_AND_MAINTENANCE', N'COMMISSIONS', N'DEPRECIATION', N'INSURANCE', N'LEGAL_AND_PROFESSIONAL', N'MANAGEMENT_FEES', N'MORTGAGE_INTEREST', N'OTHER', N'OTHER_INTEREST', N'REPAIRS', N'SUPPLIES', N'TAXES', N'UTILITIES'));

ALTER TABLE email_keyword_category_history ADD CONSTRAINT ck_email_keyword_category_history_category CHECK (category IN (N'ADVERTISING', N'AUTO_AND_TRAVEL', N'CLEANING_AND_MAINTENANCE', N'COMMISSIONS', N'DEPRECIATION', N'INSURANCE', N'LEGAL_AND_PROFESSIONAL', N'MANAGEMENT_FEES', N'MORTGAGE_INTEREST', N'OTHER', N'OTHER_INTEREST', N'REPAIRS', N'SUPPLIES', N'TAXES', N'UTILITIES'));

ALTER TABLE financial_transactions ADD CONSTRAINT ck_financial_transactions_direction CHECK (direction IN (N'INCOME', N'EXPENSE'));

ALTER TABLE inbox_artifacts ADD CONSTRAINT ck_inbox_artifacts_sha256 CHECK (((sha256 is null) or (len(sha256) = 64)));

ALTER TABLE inbox_identity_evidence ADD CONSTRAINT ck_inbox_identity_occurrences CHECK (((occurrences > 0) and (total_occurrences >= occurrences)));

ALTER TABLE background_jobs ADD CONSTRAINT ck_background_jobs_subject CHECK ((((inbox_item_id is not null) and (source_external_id is null)) or ((inbox_item_id is null) and (source_external_id is not null) and (type = N'MOVE_RECEIPT'))));

ALTER TABLE activity_reporting_profile_assignments ADD CONSTRAINT ck_activity_reporting_profile_range CHECK (((effective_to is null) or (effective_to >= effective_from)));

ALTER TABLE financial_transactions ADD CONSTRAINT ck_financial_transactions_amount CHECK ((amount > 0));

ALTER TABLE transaction_attachments ADD CONSTRAINT ck_transaction_attachments_identity CHECK (((external_id is not null) or (file_name is not null) or (sha256 is not null)));

ALTER TABLE background_jobs ADD CONSTRAINT ck_background_jobs_attempts CHECK (((attempts >= 0) and (max_attempts > 0) and (execution_attempt_base >= 0)));

-- Foreign keys

ALTER TABLE outlook_settings_folder ADD CONSTRAINT fk_outlook_settings_folder_activity FOREIGN KEY (activity_id) REFERENCES financial_activities (id) ON DELETE SET NULL;

ALTER TABLE email_keyword_property_history ADD CONSTRAINT fk_ekph_property FOREIGN KEY (property_id) REFERENCES properties (id);

ALTER TABLE background_jobs ADD CONSTRAINT fk_background_jobs_item FOREIGN KEY (inbox_item_id) REFERENCES inbox_items (id);

ALTER TABLE counterparty_accounts ADD CONSTRAINT fk_counterparty_account_counterparty FOREIGN KEY (counterparty_id) REFERENCES counterparties (id);

ALTER TABLE outlook_settings_folder ADD CONSTRAINT fk_osf_settings FOREIGN KEY (settings_id) REFERENCES outlook_settings (id);

ALTER TABLE email_keyword_payer_history ADD CONSTRAINT fk_ekpayh_payer FOREIGN KEY (payer_id) REFERENCES counterparties (id);

ALTER TABLE payer_property_history ADD CONSTRAINT fk_pph_property FOREIGN KEY (property_id) REFERENCES properties (id);

ALTER TABLE legacy_category_map ADD CONSTRAINT fk_legacy_category_map_legacy FOREIGN KEY (legacy_category_id) REFERENCES financial_categories (id);

ALTER TABLE financial_activities ADD CONSTRAINT fk_financial_activities_property FOREIGN KEY (property_id) REFERENCES properties (id);

ALTER TABLE email_keyword_classification_history ADD CONSTRAINT fk_email_keyword_classification_category FOREIGN KEY (financial_category_id) REFERENCES financial_categories (id) ON DELETE CASCADE;

ALTER TABLE inbox_items ADD CONSTRAINT fk_inbox_items_selected_activity FOREIGN KEY (selected_activity_id) REFERENCES financial_activities (id) ON DELETE SET NULL;

ALTER TABLE inbox_items ADD CONSTRAINT fk_inbox_items_selected_counterparty FOREIGN KEY (selected_counterparty_id) REFERENCES counterparties (id) ON DELETE SET NULL;

ALTER TABLE property_accounts ADD CONSTRAINT fk_property_accounts_property FOREIGN KEY (property_id) REFERENCES properties (id);

ALTER TABLE outlook_settings_receipts_import_folder ADD CONSTRAINT fk_osrif_settings FOREIGN KEY (settings_id) REFERENCES outlook_settings (id);

ALTER TABLE inbox_items ADD CONSTRAINT fk_inbox_items_financial_transaction FOREIGN KEY (financial_transaction_id) REFERENCES financial_transactions (id);

ALTER TABLE payer_property_history ADD CONSTRAINT fk_pph_payer FOREIGN KEY (payer_id) REFERENCES counterparties (id);

ALTER TABLE transaction_import_references ADD CONSTRAINT fk_transaction_import_references_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transactions (id);

ALTER TABLE activity_reporting_profile_assignments ADD CONSTRAINT fk_activity_reporting_profile_activity FOREIGN KEY (activity_id) REFERENCES financial_activities (id) ON DELETE CASCADE;

ALTER TABLE financial_activities ADD CONSTRAINT fk_financial_activities_owner FOREIGN KEY (owner_id) REFERENCES household_members (id);

ALTER TABLE category_reporting_mappings ADD CONSTRAINT fk_category_reporting_mapping_legacy FOREIGN KEY (legacy_category_id) REFERENCES financial_categories (id);

ALTER TABLE transaction_attachments ADD CONSTRAINT fk_transaction_attachments_transaction FOREIGN KEY (transaction_id) REFERENCES financial_transactions (id);

ALTER TABLE email_keyword_classification_history ADD CONSTRAINT fk_email_keyword_classification_activity FOREIGN KEY (activity_id) REFERENCES financial_activities (id) ON DELETE CASCADE;

ALTER TABLE category_reporting_mappings ADD CONSTRAINT fk_category_reporting_mapping_profile FOREIGN KEY (reporting_profile_id) REFERENCES reporting_profiles (id);

ALTER TABLE financial_transactions ADD CONSTRAINT fk_financial_transactions_activity FOREIGN KEY (activity_id) REFERENCES financial_activities (id);

ALTER TABLE financial_transactions ADD CONSTRAINT fk_financial_transactions_neutral_category FOREIGN KEY (neutral_category_id) REFERENCES neutral_categories (id);

ALTER TABLE payer_category_history ADD CONSTRAINT fk_pch_payer FOREIGN KEY (payer_id) REFERENCES counterparties (id);

ALTER TABLE inbox_identity_evidence ADD CONSTRAINT fk_inbox_identity_evidence FOREIGN KEY (inbox_item_id) REFERENCES inbox_items (id) ON DELETE CASCADE;

ALTER TABLE financial_transactions ADD CONSTRAINT fk_financial_transactions_counterparty FOREIGN KEY (counterparty_id) REFERENCES counterparties (id);

ALTER TABLE category_reporting_mappings ADD CONSTRAINT fk_category_reporting_mapping_neutral FOREIGN KEY (neutral_category_id) REFERENCES neutral_categories (id);

ALTER TABLE counterparty_aliases ADD CONSTRAINT fk_counterparty_alias_counterparty FOREIGN KEY (counterparty_id) REFERENCES counterparties (id);

ALTER TABLE inbox_artifacts ADD CONSTRAINT fk_inbox_artifacts_item FOREIGN KEY (inbox_item_id) REFERENCES inbox_items (id);

ALTER TABLE legacy_category_map ADD CONSTRAINT fk_legacy_category_map_neutral FOREIGN KEY (neutral_category_id) REFERENCES neutral_categories (id);

ALTER TABLE activity_reporting_profile_assignments ADD CONSTRAINT fk_activity_reporting_profile_profile FOREIGN KEY (reporting_profile_id) REFERENCES reporting_profiles (id);

-- Indexes

CREATE INDEX idx_counterparty_account_value ON counterparty_accounts (account_number);

CREATE INDEX idx_counterparty_alias_value ON counterparty_aliases (alias);

CREATE INDEX idx_inbox_items_state ON inbox_items (state);

CREATE INDEX idx_inbox_items_external_sync_state ON inbox_items (external_sync_state);

CREATE INDEX idx_inbox_items_created_at ON inbox_items (created_at);

CREATE INDEX idx_inbox_items_selected_activity ON inbox_items (selected_activity_id);

CREATE INDEX idx_inbox_items_selected_counterparty ON inbox_items (selected_counterparty_id);

CREATE INDEX idx_email_keyword_classification_keyword ON email_keyword_classification_history (keyword);

CREATE INDEX idx_email_keyword_classification_activity ON email_keyword_classification_history (activity_id);

CREATE INDEX idx_background_jobs_claim ON background_jobs (state, available_at, lease_expires_at);

CREATE INDEX idx_background_jobs_item ON background_jobs (inbox_item_id);

CREATE INDEX idx_background_jobs_execution ON background_jobs (execution_id);

CREATE INDEX idx_transaction_import_references_transaction ON transaction_import_references (transaction_id);

CREATE INDEX idx_tir_origin_parent ON transaction_import_references (origin, parent_external_id);

CREATE INDEX idx_legacy_category_map_neutral ON legacy_category_map (neutral_category_id);

CREATE INDEX idx_activity_reporting_profile_effective ON activity_reporting_profile_assignments (activity_id, effective_from, effective_to);

CREATE INDEX idx_category_reporting_mapping_profile ON category_reporting_mappings (reporting_profile_id, neutral_category_id);

CREATE INDEX idx_financial_transactions_date ON financial_transactions (transaction_date desc);

CREATE INDEX idx_financial_transactions_direction_date ON financial_transactions (direction, transaction_date desc);

CREATE INDEX idx_financial_transactions_activity ON financial_transactions (activity_id);

CREATE INDEX idx_financial_transactions_category ON financial_transactions (neutral_category_id);

CREATE INDEX idx_financial_transactions_counterparty ON financial_transactions (counterparty_id);

CREATE INDEX idx_transaction_attachments_transaction ON transaction_attachments (transaction_id);

CREATE INDEX ix_transaction_attachments_external ON transaction_attachments (storage_provider, external_id);

CREATE INDEX idx_inbox_artifacts_item ON inbox_artifacts (inbox_item_id);

-- text_value is NVARCHAR(2000), beyond the 1700-byte index key limit, so it is an included column.
CREATE INDEX idx_inbox_artifacts_outlook_parent ON inbox_artifacts (type) INCLUDE (text_value);

CREATE INDEX idx_inbox_identity_target ON inbox_identity_evidence (target_kind, target_id);

-- Reference data

SET IDENTITY_INSERT reporting_profiles ON;
INSERT INTO reporting_profiles (id, profile_key, label, active) VALUES
(1, N'SCHEDULE_E', N'Schedule E', 1),
(2, N'SCHEDULE_C', N'Schedule C', 1),
(3, N'W2', N'W-2 employment', 1),
(4, N'NONE', N'No tax report', 1);
SET IDENTITY_INSERT reporting_profiles OFF;

SET IDENTITY_INSERT household_members ON;
INSERT INTO household_members (id, name, active, system_key) VALUES
(1, N'Household', 1, N'DEFAULT_HOUSEHOLD');
SET IDENTITY_INSERT household_members OFF;

SET IDENTITY_INSERT financial_activities ON;
INSERT INTO financial_activities (id, name, activity_type, tax_treatment, owner_id, property_id, active, system_key) VALUES
(1, N'Needs classification', N'OTHER', N'NONE', 1, NULL, 1, N'NEEDS_CLASSIFICATION');
SET IDENTITY_INSERT financial_activities OFF;

SET IDENTITY_INSERT financial_categories ON;
INSERT INTO financial_categories (id, category_key, label, direction, tax_treatment, tax_line, active, system) VALUES
(1, N'RENTAL_INCOME', N'Rental Income', N'INCOME', N'SCHEDULE_E', N'Rents received', 1, 1),
(2, N'ADVERTISING', N'Advertising', N'EXPENSE', N'SCHEDULE_E', N'5', 1, 1),
(3, N'AUTO_AND_TRAVEL', N'Auto and travel', N'EXPENSE', N'SCHEDULE_E', N'6', 1, 1),
(4, N'CLEANING_AND_MAINTENANCE', N'Cleaning and maintenance', N'EXPENSE', N'SCHEDULE_E', N'7', 1, 1),
(5, N'COMMISSIONS', N'Commissions', N'EXPENSE', N'SCHEDULE_E', N'8', 1, 1),
(6, N'INSURANCE', N'Insurance', N'EXPENSE', N'SCHEDULE_E', N'9', 1, 1),
(7, N'LEGAL_AND_PROFESSIONAL', N'Legal and professional fees', N'EXPENSE', N'SCHEDULE_E', N'10', 1, 1),
(8, N'MANAGEMENT_FEES', N'Management fees', N'EXPENSE', N'SCHEDULE_E', N'11', 1, 1),
(9, N'MORTGAGE_INTEREST', N'Mortgage interest', N'EXPENSE', N'SCHEDULE_E', N'12', 1, 1),
(10, N'OTHER_INTEREST', N'Other interest', N'EXPENSE', N'SCHEDULE_E', N'13', 1, 1),
(11, N'REPAIRS', N'Repairs', N'EXPENSE', N'SCHEDULE_E', N'14', 1, 1),
(12, N'SUPPLIES', N'Supplies', N'EXPENSE', N'SCHEDULE_E', N'15', 1, 1),
(13, N'TAXES', N'Taxes', N'EXPENSE', N'SCHEDULE_E', N'16', 1, 1),
(14, N'UTILITIES', N'Utilities', N'EXPENSE', N'SCHEDULE_E', N'17', 1, 1),
(15, N'DEPRECIATION', N'Depreciation', N'EXPENSE', N'SCHEDULE_E', N'18', 1, 1),
(16, N'OTHER', N'Other', N'EXPENSE', N'SCHEDULE_E', N'19', 1, 1),
(17, N'WAGES', N'Wages', N'INCOME', N'W2', NULL, 1, 1),
(18, N'REIMBURSEMENT', N'Reimbursement', N'INCOME', N'W2', NULL, 1, 1),
(19, N'EDUCATOR_EXPENSE', N'Educator Expense', N'EXPENSE', N'W2', N'Schedule 1 educator expense', 1, 1),
(20, N'EMPLOYMENT_OTHER_EXPENSE', N'Other employment expense', N'EXPENSE', N'W2', NULL, 1, 1),
(21, N'SERVICE_INCOME', N'Service Income', N'INCOME', N'SCHEDULE_C', NULL, 1, 1),
(22, N'SCHEDULE_C_ADVERTISING', N'Advertising', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(23, N'SCHEDULE_C_CAR_AND_TRUCK', N'Car and truck expense', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(24, N'SCHEDULE_C_CONTRACT_LABOR', N'Contract labor', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(25, N'SCHEDULE_C_INSURANCE', N'Insurance', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(26, N'SCHEDULE_C_LEGAL_AND_PROFESSIONAL', N'Legal and professional services', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(27, N'SCHEDULE_C_OFFICE_EXPENSE', N'Office expense', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(28, N'SCHEDULE_C_REPAIRS', N'Repairs and maintenance', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(29, N'SCHEDULE_C_SUPPLIES', N'Supplies', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(30, N'SCHEDULE_C_UTILITIES', N'Utilities', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(31, N'SCHEDULE_C_OTHER_EXPENSE', N'Other business expense', N'EXPENSE', N'SCHEDULE_C', NULL, 1, 1),
(32, N'OTHER_INCOME', N'Other Income', N'INCOME', N'NONE', NULL, 1, 1),
(33, N'OTHER_REIMBURSEMENT', N'Reimbursement', N'INCOME', N'NONE', NULL, 1, 1),
(34, N'OTHER_EXPENSE', N'Other Expense', N'EXPENSE', N'NONE', NULL, 1, 1);
SET IDENTITY_INSERT financial_categories OFF;

SET IDENTITY_INSERT neutral_categories ON;
INSERT INTO neutral_categories (id, category_key, label, direction, active, system) VALUES
(1, N'RENTAL_INCOME', N'Rental Income', N'INCOME', 1, 1),
(2, N'ADVERTISING', N'Advertising', N'EXPENSE', 1, 1),
(3, N'AUTO_AND_TRAVEL', N'Auto and travel', N'EXPENSE', 1, 1),
(4, N'CLEANING_AND_MAINTENANCE', N'Cleaning and maintenance', N'EXPENSE', 1, 1),
(5, N'COMMISSIONS', N'Commissions', N'EXPENSE', 1, 1),
(6, N'INSURANCE', N'Insurance', N'EXPENSE', 1, 1),
(7, N'LEGAL_AND_PROFESSIONAL', N'Legal and professional fees', N'EXPENSE', 1, 1),
(8, N'MANAGEMENT_FEES', N'Management fees', N'EXPENSE', 1, 1),
(9, N'MORTGAGE_INTEREST', N'Mortgage interest', N'EXPENSE', 1, 1),
(10, N'OTHER_INTEREST', N'Other interest', N'EXPENSE', 1, 1),
(11, N'REPAIRS_AND_MAINTENANCE', N'Repairs and maintenance', N'EXPENSE', 1, 1),
(12, N'SUPPLIES', N'Supplies', N'EXPENSE', 1, 1),
(13, N'TAXES', N'Taxes', N'EXPENSE', 1, 1),
(14, N'UTILITIES', N'Utilities', N'EXPENSE', 1, 1),
(15, N'DEPRECIATION', N'Depreciation', N'EXPENSE', 1, 1),
(16, N'WAGES', N'Wages', N'INCOME', 1, 1),
(17, N'REIMBURSEMENT', N'Reimbursement', N'INCOME', 1, 1),
(18, N'EDUCATOR_EXPENSE', N'Educator Expense', N'EXPENSE', 1, 1),
(19, N'EMPLOYMENT_OTHER_EXPENSE', N'Other employment expense', N'EXPENSE', 1, 1),
(20, N'SERVICE_INCOME', N'Service Income', N'INCOME', 1, 1),
(21, N'SCHEDULE_C_CAR_AND_TRUCK', N'Car and truck expense', N'EXPENSE', 1, 1),
(22, N'SCHEDULE_C_CONTRACT_LABOR', N'Contract labor', N'EXPENSE', 1, 1),
(23, N'SCHEDULE_C_OFFICE_EXPENSE', N'Office expense', N'EXPENSE', 1, 1),
(24, N'OTHER_INCOME', N'Other Income', N'INCOME', 1, 1),
(25, N'OTHER_EXPENSE', N'Other Expense', N'EXPENSE', 1, 1);
SET IDENTITY_INSERT neutral_categories OFF;

INSERT INTO legacy_category_map (legacy_category_id, neutral_category_id, legacy_category_key) VALUES
(1, 1, N'RENTAL_INCOME'),
(2, 2, N'ADVERTISING'),
(3, 3, N'AUTO_AND_TRAVEL'),
(4, 4, N'CLEANING_AND_MAINTENANCE'),
(5, 5, N'COMMISSIONS'),
(6, 6, N'INSURANCE'),
(7, 7, N'LEGAL_AND_PROFESSIONAL'),
(8, 8, N'MANAGEMENT_FEES'),
(9, 9, N'MORTGAGE_INTEREST'),
(10, 10, N'OTHER_INTEREST'),
(11, 11, N'REPAIRS'),
(12, 12, N'SUPPLIES'),
(13, 13, N'TAXES'),
(14, 14, N'UTILITIES'),
(15, 15, N'DEPRECIATION'),
(16, 25, N'OTHER'),
(17, 16, N'WAGES'),
(18, 17, N'REIMBURSEMENT'),
(19, 18, N'EDUCATOR_EXPENSE'),
(20, 19, N'EMPLOYMENT_OTHER_EXPENSE'),
(21, 20, N'SERVICE_INCOME'),
(22, 2, N'SCHEDULE_C_ADVERTISING'),
(23, 21, N'SCHEDULE_C_CAR_AND_TRUCK'),
(24, 22, N'SCHEDULE_C_CONTRACT_LABOR'),
(25, 6, N'SCHEDULE_C_INSURANCE'),
(26, 7, N'SCHEDULE_C_LEGAL_AND_PROFESSIONAL'),
(27, 23, N'SCHEDULE_C_OFFICE_EXPENSE'),
(28, 11, N'SCHEDULE_C_REPAIRS'),
(29, 12, N'SCHEDULE_C_SUPPLIES'),
(30, 14, N'SCHEDULE_C_UTILITIES'),
(31, 25, N'SCHEDULE_C_OTHER_EXPENSE'),
(32, 24, N'OTHER_INCOME'),
(33, 17, N'OTHER_REIMBURSEMENT'),
(34, 25, N'OTHER_EXPENSE');

SET IDENTITY_INSERT activity_reporting_profile_assignments ON;
INSERT INTO activity_reporting_profile_assignments (id, activity_id, reporting_profile_id, effective_from, effective_to) VALUES
(1, 1, 4, N'0001-01-01', NULL);
SET IDENTITY_INSERT activity_reporting_profile_assignments OFF;

SET IDENTITY_INSERT category_reporting_mappings ON;
INSERT INTO category_reporting_mappings (id, neutral_category_id, reporting_profile_id, legacy_category_id, report_line, active) VALUES
(1, 1, 1, 1, N'Rents received', 1),
(2, 2, 1, 2, N'5', 1),
(3, 2, 2, 22, NULL, 1),
(4, 3, 1, 3, N'6', 1),
(5, 4, 1, 4, N'7', 1),
(6, 5, 1, 5, N'8', 1),
(7, 6, 1, 6, N'9', 1),
(8, 6, 2, 25, NULL, 1),
(9, 7, 1, 7, N'10', 1),
(10, 7, 2, 26, NULL, 1),
(11, 8, 1, 8, N'11', 1),
(12, 9, 1, 9, N'12', 1),
(13, 10, 1, 10, N'13', 1),
(14, 11, 1, 11, N'14', 1),
(15, 11, 2, 28, NULL, 1),
(16, 12, 1, 12, N'15', 1),
(17, 12, 2, 29, NULL, 1),
(18, 13, 1, 13, N'16', 1),
(19, 14, 1, 14, N'17', 1),
(20, 14, 2, 30, NULL, 1),
(21, 15, 1, 15, N'18', 1),
(22, 16, 3, 17, NULL, 1),
(23, 17, 3, 18, NULL, 1),
(24, 17, 4, 33, NULL, 1),
(25, 18, 3, 19, N'Schedule 1 educator expense', 1),
(26, 19, 3, 20, NULL, 1),
(27, 20, 2, 21, NULL, 1),
(28, 21, 2, 23, NULL, 1),
(29, 22, 2, 24, NULL, 1),
(30, 23, 2, 27, NULL, 1),
(31, 24, 4, 32, NULL, 1),
(32, 25, 1, 16, N'19', 1),
(33, 25, 2, 31, NULL, 1),
(34, 25, 4, 34, NULL, 1);
SET IDENTITY_INSERT category_reporting_mappings OFF;
GO

-- Mirrors NonOverlappingReportingProfileAssignmentTrigger: an activity's reporting profile
-- assignments must not overlap in time.
CREATE TRIGGER enforce_activity_reporting_profile_non_overlap
ON activity_reporting_profile_assignments
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF EXISTS (
        SELECT 1
        FROM inserted candidate
        JOIN activity_reporting_profile_assignments existing
          ON existing.activity_id = candidate.activity_id
         AND existing.id <> candidate.id
         AND existing.effective_from <= COALESCE(candidate.effective_to, '9999-12-31')
         AND COALESCE(existing.effective_to, '9999-12-31') >= candidate.effective_from
    )
    BEGIN
        THROW 50001, 'Reporting profile assignments for an activity must not overlap', 1;
    END
END;
GO

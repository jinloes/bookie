CREATE TABLE counterparties (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50)
);

CREATE TABLE counterparty_aliases (
    counterparty_id BIGINT NOT NULL,
    alias VARCHAR(255) NOT NULL,
    CONSTRAINT fk_counterparty_alias_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES counterparties(id)
);

CREATE TABLE counterparty_accounts (
    counterparty_id BIGINT NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    CONSTRAINT fk_counterparty_account_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES counterparties(id)
);

CREATE TABLE legacy_payer_map (
    payer_id BIGINT PRIMARY KEY,
    counterparty_id BIGINT NOT NULL,
    CONSTRAINT uk_legacy_payer_map_counterparty UNIQUE (counterparty_id),
    CONSTRAINT fk_legacy_payer_map_payer
        FOREIGN KEY (payer_id) REFERENCES payers(id),
    CONSTRAINT fk_legacy_payer_map_counterparty
        FOREIGN KEY (counterparty_id) REFERENCES counterparties(id)
);

INSERT INTO counterparties (id, name, type)
SELECT id, name, type
FROM payers;

INSERT INTO counterparty_aliases (counterparty_id, alias)
SELECT payer_id, alias
FROM payer_aliases;

INSERT INTO counterparty_accounts (counterparty_id, account_number)
SELECT payer_id, account_number
FROM payer_accounts;

INSERT INTO legacy_payer_map (payer_id, counterparty_id)
SELECT id, id
FROM payers;

SELECT 1 / CASE WHEN
    (SELECT COUNT(*) FROM payers) = (SELECT COUNT(*) FROM counterparties)
    AND (SELECT COUNT(*) FROM payers) = (SELECT COUNT(*) FROM legacy_payer_map)
    AND (SELECT COUNT(*) FROM payer_aliases) = (SELECT COUNT(*) FROM counterparty_aliases)
    AND (SELECT COUNT(*) FROM payer_accounts) = (SELECT COUNT(*) FROM counterparty_accounts)
THEN 1 ELSE 0 END;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM payers legacy
JOIN legacy_payer_map mapping ON mapping.payer_id = legacy.id
JOIN counterparties counterparty ON counterparty.id = mapping.counterparty_id
WHERE legacy.id <> counterparty.id
   OR legacy.name IS DISTINCT FROM counterparty.name
   OR legacy.type IS DISTINCT FROM counterparty.type;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM payer_aliases legacy
LEFT JOIN counterparty_aliases normalized
  ON normalized.counterparty_id = legacy.payer_id
 AND normalized.alias = legacy.alias
WHERE normalized.counterparty_id IS NULL;

SELECT 1 / CASE WHEN COUNT(*) = 0 THEN 1 ELSE 0 END
FROM payer_accounts legacy
LEFT JOIN counterparty_accounts normalized
  ON normalized.counterparty_id = legacy.payer_id
 AND normalized.account_number = legacy.account_number
WHERE normalized.counterparty_id IS NULL;

CREATE INDEX idx_counterparty_alias_value ON counterparty_aliases(alias);
CREATE INDEX idx_counterparty_account_value ON counterparty_accounts(account_number);

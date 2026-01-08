CREATE TABLE IF NOT EXISTS accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    balance NUMERIC(19,2) NOT NULL
);

CREATE TABLE IF NOT EXISTS transaction_staging (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    txn_id VARCHAR(100) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    processed_flag BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_txn_id UNIQUE (txn_id)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    reference VARCHAR(100),
    payload TEXT,
    created_at TIMESTAMP NOT NULL
);



CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);
CREATE INDEX IF NOT EXISTS idx_tx_staging_processed ON transaction_staging(processed_flag);



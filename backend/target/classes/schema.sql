CREATE TABLE accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    balance NUMERIC(19,2) NOT NULL
);

CREATE TABLE transaction_staging (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    txn_id VARCHAR(100) NOT NULL,
    account_number VARCHAR(50) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    processed_flag BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_txn_id UNIQUE (txn_id)
);

CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    reference VARCHAR(100),
    payload CLOB,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE job_run_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(50),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    details CLOB
);

CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_tx_staging_processed ON transaction_staging(processed_flag);



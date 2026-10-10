
-- Customers
CREATE SEQUENCE customer_seq
    START WITH 1000000 INCREMENT BY 1;

CREATE TABLE customers (
    customer_id BIGINT PRIMARY KEY DEFAULT nextval('customer_seq'),
    customer_number VARCHAR(50) NOT NULL UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Accounts
CREATE SEQUENCE account_seq
    START WITH 5000000 INCREMENT BY 1;

CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY DEFAULT nextval('account_seq'),
    customer_id BIGINT NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_type VARCHAR(50) NOT NULL DEFAULT 'CHECKING',
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_accounts_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_accounts_customer_id
ON accounts(customer_id);

-- Transactions
CREATE SEQUENCE transaction_seq
    START WITH 2000000 INCREMENT BY 1;

CREATE TABLE transactions (
    transaction_id BIGINT PRIMARY KEY DEFAULT nextval('transaction_seq'),
    account_id BIGINT NOT NULL,
    reference VARCHAR(50) NOT NULL UNIQUE,
    merchant VARCHAR(255) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'ZAR',
    mandate_type VARCHAR(50) NOT NULL DEFAULT 'STANDARD_EFT',
    status VARCHAR(50) NOT NULL DEFAULT 'COMPLETED',
    transaction_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id)
        REFERENCES accounts(account_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_transactions_account_id
    ON transactions(account_id);

CREATE INDEX idx_transactions_transaction_date
    ON transactions(transaction_date);

-- Disputes
CREATE SEQUENCE dispute_seq
    START WITH 3000000 INCREMENT BY 1;

CREATE TABLE disputes (
    dispute_id BIGINT PRIMARY KEY DEFAULT nextval('dispute_seq'),
    dispute_reference VARCHAR(50) NOT NULL UNIQUE,
    transaction_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    reason VARCHAR(50) NOT NULL,
    description TEXT,
    risk_score INTEGER NOT NULL,
    decision_route VARCHAR(50) NOT NULL,
    sqs_message_id VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_disputes_transaction
        FOREIGN KEY (transaction_id)
        REFERENCES transactions(transaction_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_disputes_customer
        FOREIGN KEY (user_id)
        REFERENCES customers(customer_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_disputes_user_id ON disputes(user_id);
CREATE INDEX idx_disputes_status ON disputes(status);
CREATE INDEX idx_disputes_created_at ON disputes(created_at);

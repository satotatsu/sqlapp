CREATE TABLE accounts_receivable (
    customer_id BIGINT PRIMARY KEY,
    balance_amount DECIMAL(15,2) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_accounts_receivable_customer
        FOREIGN KEY(customer_id)
        REFERENCES customers(customer_id)
);
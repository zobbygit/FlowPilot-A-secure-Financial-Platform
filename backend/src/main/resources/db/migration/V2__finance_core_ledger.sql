-- V2: finance core. NOT yet exposed by any API (see docs/STATUS.md) - the schema is fixed first
-- because the ledger model is the most expensive thing to change later.
--
-- Ledger model (documented in docs/ARCHITECTURE.md):
--  * A transaction is immutable once posted. Corrections are reversals + new postings.
--  * Every balance change is a ledger_entries row (signed amount, one account each).
--    A transfer = one transaction with two entries that sum to zero (same currency).
--  * accounts.current_balance is a denormalised cache of SUM(ledger_entries.amount), updated in the
--    same DB transaction under a row lock, and verifiable by a reconciliation query.

CREATE TABLE categories (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid REFERENCES users (id) ON DELETE RESTRICT,   -- NULL = platform default
    name        varchar(80)  NOT NULL,
    kind        varchar(10)  NOT NULL,
    color       varchar(9),
    sort_order  integer      NOT NULL DEFAULT 0,
    archived_at timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT categories_kind_chk CHECK (kind IN ('INCOME', 'EXPENSE'))
);
CREATE UNIQUE INDEX ux_categories_owner_name ON categories
    (COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::uuid), lower(name), kind);

CREATE TABLE accounts (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name            varchar(80)   NOT NULL,
    type            varchar(10)   NOT NULL,
    currency        varchar(3)    NOT NULL,
    current_balance numeric(19,4) NOT NULL DEFAULT 0,
    allow_negative  boolean       NOT NULL DEFAULT false,
    archived_at     timestamptz,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT accounts_type_chk CHECK (type IN ('CASH', 'BANK', 'SAVINGS', 'WALLET')),
    CONSTRAINT accounts_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT accounts_balance_chk CHECK (allow_negative OR current_balance >= 0)
);
CREATE UNIQUE INDEX ux_accounts_user_name_active ON accounts (user_id, lower(name)) WHERE archived_at IS NULL;
CREATE INDEX ix_accounts_user ON accounts (user_id);

CREATE TABLE transactions (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    type                   varchar(16)  NOT NULL,
    status                 varchar(10)  NOT NULL DEFAULT 'POSTED',
    occurred_on            date         NOT NULL,
    description            varchar(200),
    merchant               varchar(120),
    category_id            uuid REFERENCES categories (id) ON DELETE RESTRICT,
    payment_method         varchar(40),
    notes                  varchar(2000),
    reference              varchar(80),
    tags                   varchar(500),
    reverses_transaction_id uuid REFERENCES transactions (id) ON DELETE RESTRICT,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    archived_at            timestamptz,
    CONSTRAINT transactions_type_chk CHECK (type IN
        ('INCOME', 'EXPENSE', 'TRANSFER', 'REFUND', 'ADJUSTMENT', 'OPENING_BALANCE')),
    CONSTRAINT transactions_status_chk CHECK (status IN ('POSTED', 'REVERSED'))
);
CREATE INDEX ix_transactions_user_date ON transactions (user_id, occurred_on DESC, created_at DESC);
CREATE INDEX ix_transactions_category ON transactions (category_id);
CREATE UNIQUE INDEX ux_transactions_one_reversal ON transactions (reverses_transaction_id)
    WHERE reverses_transaction_id IS NOT NULL;

CREATE TABLE ledger_entries (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id uuid          NOT NULL REFERENCES transactions (id) ON DELETE RESTRICT,
    account_id     uuid          NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    user_id        uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    amount         numeric(19,4) NOT NULL,
    currency       varchar(3)    NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ledger_amount_nonzero_chk CHECK (amount <> 0)
);
CREATE INDEX ix_ledger_account_time ON ledger_entries (account_id, created_at);
CREATE INDEX ix_ledger_transaction ON ledger_entries (transaction_id);

CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Server-side idempotency for retryable financial writes (payments, transfers, imports).
CREATE TABLE idempotency_keys (
    user_id      uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    key          varchar(80)  NOT NULL,
    operation    varchar(40)  NOT NULL,
    request_hash varchar(64)  NOT NULL,
    resource_id  uuid,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, key)
);

-- V11: statement reconciliation. Imports never modify existing transactions: they only record match decisions.
CREATE TABLE statement_imports (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    account_id    uuid         NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    file_sha256   varchar(64)  NOT NULL,
    original_name varchar(120),
    row_count     integer      NOT NULL,
    status        varchar(10)  NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT stmt_status_chk CHECK (status IN ('ACTIVE', 'DISCARDED'))
);
-- the same file cannot be imported twice into the same account while the first import is active
CREATE UNIQUE INDEX ux_stmt_file ON statement_imports (user_id, account_id, file_sha256) WHERE status = 'ACTIVE';
CREATE INDEX ix_stmt_user ON statement_imports (user_id, created_at DESC);

CREATE TABLE statement_rows (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    import_id              uuid          NOT NULL REFERENCES statement_imports (id) ON DELETE CASCADE,
    row_no                 integer       NOT NULL,
    entry_date             date,
    amount                 numeric(19,4),
    description            varchar(300),
    reference              varchar(80),
    validation_error       varchar(200),
    match_status           varchar(14)   NOT NULL,
    suggested_transaction_id uuid REFERENCES transactions (id) ON DELETE SET NULL,
    matched_transaction_id uuid REFERENCES transactions (id) ON DELETE RESTRICT,
    score                  integer,
    note                   varchar(200),
    decided_at             timestamptz,
    CONSTRAINT row_status_chk CHECK (match_status IN ('INVALID', 'UNMATCHED', 'SUGGESTED', 'CONFIRMED', 'REJECTED', 'POSSIBLE_DUPLICATE')),
    CONSTRAINT row_confirmed_chk CHECK ((match_status = 'CONFIRMED') = (matched_transaction_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_stmt_row ON statement_rows (import_id, row_no);
-- one recorded transaction can be confirmed against at most one statement row
CREATE UNIQUE INDEX ux_stmt_row_tx ON statement_rows (matched_transaction_id) WHERE matched_transaction_id IS NOT NULL;
CREATE INDEX ix_stmt_row_status ON statement_rows (import_id, match_status);

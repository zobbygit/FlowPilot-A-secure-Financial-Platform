-- V8: SIMULATED payments between FlowPilot users. No real money, banks or UPI rails are involved.
-- Authoritative state lives here; the WebSocket only tells browsers to re-read it.

CREATE TABLE payment_profiles (
    user_id            uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    handle             varchar(30) NOT NULL,
    receive_account_id uuid REFERENCES accounts (id) ON DELETE SET NULL,
    created_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT payment_handle_chk CHECK (handle ~ '^[a-z0-9][a-z0-9-]{2,29}$')
);
CREATE UNIQUE INDEX ux_payment_handle ON payment_profiles (handle);

CREATE TABLE payment_requests (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reference      varchar(16)   NOT NULL,
    requester_id   uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    target_user_id uuid REFERENCES users (id) ON DELETE RESTRICT,
    amount         numeric(19,4) NOT NULL,
    currency       varchar(3)    NOT NULL,
    note           varchar(200),
    status         varchar(10)   NOT NULL DEFAULT 'OPEN',
    expires_at     timestamptz   NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT preq_status_chk CHECK (status IN ('OPEN', 'PAID', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT preq_amount_chk CHECK (amount > 0),
    CONSTRAINT preq_not_self_chk CHECK (target_user_id IS NULL OR target_user_id <> requester_id)
);
CREATE UNIQUE INDEX ux_preq_reference ON payment_requests (reference);
CREATE INDEX ix_preq_requester ON payment_requests (requester_id, created_at DESC);
CREATE INDEX ix_preq_target ON payment_requests (target_user_id) WHERE target_user_id IS NOT NULL;

CREATE TABLE payments (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reference        varchar(16)   NOT NULL,
    request_id       uuid REFERENCES payment_requests (id) ON DELETE RESTRICT,
    payer_id         uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    payee_id         uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    payer_account_id uuid          NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    payee_account_id uuid REFERENCES accounts (id) ON DELETE RESTRICT,
    amount           numeric(19,4) NOT NULL,
    currency         varchar(3)    NOT NULL,
    note             varchar(200),
    status           varchar(10)   NOT NULL DEFAULT 'PENDING',
    failure_reason   varchar(200),
    payer_tx_id      uuid REFERENCES transactions (id) ON DELETE RESTRICT,
    payee_tx_id      uuid REFERENCES transactions (id) ON DELETE RESTRICT,
    expires_at       timestamptz   NOT NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pay_status_chk CHECK (status IN ('PENDING', 'SUCCESSFUL', 'FAILED', 'CANCELLED', 'REFUNDED')),
    CONSTRAINT pay_amount_chk CHECK (amount > 0),
    CONSTRAINT pay_not_self_chk CHECK (payer_id <> payee_id),
    -- money only moves for SUCCESSFUL/REFUNDED payments, and then both ledger links exist
    CONSTRAINT pay_ledger_link_chk CHECK ((status IN ('SUCCESSFUL', 'REFUNDED')) = (payer_tx_id IS NOT NULL AND payee_tx_id IS NOT NULL))
);
CREATE UNIQUE INDEX ux_payments_reference ON payments (reference);
-- one live payment per request: a second attempt to pay the same request is rejected by the database itself
CREATE UNIQUE INDEX ux_payments_active_request ON payments (request_id) WHERE request_id IS NOT NULL AND status IN ('PENDING', 'SUCCESSFUL', 'REFUNDED');
CREATE INDEX ix_payments_payer ON payments (payer_id, created_at DESC);
CREATE INDEX ix_payments_payee ON payments (payee_id, created_at DESC);
CREATE INDEX ix_payments_pending_expiry ON payments (expires_at) WHERE status = 'PENDING';

CREATE TABLE payment_events (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id    uuid        NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    from_status   varchar(10),
    to_status     varchar(10) NOT NULL,
    actor_user_id uuid REFERENCES users (id) ON DELETE RESTRICT,   -- NULL = system (e.g. expiry)
    note          varchar(300),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_payment_events ON payment_events (payment_id, created_at);
CREATE TRIGGER trg_payment_events_append_only BEFORE UPDATE OR DELETE ON payment_events FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- A refund is its own explicit financial operation with its own ledger transactions.
CREATE TABLE payment_refunds (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      uuid          NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    amount          numeric(19,4) NOT NULL,
    reason          varchar(300),
    initiated_by    uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    payee_tx_id     uuid          NOT NULL REFERENCES transactions (id) ON DELETE RESTRICT,   -- money leaving the original payee
    payer_tx_id     uuid          NOT NULL REFERENCES transactions (id) ON DELETE RESTRICT,   -- money returning to the original payer
    created_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT refund_amount_chk CHECK (amount > 0)
);
CREATE UNIQUE INDEX ux_refund_payment ON payment_refunds (payment_id);   -- full refunds only: at most one per payment

CREATE TABLE payment_disputes (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id      uuid         NOT NULL REFERENCES payments (id) ON DELETE RESTRICT,
    opened_by       uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    reason          varchar(500) NOT NULL,
    status          varchar(20)  NOT NULL DEFAULT 'OPEN',
    resolution_note varchar(500),
    resolved_by     uuid REFERENCES users (id) ON DELETE RESTRICT,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    resolved_at     timestamptz,
    CONSTRAINT dispute_status_chk CHECK (status IN ('OPEN', 'RESOLVED_REFUNDED', 'RESOLVED_REJECTED', 'WITHDRAWN'))
);
CREATE UNIQUE INDEX ux_dispute_open ON payment_disputes (payment_id) WHERE status = 'OPEN';
CREATE INDEX ix_dispute_status ON payment_disputes (status, created_at);

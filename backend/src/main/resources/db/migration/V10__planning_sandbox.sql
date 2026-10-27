-- V10: hypothetical scenarios. These tables hold ASSUMPTIONS only; nothing here ever touches balances or the ledger.
CREATE TABLE sandbox_scenarios (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name         varchar(80) NOT NULL,
    currency     varchar(3)  NOT NULL,
    horizon_days integer     NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT sandbox_horizon_chk CHECK (horizon_days IN (30, 60, 90))
);
CREATE INDEX ix_sandbox_user ON sandbox_scenarios (user_id, created_at DESC);

CREATE TABLE sandbox_events (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scenario_id uuid          NOT NULL REFERENCES sandbox_scenarios (id) ON DELETE CASCADE,
    position    integer       NOT NULL,
    kind        varchar(20)   NOT NULL,
    label       varchar(80)   NOT NULL,
    event_date  date,
    amount      numeric(19,4),
    bill_id     uuid REFERENCES bills (id) ON DELETE SET NULL,
    CONSTRAINT sbe_kind_chk CHECK (kind IN ('ONE_OFF', 'MONTHLY_CHANGE', 'CANCEL_BILL')),
    CONSTRAINT sbe_shape_chk CHECK ((kind = 'CANCEL_BILL' AND bill_id IS NOT NULL) OR (kind <> 'CANCEL_BILL' AND event_date IS NOT NULL AND amount IS NOT NULL AND amount <> 0))
);
CREATE UNIQUE INDEX ux_sbe_pos ON sandbox_events (scenario_id, position);

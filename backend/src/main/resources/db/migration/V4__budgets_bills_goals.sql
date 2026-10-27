-- V4: budgets, bills & subscriptions, savings goals.
-- Planned allocations (goal_contributions.kind = 'PLANNED') never touch the ledger.
-- Only kind = 'TRANSFERRED' rows reference a real ledger transaction.

CREATE TABLE budgets (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    household_id     uuid,                                   -- FK added with the household module
    name             varchar(80)   NOT NULL,
    period_type      varchar(10)   NOT NULL,
    start_date       date,
    end_date         date,
    category_id      uuid REFERENCES categories (id) ON DELETE RESTRICT,   -- NULL = all expenses
    limit_amount     numeric(19,4) NOT NULL,
    currency         varchar(3)    NOT NULL,
    rollover         boolean       NOT NULL DEFAULT false,
    alert_thresholds varchar(40)   NOT NULL DEFAULT '80,100',
    archived_at      timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT budgets_period_chk CHECK (period_type IN ('MONTHLY', 'CUSTOM')),
    CONSTRAINT budgets_limit_chk CHECK (limit_amount > 0),
    CONSTRAINT budgets_custom_dates_chk CHECK (period_type <> 'CUSTOM' OR (start_date IS NOT NULL AND end_date IS NOT NULL AND end_date >= start_date))
);
CREATE UNIQUE INDEX ux_budgets_monthly_scope ON budgets
    (user_id, COALESCE(category_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE period_type = 'MONTHLY' AND archived_at IS NULL AND household_id IS NULL;
CREATE INDEX ix_budgets_user ON budgets (user_id) WHERE archived_at IS NULL;

CREATE TABLE bills (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name                varchar(80)   NOT NULL,
    kind                varchar(12)   NOT NULL,
    amount              numeric(19,4) NOT NULL,
    currency            varchar(3)    NOT NULL,
    frequency           varchar(10)   NOT NULL,
    next_due_date       date          NOT NULL,
    status              varchar(14)   NOT NULL DEFAULT 'ACTIVE',
    category_id         uuid REFERENCES categories (id) ON DELETE RESTRICT,
    account_id          uuid REFERENCES accounts (id) ON DELETE RESTRICT,
    reminder_days_before integer      NOT NULL DEFAULT 3,
    notes               varchar(1000),
    last_paid_on        date,
    archived_at         timestamptz,
    created_at          timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT bills_kind_chk CHECK (kind IN ('BILL', 'SUBSCRIPTION')),
    CONSTRAINT bills_freq_chk CHECK (frequency IN ('WEEKLY', 'MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT bills_status_chk CHECK (status IN ('ACTIVE', 'PAUSED', 'CANCELLED', 'NEEDS_REVIEW')),
    CONSTRAINT bills_amount_chk CHECK (amount > 0),
    CONSTRAINT bills_reminder_chk CHECK (reminder_days_before BETWEEN 0 AND 60)
);
CREATE INDEX ix_bills_user_due ON bills (user_id, next_due_date) WHERE archived_at IS NULL;

CREATE TABLE bill_price_changes (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bill_id    uuid          NOT NULL REFERENCES bills (id) ON DELETE CASCADE,
    old_amount numeric(19,4) NOT NULL,
    new_amount numeric(19,4) NOT NULL,
    changed_at timestamptz   NOT NULL DEFAULT now()
);
CREATE INDEX ix_bill_price_changes_bill ON bill_price_changes (bill_id, changed_at DESC);

CREATE TABLE savings_goals (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name                   varchar(80)   NOT NULL,
    goal_type              varchar(12)   NOT NULL,
    target_amount          numeric(19,4) NOT NULL,
    target_date            date,
    currency               varchar(3)    NOT NULL,
    contribution_amount    numeric(19,4),
    contribution_frequency varchar(10),
    status                 varchar(10)   NOT NULL DEFAULT 'ACTIVE',
    created_at             timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT goals_type_chk CHECK (goal_type IN ('EMERGENCY', 'TRAVEL', 'GADGET', 'OTHER')),
    CONSTRAINT goals_status_chk CHECK (status IN ('ACTIVE', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT goals_target_chk CHECK (target_amount > 0),
    CONSTRAINT goals_freq_chk CHECK (contribution_frequency IS NULL OR contribution_frequency IN ('WEEKLY', 'MONTHLY'))
);
CREATE INDEX ix_goals_user ON savings_goals (user_id) WHERE status <> 'ARCHIVED';

CREATE TABLE goal_contributions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id        uuid          NOT NULL REFERENCES savings_goals (id) ON DELETE RESTRICT,
    user_id        uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    kind           varchar(12)   NOT NULL,
    amount         numeric(19,4) NOT NULL,
    transaction_id uuid REFERENCES transactions (id) ON DELETE RESTRICT,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT contrib_kind_chk CHECK (kind IN ('PLANNED', 'TRANSFERRED')),
    CONSTRAINT contrib_amount_chk CHECK (amount > 0),
    -- a planned allocation must never point at the ledger; a transfer must
    CONSTRAINT contrib_ledger_link_chk CHECK ((kind = 'TRANSFERRED') = (transaction_id IS NOT NULL))
);
CREATE INDEX ix_contrib_goal ON goal_contributions (goal_id);

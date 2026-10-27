-- V9: money automation. Rules create PLANNED allocations only (intent + reservation); they never move money.
CREATE TABLE automation_rules (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    name               varchar(80)   NOT NULL,
    status             varchar(10)   NOT NULL DEFAULT 'ACTIVE',
    trigger_type       varchar(16)   NOT NULL,
    schedule           varchar(10),
    schedule_day       integer,
    min_trigger_amount numeric(19,4),
    trigger_category_id uuid REFERENCES categories (id) ON DELETE RESTRICT,
    source_account_id  uuid          NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    priority           integer       NOT NULL DEFAULT 100,
    per_run_limit      numeric(19,4),
    created_at         timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT rule_status_chk CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    CONSTRAINT rule_trigger_chk CHECK (trigger_type IN ('INCOME_RECEIVED', 'SCHEDULE', 'MANUAL')),
    CONSTRAINT rule_schedule_chk CHECK ((trigger_type = 'SCHEDULE') = (schedule IS NOT NULL)),
    CONSTRAINT rule_schedule_kind_chk CHECK (schedule IS NULL OR schedule IN ('WEEKLY', 'MONTHLY')),
    CONSTRAINT rule_day_chk CHECK (schedule_day IS NULL OR (schedule_day BETWEEN 1 AND 31)),
    CONSTRAINT rule_limit_chk CHECK (per_run_limit IS NULL OR per_run_limit > 0)
);
CREATE INDEX ix_rules_user ON automation_rules (user_id, priority) WHERE status <> 'ARCHIVED';

CREATE TABLE automation_actions (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id      uuid          NOT NULL REFERENCES automation_rules (id) ON DELETE CASCADE,
    position     integer       NOT NULL,
    target_type  varchar(8)    NOT NULL,
    target_id    uuid          NOT NULL,
    amount_type  varchar(8)    NOT NULL,
    amount_value numeric(19,4) NOT NULL,
    CONSTRAINT act_target_chk CHECK (target_type IN ('GOAL', 'BILL', 'BUDGET')),
    CONSTRAINT act_amount_type_chk CHECK (amount_type IN ('FIXED', 'PERCENT')),
    CONSTRAINT act_value_chk CHECK (amount_value > 0 AND (amount_type <> 'PERCENT' OR amount_value <= 100))
);
CREATE UNIQUE INDEX ux_actions_pos ON automation_actions (rule_id, position);

CREATE TABLE automation_runs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    rule_id        uuid          NOT NULL REFERENCES automation_rules (id) ON DELETE RESTRICT,
    user_id        uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    trigger_key    varchar(80)   NOT NULL,
    trigger_amount numeric(19,4),
    status         varchar(10)   NOT NULL,
    total_allocated numeric(19,4) NOT NULL DEFAULT 0,
    message        varchar(500),
    retry_of       uuid REFERENCES automation_runs (id) ON DELETE RESTRICT,
    attempt        integer       NOT NULL DEFAULT 1,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT run_status_chk CHECK (status IN ('SUCCESS', 'PARTIAL', 'FAILED'))
);
-- the same trigger can never execute the same rule twice (duplicate events, restarts, two instances)
CREATE UNIQUE INDEX ux_runs_trigger ON automation_runs (rule_id, trigger_key);
CREATE INDEX ix_runs_user ON automation_runs (user_id, created_at DESC);

CREATE TABLE automation_allocations (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id               uuid          NOT NULL REFERENCES automation_runs (id) ON DELETE RESTRICT,
    user_id              uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    position             integer       NOT NULL,
    target_type          varchar(8)    NOT NULL,
    target_id            uuid          NOT NULL,
    target_name          varchar(80),
    requested            numeric(19,4) NOT NULL,
    amount               numeric(19,4) NOT NULL,
    currency             varchar(3)    NOT NULL,
    source_account_id    uuid          NOT NULL REFERENCES accounts (id) ON DELETE RESTRICT,
    status               varchar(24)   NOT NULL,
    message              varchar(200),
    goal_contribution_id uuid REFERENCES goal_contributions (id) ON DELETE SET NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT alloc_status_chk CHECK (status IN ('PLANNED', 'RELEASED', 'SKIPPED_INSUFFICIENT', 'SKIPPED_LIMIT', 'SKIPPED_INVALID', 'SKIPPED_DONE'))
);
CREATE INDEX ix_alloc_run ON automation_allocations (run_id);
CREATE INDEX ix_alloc_open ON automation_allocations (source_account_id) WHERE status = 'PLANNED';

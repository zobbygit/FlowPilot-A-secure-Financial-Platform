-- V5: notifications (+preferences), profile settings, onboarding.
ALTER TABLE user_profiles
    ADD COLUMN financial_goals  varchar(400),
    ADD COLUMN onboarding_draft varchar(4000),
    ADD COLUMN number_format    varchar(16) NOT NULL DEFAULT 'auto',
    ADD COLUMN date_format      varchar(16) NOT NULL DEFAULT 'auto';

CREATE TABLE user_preferred_categories (
    user_id     uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category_id uuid NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, category_id)
);

CREATE TABLE notifications (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type       varchar(16)  NOT NULL,
    title      varchar(160) NOT NULL,
    body       varchar(500),
    link       varchar(200),
    -- scheduled jobs use deterministic keys, so re-running a job (or running it on two instances) never duplicates a notification
    dedupe_key varchar(160) NOT NULL,
    read_at    timestamptz,
    created_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT notifications_type_chk CHECK (type IN ('BILL', 'BUDGET', 'LOW_BALANCE', 'PAYMENT', 'AUTOMATION', 'SUPPORT', 'SECURITY', 'SYSTEM'))
);
CREATE UNIQUE INDEX ux_notifications_dedupe ON notifications (user_id, dedupe_key);
CREATE INDEX ix_notifications_user_time ON notifications (user_id, created_at DESC);
CREATE INDEX ix_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;

CREATE TABLE notification_preferences (
    user_id               uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    bills                 boolean NOT NULL DEFAULT true,
    budgets               boolean NOT NULL DEFAULT true,
    low_balance           boolean NOT NULL DEFAULT true,
    payments              boolean NOT NULL DEFAULT true,
    automation            boolean NOT NULL DEFAULT true,
    support               boolean NOT NULL DEFAULT true,
    low_balance_threshold numeric(19,4),
    CONSTRAINT notif_threshold_chk CHECK (low_balance_threshold IS NULL OR low_balance_threshold >= 0)
);

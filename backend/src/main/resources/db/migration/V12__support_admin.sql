-- V12: complaint tickets + live chat, admin announcements, investigations, platform settings.
CREATE SEQUENCE support_ticket_seq START 1000;

CREATE TABLE support_tickets (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_no         bigint       NOT NULL DEFAULT nextval('support_ticket_seq'),
    user_id           uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    title             varchar(120) NOT NULL,
    category          varchar(10)  NOT NULL,
    priority          varchar(8)   NOT NULL DEFAULT 'MEDIUM',
    description       varchar(4000) NOT NULL,
    status            varchar(14)  NOT NULL DEFAULT 'OPEN',
    payment_id        uuid REFERENCES payments (id) ON DELETE RESTRICT,
    assigned_admin_id uuid REFERENCES users (id) ON DELETE RESTRICT,
    first_admin_reply_at timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    resolved_at       timestamptz,
    closed_at         timestamptz,
    CONSTRAINT tk_category_chk CHECK (category IN ('PAYMENT', 'RECORD', 'BUG', 'ACCOUNT', 'OTHER')),
    CONSTRAINT tk_priority_chk CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    CONSTRAINT tk_status_chk CHECK (status IN ('OPEN', 'IN_PROGRESS', 'AWAITING_USER', 'RESOLVED', 'CLOSED'))
);
CREATE UNIQUE INDEX ux_tickets_no ON support_tickets (ticket_no);
CREATE INDEX ix_tickets_user ON support_tickets (user_id, created_at DESC);
CREATE INDEX ix_tickets_queue ON support_tickets (status, priority, created_at);

CREATE TABLE support_messages (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   uuid          NOT NULL REFERENCES support_tickets (id) ON DELETE RESTRICT,
    sender_id   uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    sender_role varchar(5)    NOT NULL,
    body        varchar(4000),
    file_id     uuid REFERENCES stored_files (id) ON DELETE RESTRICT,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    read_at     timestamptz,
    CONSTRAINT msg_role_chk CHECK (sender_role IN ('USER', 'ADMIN')),
    CONSTRAINT msg_content_chk CHECK (body IS NOT NULL OR file_id IS NOT NULL)
);
CREATE INDEX ix_msg_ticket ON support_messages (ticket_id, created_at);
CREATE INDEX ix_msg_unread ON support_messages (ticket_id, sender_role) WHERE read_at IS NULL;

CREATE TABLE support_events (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id   uuid        NOT NULL REFERENCES support_tickets (id) ON DELETE RESTRICT,
    actor_id    uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    actor_role  varchar(5)  NOT NULL,
    event_type  varchar(24) NOT NULL,
    from_value  varchar(40),
    to_value    varchar(40),
    note        varchar(300),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_support_events ON support_events (ticket_id, created_at);
CREATE TRIGGER trg_support_events_append_only BEFORE UPDATE OR DELETE ON support_events FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Admin announcements: persisted, with publication state; delivery rows are ordinary notifications keyed by announcement id.
CREATE TABLE announcements (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    title         varchar(120)  NOT NULL,
    body          varchar(1000) NOT NULL,
    audience      varchar(14)   NOT NULL,
    target_emails varchar(2000),
    status        varchar(10)   NOT NULL DEFAULT 'DRAFT',
    created_by    uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    published_at  timestamptz,
    delivered     integer       NOT NULL DEFAULT 0,
    CONSTRAINT ann_audience_chk CHECK (audience IN ('ALL', 'NEW_USERS_30D', 'SPECIFIC')),
    CONSTRAINT ann_status_chk CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

-- Time-boxed, reasoned access to a user's financial summary. Admins have NO default access to private financial detail.
CREATE TABLE admin_investigations (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id    uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    ticket_id   uuid REFERENCES support_tickets (id) ON DELETE RESTRICT,
    reason      varchar(500)  NOT NULL,
    created_at  timestamptz   NOT NULL DEFAULT now(),
    expires_at  timestamptz   NOT NULL
);
CREATE INDEX ix_invest_admin ON admin_investigations (admin_id, created_at DESC);

CREATE TABLE platform_settings (
    key        varchar(60)  PRIMARY KEY,
    value      varchar(500) NOT NULL,
    updated_by uuid REFERENCES users (id) ON DELETE RESTRICT,
    updated_at timestamptz  NOT NULL DEFAULT now()
);
INSERT INTO platform_settings (key, value) VALUES
    ('currencies.supported', 'USD,EUR,GBP,INR,CAD,AUD,JPY'),
    ('payment.max_amount', '100000'),
    ('payment.pending_ttl_minutes', '15'),
    ('support.reopen_days', '14');

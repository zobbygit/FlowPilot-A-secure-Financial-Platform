-- V1: identity, tokens, server-side sessions, append-only audit log.
-- Conventions: UUID keys, timestamptz in UTC, varchar+CHECK instead of PG enums (easier to evolve).

CREATE TABLE users (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email                 varchar(254) NOT NULL,
    password_hash         varchar(255) NOT NULL,
    role                  varchar(10)  NOT NULL,
    status                varchar(24)  NOT NULL,
    email_verified_at     timestamptz,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    password_changed_at   timestamptz  NOT NULL DEFAULT now(),
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT users_role_chk   CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT users_status_chk CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETION_REQUESTED', 'DELETED')),
    CONSTRAINT users_email_lower_chk CHECK (email = lower(email))
);
CREATE UNIQUE INDEX ux_users_email ON users (email);
CREATE INDEX ix_users_role_status ON users (role, status);

CREATE TABLE user_profiles (
    user_id                 uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    full_name               varchar(120) NOT NULL,
    avatar_url              varchar(500),
    avatar_public_id        varchar(255),
    preferred_currency      varchar(3)   NOT NULL DEFAULT 'USD',
    timezone                varchar(64)  NOT NULL DEFAULT 'UTC',
    locale                  varchar(16)  NOT NULL DEFAULT 'en-US',
    theme_preference        varchar(10)  NOT NULL DEFAULT 'system',
    onboarding_completed_at timestamptz,
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    version                 bigint       NOT NULL DEFAULT 0,
    CONSTRAINT user_profiles_theme_chk CHECK (theme_preference IN ('light', 'dark', 'system'))
);

-- Only the SHA-256 of a token is stored; the raw token exists only in the email link.
CREATE TABLE email_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    purpose     varchar(20)  NOT NULL,
    token_hash  varchar(64)  NOT NULL,
    expires_at  timestamptz  NOT NULL,
    consumed_at timestamptz,
    revoked_at  timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT email_tokens_purpose_chk CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD'))
);
CREATE UNIQUE INDEX ux_email_tokens_hash ON email_tokens (token_hash);
CREATE INDEX ix_email_tokens_user_purpose ON email_tokens (user_id, purpose);
CREATE INDEX ix_email_tokens_expires ON email_tokens (expires_at);

-- Opaque server-side sessions. The cookie carries a random token; only its hash is stored.
CREATE TABLE auth_sessions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash     varchar(64)  NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    last_active_at timestamptz  NOT NULL DEFAULT now(),
    expires_at     timestamptz  NOT NULL,
    revoked_at     timestamptz,
    revoked_reason varchar(24),
    user_agent     varchar(400),
    ip_address     varchar(45)
);
CREATE UNIQUE INDEX ux_auth_sessions_hash ON auth_sessions (token_hash);
CREATE INDEX ix_auth_sessions_user_active ON auth_sessions (user_id, last_active_at DESC);
CREATE INDEX ix_auth_sessions_expires ON auth_sessions (expires_at);

-- Append-only audit trail (user + admin scopes). Never store secrets or raw tokens in details.
CREATE TABLE audit_events (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope         varchar(8)   NOT NULL,
    event_type    varchar(64)  NOT NULL,
    outcome       varchar(10)  NOT NULL DEFAULT 'SUCCESS',
    actor_user_id uuid REFERENCES users (id) ON DELETE RESTRICT,
    actor_role    varchar(10),
    session_id    uuid,
    target_type   varchar(40),
    target_id     varchar(64),
    ip_address    varchar(45),
    user_agent    varchar(400),
    details       varchar(2000),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT audit_scope_chk   CHECK (scope IN ('USER', 'ADMIN')),
    CONSTRAINT audit_outcome_chk CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);
CREATE INDEX ix_audit_actor_time ON audit_events (actor_user_id, created_at DESC);
CREATE INDEX ix_audit_scope_time ON audit_events (scope, created_at DESC);
CREATE INDEX ix_audit_type_time ON audit_events (event_type, created_at DESC);

CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% on % is not permitted (append-only table)', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

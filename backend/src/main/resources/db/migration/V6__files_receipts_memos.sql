-- V6: file metadata, receipts (+status history), memos.
-- Bytes live in managed object storage; PostgreSQL holds ownership, keys, MIME, size, checksum and permissions.
CREATE TABLE stored_files (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    purpose       varchar(20)  NOT NULL,
    object_key    varchar(200) NOT NULL,
    original_name varchar(200),
    mime_type     varchar(80)  NOT NULL,
    size_bytes    bigint       NOT NULL,
    sha256        varchar(64)  NOT NULL,
    provider      varchar(20)  NOT NULL DEFAULT 'cloudinary',
    created_at    timestamptz  NOT NULL DEFAULT now(),
    deleted_at    timestamptz,
    CONSTRAINT files_purpose_chk CHECK (purpose IN ('RECEIPT', 'AVATAR', 'COMPLAINT_EVIDENCE', 'DOCUMENT')),
    CONSTRAINT files_size_chk CHECK (size_bytes > 0)
);
CREATE UNIQUE INDEX ux_files_object_key ON stored_files (object_key);
CREATE INDEX ix_files_owner ON stored_files (owner_user_id, purpose) WHERE deleted_at IS NULL;

CREATE TABLE receipts (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    transaction_id uuid REFERENCES transactions (id) ON DELETE RESTRICT,
    file_id        uuid         NOT NULL REFERENCES stored_files (id) ON DELETE RESTRICT,
    receipt_number varchar(60),
    merchant       varchar(120),
    subtotal       numeric(19,4),
    tax            numeric(19,4),
    total          numeric(19,4),
    currency       varchar(3),
    review_status  varchar(14)  NOT NULL DEFAULT 'UNREVIEWED',
    created_at     timestamptz  NOT NULL DEFAULT now(),
    archived_at    timestamptz,
    CONSTRAINT receipts_status_chk CHECK (review_status IN ('UNREVIEWED', 'VERIFIED', 'NEEDS_REVIEW')),
    CONSTRAINT receipts_amounts_chk CHECK ((subtotal IS NULL OR subtotal >= 0) AND (tax IS NULL OR tax >= 0) AND (total IS NULL OR total >= 0))
);
CREATE INDEX ix_receipts_user ON receipts (user_id, created_at DESC) WHERE archived_at IS NULL;
CREATE INDEX ix_receipts_tx ON receipts (transaction_id);

CREATE TABLE receipt_status_history (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id  uuid        NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    from_status varchar(14),
    to_status   varchar(14) NOT NULL,
    note        varchar(300),
    changed_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_receipt_history ON receipt_status_history (receipt_id, changed_at);

CREATE TABLE memos (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    title          varchar(120) NOT NULL,
    body           varchar(4000),
    tags           varchar(300),
    pinned         boolean      NOT NULL DEFAULT false,
    follow_up_date date,
    transaction_id uuid REFERENCES transactions (id) ON DELETE RESTRICT,
    goal_id        uuid REFERENCES savings_goals (id) ON DELETE RESTRICT,
    archived_at    timestamptz,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_memos_user ON memos (user_id, pinned DESC, updated_at DESC) WHERE archived_at IS NULL;
CREATE INDEX ix_memos_followup ON memos (user_id, follow_up_date) WHERE archived_at IS NULL AND follow_up_date IS NOT NULL;

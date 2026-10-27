-- V13: user-facing financial audit trail, shared households, custom document layouts.

-- Structured, append-only trail of important financial changes. Written in the SAME transaction as the change it describes,
-- so a rolled-back change leaves no audit row and a committed change always has one. Never put secrets or free-text notes here.
CREATE TABLE financial_audit (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id    uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    actor_id    uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    action      varchar(40) NOT NULL,
    entity_type varchar(24) NOT NULL,
    entity_id   uuid,
    summary     varchar(200),
    prev_value  varchar(300),
    new_value   varchar(300),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_faudit_owner ON financial_audit (owner_id, created_at DESC);
CREATE INDEX ix_faudit_entity ON financial_audit (owner_id, entity_type, entity_id);
CREATE TRIGGER trg_financial_audit_append_only BEFORE UPDATE OR DELETE ON financial_audit FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

-- Households: membership is a domain relation. There is no extra application role; users stay USER.
CREATE TABLE households (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(80) NOT NULL,
    owner_id    uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    currency    varchar(3)  NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    archived_at timestamptz
);
CREATE TABLE household_members (
    household_id uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    member_role  varchar(6)  NOT NULL DEFAULT 'MEMBER',
    joined_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (household_id, user_id),
    CONSTRAINT hm_role_chk CHECK (member_role IN ('OWNER', 'MEMBER'))
);
CREATE INDEX ix_hm_user ON household_members (user_id);
CREATE TABLE household_invitations (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id    uuid        NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    invited_by      uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    invitee_user_id uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    status          varchar(10) NOT NULL DEFAULT 'PENDING',
    created_at      timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    responded_at    timestamptz,
    CONSTRAINT hi_status_chk CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'))
);
CREATE UNIQUE INDEX ux_hi_pending ON household_invitations (household_id, invitee_user_id) WHERE status = 'PENDING';
CREATE INDEX ix_hi_invitee ON household_invitations (invitee_user_id, status);

CREATE TABLE household_expenses (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id  uuid          NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    payer_id      uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    amount        numeric(19,4) NOT NULL,
    description   varchar(120)  NOT NULL,
    category_id   uuid REFERENCES categories (id) ON DELETE RESTRICT,
    occurred_on   date          NOT NULL,
    split_type    varchar(8)    NOT NULL,
    created_by    uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    deleted_at    timestamptz,
    CONSTRAINT he_amount_chk CHECK (amount > 0),
    CONSTRAINT he_split_chk CHECK (split_type IN ('EQUAL', 'EXACT', 'PERCENT'))
);
CREATE INDEX ix_he_household ON household_expenses (household_id, occurred_on DESC) WHERE deleted_at IS NULL;
CREATE TABLE household_splits (
    expense_id   uuid          NOT NULL REFERENCES household_expenses (id) ON DELETE CASCADE,
    user_id      uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    share_amount numeric(19,4) NOT NULL,
    PRIMARY KEY (expense_id, user_id),
    CONSTRAINT hs_share_chk CHECK (share_amount >= 0)
);
CREATE TABLE household_settlements (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id uuid          NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    from_user    uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    to_user      uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    amount       numeric(19,4) NOT NULL,
    note         varchar(120),
    created_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT hset_amount_chk CHECK (amount > 0),
    CONSTRAINT hset_distinct_chk CHECK (from_user <> to_user)
);
CREATE TABLE household_budgets (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    household_id uuid          NOT NULL REFERENCES households (id) ON DELETE CASCADE,
    name         varchar(80)   NOT NULL,
    category_id  uuid REFERENCES categories (id) ON DELETE RESTRICT,
    limit_amount numeric(19,4) NOT NULL,
    created_by   uuid          NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    archived_at  timestamptz,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT hb_limit_chk CHECK (limit_amount > 0)
);

-- Custom financial documents: personal records only. A fixed disclaimer is always printed and cannot be removed.
CREATE TABLE document_templates (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid REFERENCES users (id) ON DELETE RESTRICT,           -- NULL = built-in
    name         varchar(80)  NOT NULL,
    kind         varchar(20)  NOT NULL,
    title        varchar(80)  NOT NULL,
    brand_name   varchar(80),
    header_text  varchar(200),
    footer_text  varchar(200),
    accent_color varchar(7)   NOT NULL DEFAULT '#6d28d9',
    font_family  varchar(10)  NOT NULL DEFAULT 'HELVETICA',
    font_size    integer      NOT NULL DEFAULT 11,
    orientation  varchar(9)   NOT NULL DEFAULT 'PORTRAIT',
    logo_file_id uuid REFERENCES stored_files (id) ON DELETE SET NULL,
    archived_at  timestamptz,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT dt_kind_chk CHECK (kind IN ('EXPENSE_VOUCHER', 'REIMBURSEMENT', 'PAYMENT_CONFIRMATION', 'CUSTOM')),
    CONSTRAINT dt_font_chk CHECK (font_family IN ('HELVETICA', 'TIMES', 'COURIER')),
    CONSTRAINT dt_size_chk CHECK (font_size BETWEEN 8 AND 16),
    CONSTRAINT dt_orient_chk CHECK (orientation IN ('PORTRAIT', 'LANDSCAPE')),
    CONSTRAINT dt_color_chk CHECK (accent_color ~ '^#[0-9a-fA-F]{6}$')
);
CREATE TABLE document_fields (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id uuid        NOT NULL REFERENCES document_templates (id) ON DELETE CASCADE,
    position    integer     NOT NULL,
    label       varchar(60) NOT NULL,
    source      varchar(20) NOT NULL,
    CONSTRAINT df_source_chk CHECK (source IN ('MANUAL', 'USER_NAME', 'TX_AMOUNT', 'TX_DATE', 'TX_MERCHANT', 'TX_DESCRIPTION', 'TX_CATEGORY', 'TX_ACCOUNT', 'PAY_REFERENCE', 'PAY_AMOUNT', 'PAY_PARTIES', 'PAY_STATUS', 'TODAY'))
);
CREATE UNIQUE INDEX ux_df_pos ON document_fields (template_id, position);
CREATE TABLE generated_documents (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    template_id  uuid        NOT NULL REFERENCES document_templates (id) ON DELETE RESTRICT,
    transaction_id uuid REFERENCES transactions (id) ON DELETE RESTRICT,
    payment_id   uuid REFERENCES payments (id) ON DELETE RESTRICT,
    sha256       varchar(64) NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_gendoc_user ON generated_documents (user_id, created_at DESC);

INSERT INTO document_templates (id, name, kind, title, header_text) VALUES
 ('00000000-0000-0000-0000-0000000000d1', 'Expense voucher', 'EXPENSE_VOUCHER', 'Expense Voucher', 'Record of a personal expense'),
 ('00000000-0000-0000-0000-0000000000d2', 'Reimbursement form', 'REIMBURSEMENT', 'Reimbursement Request', 'Request to be reimbursed for an expense'),
 ('00000000-0000-0000-0000-0000000000d3', 'Payment confirmation', 'PAYMENT_CONFIRMATION', 'Simulated Payment Confirmation', 'Confirmation of a simulated payment');
INSERT INTO document_fields (template_id, position, label, source) VALUES
 ('00000000-0000-0000-0000-0000000000d1', 1, 'Prepared by', 'USER_NAME'), ('00000000-0000-0000-0000-0000000000d1', 2, 'Date', 'TX_DATE'), ('00000000-0000-0000-0000-0000000000d1', 3, 'Merchant', 'TX_MERCHANT'),
 ('00000000-0000-0000-0000-0000000000d1', 4, 'Description', 'TX_DESCRIPTION'), ('00000000-0000-0000-0000-0000000000d1', 5, 'Category', 'TX_CATEGORY'), ('00000000-0000-0000-0000-0000000000d1', 6, 'Amount', 'TX_AMOUNT'), ('00000000-0000-0000-0000-0000000000d1', 7, 'Approved by', 'MANUAL'),
 ('00000000-0000-0000-0000-0000000000d2', 1, 'Employee / claimant', 'USER_NAME'), ('00000000-0000-0000-0000-0000000000d2', 2, 'Expense date', 'TX_DATE'), ('00000000-0000-0000-0000-0000000000d2', 3, 'Merchant', 'TX_MERCHANT'),
 ('00000000-0000-0000-0000-0000000000d2', 4, 'Amount to reimburse', 'TX_AMOUNT'), ('00000000-0000-0000-0000-0000000000d2', 5, 'Business purpose', 'MANUAL'), ('00000000-0000-0000-0000-0000000000d2', 6, 'Submitted on', 'TODAY'),
 ('00000000-0000-0000-0000-0000000000d3', 1, 'Reference', 'PAY_REFERENCE'), ('00000000-0000-0000-0000-0000000000d3', 2, 'Parties', 'PAY_PARTIES'), ('00000000-0000-0000-0000-0000000000d3', 3, 'Amount', 'PAY_AMOUNT'), ('00000000-0000-0000-0000-0000000000d3', 4, 'Status', 'PAY_STATUS'), ('00000000-0000-0000-0000-0000000000d3', 5, 'Printed on', 'TODAY');

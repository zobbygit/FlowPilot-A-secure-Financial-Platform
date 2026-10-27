-- V7: private, reusable recording forms ("My Finance Templates") + four built-in templates.
-- Fields are normalised rows (no JSON blobs) so every rule is validated and queryable.
CREATE TABLE finance_templates (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid REFERENCES users (id) ON DELETE RESTRICT,          -- NULL = built-in (read-only, duplicable)
    name        varchar(80)  NOT NULL,
    description varchar(300),
    target_type varchar(12)  NOT NULL,
    fixed_type  varchar(10),                                           -- TRANSACTION templates: fix INCOME/EXPENSE (else a field binds 'type')
    sort_order  integer      NOT NULL DEFAULT 100,
    archived_at timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT templates_target_chk CHECK (target_type IN ('TRANSACTION', 'MEMO', 'BILL')),
    CONSTRAINT templates_fixed_chk CHECK (fixed_type IS NULL OR fixed_type IN ('INCOME', 'EXPENSE'))
);
CREATE INDEX ix_templates_user ON finance_templates (user_id) WHERE archived_at IS NULL;

CREATE TABLE template_fields (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id   uuid         NOT NULL REFERENCES finance_templates (id) ON DELETE CASCADE,
    position      integer      NOT NULL,
    field_key     varchar(30)  NOT NULL,
    label         varchar(60)  NOT NULL,
    field_type    varchar(12)  NOT NULL,
    binding       varchar(24)  NOT NULL,
    required      boolean      NOT NULL DEFAULT false,
    options       varchar(400),                                        -- DROPDOWN: "value|Label,value|Label" (or plain values)
    default_value varchar(200),
    min_value     numeric(19,4),                                       -- AMOUNT/NUMBER minimum, TEXT minimum length
    max_value     numeric(19,4),                                       -- AMOUNT/NUMBER maximum, TEXT maximum length
    CONSTRAINT tf_type_chk CHECK (field_type IN ('TEXT', 'AMOUNT', 'NUMBER', 'DATE', 'DROPDOWN', 'CHECKBOX', 'TAGS', 'FILE', 'CATEGORY', 'ACCOUNT', 'TRANSACTION')),
    CONSTRAINT tf_key_chk CHECK (field_key ~ '^[a-z][a-zA-Z0-9]{0,29}$')
);
CREATE UNIQUE INDEX ux_tf_key ON template_fields (template_id, field_key);
CREATE UNIQUE INDEX ux_tf_pos ON template_fields (template_id, position);

-- Built-ins -----------------------------------------------------------------------------------------------
INSERT INTO finance_templates (id, name, description, target_type, fixed_type, sort_order) VALUES
 ('00000000-0000-0000-0000-0000000000a1', 'Expense Record', 'Record a purchase with merchant, tax, payment method and an optional receipt.', 'TRANSACTION', 'EXPENSE', 1),
 ('00000000-0000-0000-0000-0000000000a2', 'Cash Record',    'Cash received or spent, with purpose, person and reference.',               'TRANSACTION', NULL,      2),
 ('00000000-0000-0000-0000-0000000000a3', 'Financial Memo', 'A note linked to a transaction, with tags and a follow-up date.',           'MEMO',        NULL,      3),
 ('00000000-0000-0000-0000-0000000000a4', 'Recurring Payment', 'A bill or subscription with frequency, next date, account and reminder.', 'BILL',       NULL,      4);

INSERT INTO template_fields (template_id, position, field_key, label, field_type, binding, required, options, default_value, min_value, max_value) VALUES
 ('00000000-0000-0000-0000-0000000000a1', 1, 'merchant',   'Merchant',        'TEXT',     'merchant',      true,  NULL, NULL, 1, 120),
 ('00000000-0000-0000-0000-0000000000a1', 2, 'amount',      'Amount',          'AMOUNT',   'amount',        true,  NULL, NULL, 0.01, NULL),
 ('00000000-0000-0000-0000-0000000000a1', 3, 'tax',         'Tax included',    'AMOUNT',   'tax',           false, NULL, NULL, 0, NULL),
 ('00000000-0000-0000-0000-0000000000a1', 4, 'category',    'Category',        'CATEGORY', 'categoryId',    false, NULL, NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a1', 5, 'method',      'Payment method',  'DROPDOWN', 'paymentMethod', false, 'card|Card,cash|Cash,bank|Bank transfer,wallet|Mobile wallet', 'card', NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a1', 6, 'accountId',   'Paid from',       'ACCOUNT',  'accountId',     true,  NULL, NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a1', 7, 'date',        'Date',            'DATE',     'occurredOn',    true,  NULL, 'today', NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a1', 8, 'receipt',     'Receipt',         'FILE',     'receiptFile',   false, NULL, NULL, NULL, NULL),

 ('00000000-0000-0000-0000-0000000000a2', 1, 'direction',   'Cash',            'DROPDOWN', 'type',          true,  'INCOME|Cash received,EXPENSE|Cash spent', 'EXPENSE', NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a2', 2, 'amount',      'Amount',          'AMOUNT',   'amount',        true,  NULL, NULL, 0.01, NULL),
 ('00000000-0000-0000-0000-0000000000a2', 3, 'purpose',     'Purpose',         'TEXT',     'description',   true,  NULL, NULL, 1, 200),
 ('00000000-0000-0000-0000-0000000000a2', 4, 'person',      'Person',          'TEXT',     'merchant',      false, NULL, NULL, NULL, 120),
 ('00000000-0000-0000-0000-0000000000a2', 5, 'reference',   'Reference',       'TEXT',     'reference',     false, NULL, NULL, NULL, 80),
 ('00000000-0000-0000-0000-0000000000a2', 6, 'accountId',   'Cash account',    'ACCOUNT',  'accountId',     true,  NULL, NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a2', 7, 'date',        'Date',            'DATE',     'occurredOn',    true,  NULL, 'today', NULL, NULL),

 ('00000000-0000-0000-0000-0000000000a3', 1, 'title',       'Title',           'TEXT',     'title',         true,  NULL, NULL, 1, 120),
 ('00000000-0000-0000-0000-0000000000a3', 2, 'description', 'Description',     'TEXT',     'body',          false, NULL, NULL, NULL, 4000),
 ('00000000-0000-0000-0000-0000000000a3', 3, 'transaction', 'Linked transaction', 'TRANSACTION', 'transactionId', false, NULL, NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a3', 4, 'tags',        'Tags',            'TAGS',     'tags',          false, NULL, NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a3', 5, 'followUp',    'Follow-up date',  'DATE',     'followUpDate',  false, NULL, NULL, NULL, NULL),

 ('00000000-0000-0000-0000-0000000000a4', 1, 'name',        'Name',            'TEXT',     'name',          true,  NULL, NULL, 1, 80),
 ('00000000-0000-0000-0000-0000000000a4', 2, 'amount',      'Amount',          'AMOUNT',   'amount',        true,  NULL, NULL, 0.01, NULL),
 ('00000000-0000-0000-0000-0000000000a4', 3, 'frequency',   'Frequency',       'DROPDOWN', 'frequency',     true,  'WEEKLY|Weekly,MONTHLY|Monthly,QUARTERLY|Quarterly,YEARLY|Yearly', 'MONTHLY', NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a4', 4, 'next',        'Next date',       'DATE',     'nextDueDate',   true,  NULL, 'today', NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a4', 5, 'accountId',   'Account',         'ACCOUNT',  'accountId',     true,  NULL, NULL, NULL, NULL),
 ('00000000-0000-0000-0000-0000000000a4', 6, 'reminder',    'Remind me (days before)', 'NUMBER', 'reminderDays', false, NULL, '3', 0, 60);

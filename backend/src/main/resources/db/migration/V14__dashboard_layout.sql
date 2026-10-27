-- V14: per-user dashboard widget layout (visibility + order). Purely presentational.
CREATE TABLE dashboard_layouts (
    user_id    uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    widgets    varchar(200) NOT NULL,
    updated_at timestamptz  NOT NULL DEFAULT now()
);

# Architecture

**Modular monolith** (Spring Boot, Java 25). Packages under `com.flowpilot`: `auth`, `security`, `audit`, `mail`, `domain`, `repo`,
`common`, `config`, `jobs` — finance modules (`accounts`, `ledger`, `budgets`, …) are added as sibling packages. Controllers → services → repositories;
entities never leave the API (DTO records only).

**Auth model**: opaque 256-bit session token in an `HttpOnly; Secure; SameSite` cookie; only its SHA-256 is stored in `auth_sessions`.
Every request is authorised from the database row, so revoke/suspend/expiry apply instantly. Roles: `USER`, `ADMIN` (separate login endpoints).
No JWT: nothing needs to be verified without a DB hit, and revocation is a core requirement.

**CSRF**: double-submit with a token fetched from `GET /api/auth/csrf` (works cross-site where cookie reading cannot).

**Ledger model** (V2): transactions are immutable; corrections are reversal + new posting. Balances change only via `ledger_entries`
(signed amounts, DB trigger forbids UPDATE/DELETE). `accounts.current_balance` is a cache updated in the same DB transaction under a row lock,
with `CHECK (allow_negative OR balance >= 0)`. Transfers = one transaction, two entries summing to zero. Money is `numeric(19,4)`/`BigDecimal`, never floating point.

**Email**: Brevo HTTP API behind `EmailSender`; dev uses a log-only sender that is impossible in `prod`.
Success means "provider accepted the request", never "delivered".

## Modules (packages under `com.flowpilot`)
`auth` `security` `audit` (security trail + user-visible financial trail) · `finance` (ledger, accounts) · `planning` (budgets, bills, goals) · `automation` · `payments` (simulator) ·
`files` (receipts, memos, Cloudinary, validator) · `templates` · `documents` · `reconciliation` + `csv` · `sandbox` · `reports` (reports, calendar, pipeline, export) · `households` ·
`notify` (notifications + WebSocket hints + reminder jobs) · `support` · `admin` (dashboard, users, payments, audit, oversight, announcements, settings) · `account` (profile, onboarding, categories).

**Real time**: PostgreSQL is the source of truth. The WebSocket carries only `{type:"refresh", topic}` hints; clients re-read via REST, reconnect with backoff, and reload everything after a reconnect. Revoked sessions are swept from the socket registry every minute.
**Idempotency**: unique keys at the database level (payment per request, goal/receipt dedupe, automation trigger keys, notification dedupe keys, import file hash).

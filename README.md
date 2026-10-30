# FlowPilot
<img width="1905" height="829" alt="image" src="https://github.com/user-attachments/assets/1443259e-5f25-43c2-9de3-11862a2a3d2b" />
<img width="1203" height="942" alt="image" src="https://github.com/user-attachments/assets/0e1a17fa-6a4a-464e-a58c-223bd5a60450" />



**A personal-finance command center**: accounts, an immutable ledger, receipts, budgets, bills, goals, automation rules, a payment simulator, shared households, reconciliation, reports, custom documents, support chat and a full admin console, in one secure web app.

| | |
|---|---|
| **Frontend** | React 19 · TypeScript · Vite · Tailwind · deployed on **Vercel** |
| **Backend** | Spring Boot 4 · Java 25 · REST + WebSocket · deployed on **Render** (Docker runtime, built by Render) |
| **Database** | PostgreSQL 17 on **Neon** · Flyway migrations |
| **Email** | **Brevo** transactional API (verification + password reset) |
| **Files** | **Cloudinary** (receipts, avatars, support attachments) |
| **Themes** | Dark and Light, system-aware |

> **Read this honestly:** the code was authored without a compiler or database in the authoring environment, then run and fixed on a real machine and deployed. Some paths are covered by tests, others have only been exercised by hand. See [§19 Known limitations](#19-known-limitations--roadmap).

---

## Table of contents
1. [What FlowPilot is](#1-what-flowpilot-is)
2. [Feature catalogue](#2-feature-catalogue)
3. [Tech stack](#3-tech-stack)
4. [Architecture](#4-architecture)
5. [Repository layout (full file tree)](#5-repository-layout-full-file-tree)
6. [Getting started locally (clone → run)](#6-getting-started-locally-clone--run)
7. [Environment variables](#7-environment-variables)
8. [Booting: what happens at start-up](#8-booting-what-happens-at-start-up)
9. [Database](#9-database)
10. [API reference](#10-api-reference)
11. [Real-time (WebSocket)](#11-real-time-websocket)
12. [Background jobs](#12-background-jobs)
13. [Frontend](#13-frontend)
14. [Security model](#14-security-model)
15. [Testing & quality](#15-testing--quality)
16. [Production deployment](#16-production-deployment)
17. [Operations & maintenance](#17-operations--maintenance)
18. [Troubleshooting](#18-troubleshooting)
19. [Known limitations & roadmap](#19-known-limitations--roadmap)
20. [Documentation index](#20-documentation-index)

---

## 1. What FlowPilot is

FlowPilot gives a person one place to see and plan all of their money.

- **Two roles only:** `USER` and `ADMIN`. Separate sign-in screens (`/user/login`, `/admin/login`) and separate layouts. There is no public admin sign-up; the first admin is created from environment variables.
- **No real money moves.** Payments between users are a clearly-labelled **simulator**; automation rules only **plan** allocations; documents are **personal records**, never official invoices.
- **The ledger is the source of truth.** Money is stored as exact decimals (`numeric(19,4)`), every posting creates immutable ledger entries, and corrections are made by *reversal*, never by editing history.
- **Privacy by design.** Admins see aggregates; reaching a user's financial summary needs a written reason, expires after 24 hours and is audited in an append-only log.

---

## 2. Feature catalogue

### A. Public site and authentication
1. **Cinematic landing page** with 12 story sections, an optional skippable curtain intro (once per session, skipped for reduced-motion users), a React-Three-Fiber hero and scroll-linked "money universe" (lazy-loaded, paused off-screen, CSS fallback when WebGL is unavailable), product previews labelled *Sample data*, FAQ and footer.
2. **Dark / Light theme** with no flash on load, remembered per browser.
3. **Cookie consent banner** (versioned; only essential cookies without consent) and **legal pages** (Privacy, Cookies, Terms: draft text that needs legal review).
4. **Registration** with strong-password policy, **email verification** through Brevo (single-use, expiring link) and a resend flow with generic, non-enumerating responses.
5. **Login** with lockout (5 failures → 15 minutes), rate limits and device tracking; **forgot / reset password** via emailed single-use link; every session revoked after a reset.
6. **Onboarding wizard** (currency, first account, categories, notification preferences) that gates the app until finished.

### B. User application (`/app/*`)
7. **Dashboard**: key figures (balance, income, expenses, net saved, with period change), **Safe to spend** (spendable balance − bills due this month − amounts planned by rules), the **Financial Activity Pipeline** (animated SVG: income → balances → commitments → budgets → goals → expenses → available, with accessible text fallback), monthly cash flow chart, spending donut, upcoming bills, goals, recent transactions, date ranges (7D/30D/3M/1Y) and **customizable widgets** (show/hide/reorder, saved per user). *Explore with demo data* for empty workspaces.
8. **Accounts & wallets**: `BANK`, `CASH`, `SAVINGS`, `WALLET`; opening balances; archive; overdraft refused unless allowed.
9. **Transactions**: income, expense and transfer; categories, merchant, payment method, notes; filters, search, pagination; **reversal** instead of edit/delete; **idempotency keys** so a retry never double-posts.
10. **Receipts & memos**: upload JPEG/PNG/WebP/PDF (content-verified), review status, link to transactions; free-form memos with tags and pinning.
11. **Finance templates**: four built-in templates (Expense Record, Cash Record, Financial Memo, Recurring Payment) plus a **template builder** with live preview that creates real records.
12. **Budgets**: monthly or custom period, category scoped, rollover, threshold alerts (80 % / 100 %).
13. **Bills & subscriptions**: frequencies, reminders, price-change history, mark-as-paid creating a real transaction.
14. **Savings goals & planning**: planned contributions never touch the ledger; only real transfers count as saved; 30–90 day projection.
15. **Money automation**: rules ("when income ≥ X arrives, plan N % to goal Y"), idempotent runs per trigger, preview, retry, daily schedule; plans allocations only.
16. **Payment simulator**: payment handles, QR payment requests, two-step initiate → confirm, refunds (new ledger entries), disputes, PDF confirmation, expiry job, live updates.
17. **Planning sandbox**: what-if projections that never write to the real ledger.
18. **Statement reconciliation**: CSV import with column mapping, match suggestions (confirm / reject / record / undo), **recurring-charge detection** that can create bills.
19. **Financial calendar**: bills, renewals, planned contributions and spending on a month view.
20. **Reports & exports**: `income-expense`, `spending-by-category`, `spending-by-merchant`, `accounts`, `subscription-cost`, `budget-variance`, `cash-flow-forecast`; CSV (formula-injection-safe) and PDF export.
21. **Shared households**: invite by payment handle, equal / exact / percentage splits, per-member balances (always sum to zero), suggested settle-up, household budgets, owner and member permissions, strict isolation.
22. **Custom documents**: reusable layouts (expense voucher, reimbursement form, payment confirmation, custom) with fields, typography, accent colour, logo, preview and PDF export; fixed "personal record" notice.
23. **Financial audit trail**: structured, read-only history of financial changes with before/after values, written in the same DB transaction as the change.
24. **Support Center**: tickets (category, priority, optional linked payment), persistent live chat with attachments, read receipts, reopen/close, history.
25. **Notifications**: in-app bell, live updates, preferences per type, security alert on a new device.
26. **Settings**: profile and avatar (Cloudinary), currency/date/number formats, categories manager, notification preferences, devices & sessions (revoke), password change, **full JSON data export**, **account deletion** (de-identifies; the ledger is retained in de-identified form).

### C. Admin console (`/admin/*`)
27. **Dashboard**: user/transaction/payment/ticket figures, 14-day activity chart, system health (DB latency, live sockets, heap, uptime, stale background work), recent audit events.
28. **Users**: search/filter, suspend / reactivate with a mandatory reason (revokes sessions).
29. **Payment monitoring**: filters, suspicious flags (high amount, velocity, repeated failures), detail history, administrative refund, dispute rejection (reason required, audited).
30. **Support queue** with live chat, status/priority/assignment, internal history.
31. **Audit logs** (append-only, filterable, viewing is itself audited) and a live **activity timeline**.
32. **Oversight**: default categories, and time-boxed (24 h) **investigations** that expose counts and balances only.
33. **Announcements**: draft → publish to all / new users / specific emails, delivery and read counts; publishing is idempotent.
34. **Platform reports** (CSV/PDF, aggregated, no personal records).
35. **Configuration**: whitelisted runtime settings (supported currencies, payment limits, ticket reopen window), each change audited with old and new value. Security settings stay in the environment.
36. **Admin profile & settings** (profile, avatar, notifications, sessions, password).

---

## 3. Tech stack

### Backend
| Concern | Technology |
|---|---|
| Language / runtime | Java 25 (`java.version` in `pom.xml`) |
| Framework | Spring Boot **4.0.7** (Spring MVC, Spring Security, Spring Data JPA/Hibernate, Spring WebSocket, Validation, Actuator, Caching) |
| Database access | Spring Data JPA for entities, `JdbcTemplate` for reporting/ledger SQL |
| Migrations | Flyway (14 versioned SQL files) |
| Password hashing | Argon2id (`Argon2PasswordEncoder`, BouncyCastle) |
| Sessions | Opaque 256-bit token in an HttpOnly cookie; only its SHA-256 is stored |
| Cache / rate limit | Caffeine (in-memory, per instance) |
| PDF / QR | OpenPDF, ZXing |
| Email | Brevo HTTP API (`BrevoEmailSender`) / dev `LoggingEmailSender` |
| Files | Cloudinary signed uploads (JDK `HttpClient`) |
| API docs | springdoc-openapi (dev only; disabled in prod) |
| Build / test | Maven · JUnit 5 · Mockito · Testcontainers (PostgreSQL 17) · failsafe for `*IT` |
| Packaging | Dockerfile (multi-stage, non-root) built by Render |

### Frontend
| Concern | Technology |
|---|---|
| Framework | React **19**, TypeScript ~5.9 |
| Build | Vite **7** |
| Styling | Tailwind CSS 3.4 with CSS-variable design tokens (dark/light) |
| Routing | React Router 7 |
| Server state | TanStack Query 5 |
| Forms / validation | React Hook Form + Zod (rules mirrored from the server) |
| HTTP | Axios (credentials, CSRF handling, one automatic retry) |
| 3D | three.js + @react-three/fiber (landing page only, lazy loaded) |
| Icons | lucide-react |
| Tests | Jest 30 + Testing Library · Playwright (e2e smoke) |

### Services
Neon (PostgreSQL) · Render (API) · Vercel (SPA) · Brevo (email) · Cloudinary (files) · GitHub Actions (CI).

---

## 4. Architecture

```
 Browser (React SPA, Vercel)
   │   HTTPS + cookies (fp_session, fp_csrf)           wss:// /ws (hints only)
   ▼
 Render web service  ──  Spring Boot (Docker image built from backend/Dockerfile)
   │  filters: RequestId → RequestSizeLimit → CORS → CSRF → SessionAuth → AuthZ
   │  modules: auth · finance · planning · automation · payments · files · templates ·
   │           documents · reconciliation · sandbox · reports · households · notify ·
   │           support · admin · account · audit
   ├──► Neon PostgreSQL   (Flyway-managed schema, source of truth)
   ├──► Brevo API         (verification & reset emails)
   └──► Cloudinary        (receipts, avatars, attachments; private files streamed via the API)
```

**Modular monolith.** One deployable, packages by domain under `com.flowpilot.*`. Controllers are thin; services own transactions.

**Money & ledger.** `numeric(19,4)`, JSON money is sent as **strings**. Each transaction writes immutable `ledger_entries` (DB triggers forbid UPDATE/DELETE); account balances are updated under row locks in a stable order; a CHECK constraint stops overdrafts on normal accounts; idempotency keys make retries safe.

**Two audit trails.** `audit_events` (security/admin, written in its own transaction so failures are recorded) and `financial_audit` (user-visible financial history, written *in the same transaction* as the change). Both are append-only at the database level.

**Real time.** PostgreSQL stays the source of truth. The WebSocket sends only `{type:"refresh", topic}` hints; clients re-read through REST.

**Request lifecycle (authenticated mutation).** SPA fetches `GET /api/auth/csrf` once → sends `X-XSRF-TOKEN` header + `fp_session` cookie → CORS → CSRF check → session lookup (hash of cookie token, idle + absolute expiry) → role check (`@PreAuthorize`) → service transaction → JSON response or the uniform error body.

**Uniform error body**
```json
{ "code": "VALIDATION_FAILED", "message": "…", "fieldErrors": { "email": "…" }, "requestId": "…", "timestamp": "…" }
```

---

## 5. Repository layout (full file tree)

```text
flowpilot/
├── .github/   # CI
│   └── workflows/   # GitHub Actions
│       ├── ci.yml   # build, test, e2e, audit, secret scan
│       └── deploy.yml   # optional Render deploy hook
├── backend/   # Spring Boot 4 / Java 25 API
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── flowpilot/
│   │   │   │           ├── account/   # profile, onboarding, categories, export/delete, demo data
│   │   │   │           │   ├── AccountDtos.java
│   │   │   │           │   ├── AccountSettingsController.java
│   │   │   │           │   ├── CategoryService.java
│   │   │   │           │   ├── DemoController.java
│   │   │   │           │   ├── DemoDataService.java
│   │   │   │           │   ├── OnboardingService.java
│   │   │   │           │   └── ProfileService.java
│   │   │   │           ├── admin/   # admin console API + runtime settings
│   │   │   │           │   ├── AdminController.java
│   │   │   │           │   ├── AdminDtos.java
│   │   │   │           │   ├── AdminService.java
│   │   │   │           │   └── SettingsService.java
│   │   │   │           ├── audit/   # security audit + user-visible financial audit trail
│   │   │   │           │   ├── AuditService.java
│   │   │   │           │   ├── FinancialAudit.java
│   │   │   │           │   └── FinancialAuditController.java
│   │   │   │           ├── auth/   # register, verify, login, reset, sessions API
│   │   │   │           │   ├── AccountController.java
│   │   │   │           │   ├── AdminBootstrap.java
│   │   │   │           │   ├── AuthController.java
│   │   │   │           │   ├── AuthDtos.java
│   │   │   │           │   ├── AuthService.java
│   │   │   │           │   └── PasswordPolicy.java
│   │   │   │           ├── automation/   # money-automation engine + rules
│   │   │   │           │   ├── AutomationController.java
│   │   │   │           │   ├── AutomationDtos.java
│   │   │   │           │   ├── AutomationEngine.java
│   │   │   │           │   ├── AutomationService.java
│   │   │   │           │   └── IncomePosted.java
│   │   │   │           ├── common/   # errors, rate limiter, request id, size limit
│   │   │   │           │   ├── ApiError.java
│   │   │   │           │   ├── ApiException.java
│   │   │   │           │   ├── ClientInfo.java
│   │   │   │           │   ├── GlobalExceptionHandler.java
│   │   │   │           │   ├── RateLimiter.java
│   │   │   │           │   ├── RequestIdFilter.java
│   │   │   │           │   └── RequestSizeLimitFilter.java
│   │   │   │           ├── config/   # typed properties + production config validator
│   │   │   │           │   ├── AppProperties.java
│   │   │   │           │   └── ProductionConfigValidator.java
│   │   │   │           ├── csv/   # CSV parser, column mapping, recurring detector
│   │   │   │           │   ├── CsvMapping.java
│   │   │   │           │   ├── CsvParser.java
│   │   │   │           │   └── RecurringDetector.java
│   │   │   │           ├── documents/   # custom printable documents (PDF)
│   │   │   │           │   ├── DocumentController.java
│   │   │   │           │   ├── DocumentDtos.java
│   │   │   │           │   └── DocumentService.java
│   │   │   │           ├── domain/   # JPA entities (users, sessions, tokens, audit)
│   │   │   │           │   ├── AuditEvent.java
│   │   │   │           │   ├── AuthSession.java
│   │   │   │           │   ├── EmailToken.java
│   │   │   │           │   ├── Role.java
│   │   │   │           │   ├── TokenPurpose.java
│   │   │   │           │   ├── User.java
│   │   │   │           │   ├── UserProfile.java
│   │   │   │           │   └── UserStatus.java
│   │   │   │           ├── files/   # Cloudinary client, validators, receipts, memos
│   │   │   │           │   ├── CloudinaryClient.java
│   │   │   │           │   ├── FilesController.java
│   │   │   │           │   ├── FileStorageService.java
│   │   │   │           │   ├── FileValidator.java
│   │   │   │           │   ├── MemoService.java
│   │   │   │           │   └── ReceiptService.java
│   │   │   │           ├── finance/   # accounts, immutable ledger, dashboard
│   │   │   │           │   ├── Account.java
│   │   │   │           │   ├── AccountRepository.java
│   │   │   │           │   ├── AccountService.java
│   │   │   │           │   ├── AccountType.java
│   │   │   │           │   ├── Category.java
│   │   │   │           │   ├── CategoryKind.java
│   │   │   │           │   ├── CategoryRepository.java
│   │   │   │           │   ├── DashboardService.java
│   │   │   │           │   ├── FinanceController.java
│   │   │   │           │   ├── FinanceDtos.java
│   │   │   │           │   ├── FinanceTransaction.java
│   │   │   │           │   ├── FinanceTransactionRepository.java
│   │   │   │           │   ├── LedgerEntry.java
│   │   │   │           │   ├── LedgerEntryRepository.java
│   │   │   │           │   ├── LedgerRow.java
│   │   │   │           │   ├── LedgerService.java
│   │   │   │           │   ├── TransactionSpecs.java
│   │   │   │           │   ├── TransactionType.java
│   │   │   │           │   └── TxStatus.java
│   │   │   │           ├── households/   # shared expenses, splits, settle-up
│   │   │   │           │   ├── HouseholdController.java
│   │   │   │           │   ├── HouseholdDtos.java
│   │   │   │           │   ├── HouseholdService.java
│   │   │   │           │   └── SplitCalculator.java
│   │   │   │           ├── jobs/   # retention job
│   │   │   │           │   └── MaintenanceJobs.java
│   │   │   │           ├── logging/   # SecretLogFilter (drops lines containing DB URLs/credentials)
│   │   │   │           │   └── SecretLogFilter.java
│   │   │   │           ├── mail/   # Brevo sender + dev log sender + templates
│   │   │   │           │   ├── BrevoEmailSender.java
│   │   │   │           │   ├── EmailDeliveryException.java
│   │   │   │           │   ├── EmailSender.java
│   │   │   │           │   ├── EmailTemplates.java
│   │   │   │           │   ├── LoggingEmailSender.java
│   │   │   │           │   └── OutgoingEmail.java
│   │   │   │           ├── notify/   # notifications, WebSocket, reminder jobs
│   │   │   │           │   ├── LiveUpdateHandler.java
│   │   │   │           │   ├── LiveUpdateRegistry.java
│   │   │   │           │   ├── NotificationController.java
│   │   │   │           │   ├── NotificationService.java
│   │   │   │           │   ├── ReminderJobs.java
│   │   │   │           │   └── WebSocketConfig.java
│   │   │   │           ├── payments/   # payment simulator (QR, refunds, disputes, PDF)
│   │   │   │           │   ├── PaymentController.java
│   │   │   │           │   ├── PaymentDtos.java
│   │   │   │           │   ├── PaymentJobs.java
│   │   │   │           │   ├── PaymentReceiptPdf.java
│   │   │   │           │   ├── PaymentService.java
│   │   │   │           │   └── Qr.java
│   │   │   │           ├── planning/   # budgets, bills, goals
│   │   │   │           │   ├── BillMath.java
│   │   │   │           │   ├── BillService.java
│   │   │   │           │   ├── BudgetService.java
│   │   │   │           │   ├── GoalService.java
│   │   │   │           │   ├── PlanningController.java
│   │   │   │           │   └── PlanningDtos.java
│   │   │   │           ├── reconciliation/   # statement import and matching
│   │   │   │           │   ├── ReconciliationController.java
│   │   │   │           │   ├── ReconciliationDtos.java
│   │   │   │           │   ├── ReconciliationService.java
│   │   │   │           │   └── StatementMatcher.java
│   │   │   │           ├── repo/   # Spring Data repositories
│   │   │   │           │   ├── AuditEventRepository.java
│   │   │   │           │   ├── AuthSessionRepository.java
│   │   │   │           │   ├── EmailTokenRepository.java
│   │   │   │           │   ├── UserProfileRepository.java
│   │   │   │           │   └── UserRepository.java
│   │   │   │           ├── reports/   # reports, calendar, pipeline, CSV/PDF export
│   │   │   │           │   ├── CalendarService.java
│   │   │   │           │   ├── ExportService.java
│   │   │   │           │   ├── PipelineService.java
│   │   │   │           │   ├── ReportController.java
│   │   │   │           │   └── ReportService.java
│   │   │   │           ├── sandbox/   # what-if projection engine
│   │   │   │           │   ├── SandboxController.java
│   │   │   │           │   ├── SandboxEngine.java
│   │   │   │           │   └── SandboxService.java
│   │   │   │           ├── security/   # session auth filter, CSRF, CORS, cookies, tokens
│   │   │   │           │   ├── AuthPrincipal.java
│   │   │   │           │   ├── CookieService.java
│   │   │   │           │   ├── SecurityConfig.java
│   │   │   │           │   ├── SessionAuthFilter.java
│   │   │   │           │   ├── SessionService.java
│   │   │   │           │   └── TokenService.java
│   │   │   │           ├── support/   # tickets + live chat
│   │   │   │           │   ├── SupportController.java
│   │   │   │           │   ├── SupportDtos.java
│   │   │   │           │   └── SupportService.java
│   │   │   │           ├── templates/   # finance templates
│   │   │   │           │   ├── TemplateController.java
│   │   │   │           │   ├── TemplateDtos.java
│   │   │   │           │   └── TemplateService.java
│   │   │   │           └── FlowPilotApplication.java
│   │   │   └── resources/   # config + migrations
│   │   │       ├── db/   # Flyway
│   │   │       │   └── migration/   # V1…V14 SQL migrations
│   │   │       │       ├── V10__planning_sandbox.sql
│   │   │       │       ├── V11__reconciliation.sql
│   │   │       │       ├── V12__support_admin.sql
│   │   │       │       ├── V13__audit_trail_households_documents.sql
│   │   │       │       ├── V14__dashboard_layout.sql
│   │   │       │       ├── V1__identity_sessions_audit.sql   # users, sessions, tokens, audit
│   │   │       │       ├── V2__finance_core_ledger.sql
│   │   │       │       ├── V3__default_categories.sql
│   │   │       │       ├── V4__budgets_bills_goals.sql
│   │   │       │       ├── V5__notifications_settings_onboarding.sql
│   │   │       │       ├── V6__files_receipts_memos.sql
│   │   │       │       ├── V7__finance_templates.sql
│   │   │       │       ├── V8__payment_simulator.sql
│   │   │       │       └── V9__money_automation.sql
│   │   │       ├── application-dev.yml   # dev profile
│   │   │       ├── application-prod.yml   # prod profile
│   │   │       ├── application.yml   # base config (env placeholders)
│   │   │       └── logback-spring.xml   # log setup + SecretLogFilter
│   │   └── test/   # unit tests + Testcontainers IT
│   │       └── java/
│   │           └── com/
│   │               └── flowpilot/
│   │                   ├── admin/   # admin console API + runtime settings
│   │                   │   └── SettingsValidationTest.java
│   │                   ├── auth/   # register, verify, login, reset, sessions API
│   │                   │   ├── AuthServiceTest.java
│   │                   │   ├── PasswordPolicyTest.java
│   │                   │   └── TokenServiceTest.java
│   │                   ├── automation/   # money-automation engine + rules
│   │                   │   └── AutomationEngineTest.java
│   │                   ├── common/   # errors, rate limiter, request id, size limit
│   │                   │   └── RateLimiterTest.java
│   │                   ├── csv/   # CSV parser, column mapping, recurring detector
│   │                   │   ├── CsvTest.java
│   │                   │   └── RecurringDetectorTest.java
│   │                   ├── files/   # Cloudinary client, validators, receipts, memos
│   │                   │   └── FileSecurityTest.java
│   │                   ├── finance/   # accounts, immutable ledger, dashboard
│   │                   │   └── LedgerServiceTest.java
│   │                   ├── households/   # shared expenses, splits, settle-up
│   │                   │   └── SplitCalculatorTest.java
│   │                   ├── it/
│   │                   │   └── PlatformIT.java   # Testcontainers integration tests
│   │                   ├── payments/   # payment simulator (QR, refunds, disputes, PDF)
│   │                   │   └── QrTest.java
│   │                   ├── planning/   # budgets, bills, goals
│   │                   │   └── BillMathTest.java
│   │                   ├── reconciliation/   # statement import and matching
│   │                   │   └── StatementMatcherTest.java
│   │                   ├── reports/   # reports, calendar, pipeline, CSV/PDF export
│   │                   │   └── ExportServiceTest.java
│   │                   ├── sandbox/   # what-if projection engine
│   │                   │   └── SandboxEngineTest.java
│   │                   └── templates/   # finance templates
│   │                       └── TemplateValidationTest.java
│   ├── Dockerfile   # built by Render (no local Docker needed)
│   └── pom.xml   # Maven build
├── docs/   # Extra documentation
│   ├── ARCHITECTURE.md
│   ├── DEPLOYMENT.md
│   ├── PRODUCTION_CHECKLIST.md
│   ├── SECURITY.md
│   ├── STATUS.md
│   └── TESTING.md
├── frontend/   # React 19 + Vite SPA
│   ├── e2e/   # Playwright smoke tests
│   │   └── smoke.spec.ts
│   ├── public/   # static assets
│   │   └── favicon.svg
│   ├── src/
│   │   ├── __tests__/   # Jest + Testing Library
│   │   │   ├── CookieBanner.test.tsx
│   │   │   ├── finance.test.tsx
│   │   │   ├── landing.test.tsx
│   │   │   ├── ProtectedRoute.test.tsx
│   │   │   ├── RegisterPage.test.tsx
│   │   │   ├── templates.test.ts
│   │   │   ├── ThemeProvider.test.tsx
│   │   │   └── validation.test.ts
│   │   ├── auth/   # register, verify, login, reset, sessions API
│   │   │   ├── AuthContext.tsx
│   │   │   └── ProtectedRoute.tsx
│   │   ├── components/   # shared UI
│   │   │   ├── Alert.tsx
│   │   │   ├── AuthShell.tsx
│   │   │   ├── AvatarUploader.tsx
│   │   │   ├── Button.tsx
│   │   │   ├── Charts.tsx
│   │   │   ├── ChatPanel.tsx
│   │   │   ├── CookieBanner.tsx
│   │   │   ├── CsvMapper.tsx
│   │   │   ├── FinancialPipeline.tsx
│   │   │   ├── Logo.tsx
│   │   │   ├── MiniBars.tsx
│   │   │   ├── NotificationBell.tsx
│   │   │   ├── ProgressBar.tsx
│   │   │   ├── SecurityPanel.tsx
│   │   │   ├── SelectField.tsx
│   │   │   ├── StatCard.tsx
│   │   │   ├── TemplateForm.tsx
│   │   │   ├── TextField.tsx
│   │   │   └── ThemeToggle.tsx
│   │   ├── landing/   # cinematic landing page (React Three Fiber + CSS fallback)
│   │   │   ├── Curtain.tsx
│   │   │   ├── hooks.ts
│   │   │   ├── LandingPage.tsx
│   │   │   ├── Previews.tsx
│   │   │   ├── Scene3D.tsx
│   │   │   └── SceneHost.tsx
│   │   ├── lib/   # api client, money, validation, live updates, types
│   │   │   ├── api.ts
│   │   │   ├── consent.ts
│   │   │   ├── download.ts
│   │   │   ├── env.ts
│   │   │   ├── files.ts
│   │   │   ├── live.ts
│   │   │   ├── money.ts
│   │   │   ├── templates.ts
│   │   │   ├── types.ts
│   │   │   └── validation.ts
│   │   ├── pages/   # route components
│   │   │   ├── admin/   # admin console API + runtime settings
│   │   │   │   ├── AdminAnnouncementsPage.tsx
│   │   │   │   ├── AdminAuditPage.tsx
│   │   │   │   ├── AdminConfigPage.tsx
│   │   │   │   ├── AdminHome.tsx
│   │   │   │   ├── AdminLoginPage.tsx
│   │   │   │   ├── AdminOversightPage.tsx
│   │   │   │   ├── AdminPaymentsPage.tsx
│   │   │   │   ├── AdminReportsPage.tsx
│   │   │   │   ├── AdminSettingsPage.tsx
│   │   │   │   ├── AdminSupportPage.tsx
│   │   │   │   └── AdminUsersPage.tsx
│   │   │   ├── legal/
│   │   │   │   └── LegalPage.tsx
│   │   │   ├── user/
│   │   │   │   ├── AccountsPage.tsx
│   │   │   │   ├── AuditTrailPage.tsx
│   │   │   │   ├── AutomationPage.tsx
│   │   │   │   ├── BillsPage.tsx
│   │   │   │   ├── BudgetsPage.tsx
│   │   │   │   ├── CalendarPage.tsx
│   │   │   │   ├── CheckEmailPage.tsx
│   │   │   │   ├── DashboardPage.tsx
│   │   │   │   ├── DocumentsPage.tsx
│   │   │   │   ├── ForgotPasswordPage.tsx
│   │   │   │   ├── GoalsPage.tsx
│   │   │   │   ├── HouseholdsPage.tsx
│   │   │   │   ├── LoginForm.tsx
│   │   │   │   ├── LoginPage.tsx
│   │   │   │   ├── NotificationsPage.tsx
│   │   │   │   ├── OnboardingPage.tsx
│   │   │   │   ├── PaymentsPage.tsx
│   │   │   │   ├── ReceiptsPage.tsx
│   │   │   │   ├── ReconcilePage.tsx
│   │   │   │   ├── RecurringPage.tsx
│   │   │   │   ├── RegisterPage.tsx
│   │   │   │   ├── ReportsPage.tsx
│   │   │   │   ├── ResetPasswordPage.tsx
│   │   │   │   ├── SandboxPage.tsx
│   │   │   │   ├── SettingsPage.tsx
│   │   │   │   ├── SupportPage.tsx
│   │   │   │   ├── TemplatesPage.tsx
│   │   │   │   ├── TransactionForm.tsx
│   │   │   │   ├── TransactionsPage.tsx
│   │   │   │   ├── TransactionTable.tsx
│   │   │   │   └── VerifyEmailPage.tsx
│   │   │   ├── AppShell.tsx
│   │   │   └── NotFoundPage.tsx
│   │   ├── test/   # unit tests + Testcontainers IT
│   │   │   ├── envShim.ts
│   │   │   └── setup.ts
│   │   ├── theme/   # dark/light ThemeProvider
│   │   │   └── ThemeProvider.tsx
│   │   ├── App.tsx   # route table
│   │   ├── index.css
│   │   └── main.tsx
│   ├── .env.example   # template for your private .env
│   ├── index.html
│   ├── jest.config.cjs
│   ├── package-lock.json
│   ├── package.json   # scripts + dependencies
│   ├── playwright.config.ts
│   ├── postcss.config.js
│   ├── tailwind.config.js
│   ├── tsconfig.jest.json
│   ├── tsconfig.json
│   ├── vercel.json   # Vercel config: SPA rewrite, CSP, security headers
│   └── vite.config.ts
├── .env.example   # template for your private .env
├── .gitignore
├── docker-compose.yml   # optional local PostgreSQL
├── README.md
└── render.yaml   # Render blueprint
```

---

## 6. Getting started locally (clone → run)

### 6.1 Prerequisites
| Tool | Version | Why |
|---|---|---|
| Git | any | clone |
| JDK | **25** | backend (`java -version`) |
| Maven | 3.9+ | build/run backend |
| Node.js | **22+** (npm 10+) | frontend |
| PostgreSQL | **17** (local) *or* a free Neon database | data. `docker compose up -d db` starts a local one if you have Docker (optional, local only) |

### 6.2 Clone
```bash
git clone https://github.com/zobbygit/FlowPilot-A-secure-Financial-Platform
cd flowpilot
```

### 6.3 Database
*Option A, local Docker:* `docker compose up -d db` (database `flowpilot`, user `flowpilot`, password `flowpilot_dev_only`, port 5432).
*Option B, Neon:* create a project and use the connection details as described in [§16.2](#162-neon-database).
Tables are created automatically by Flyway at first start.

### 6.4 Backend configuration
Copy the template and fill it in. **The file must be named `.env` and live in the project root or in `backend/`** (the API reads it at start-up through `spring.config.import`; it is git-ignored).
```bash
cp .env.example .env
```
A complete `.env` for local development (placeholders only, never commit real values):
```properties
SPRING_PROFILES_ACTIVE=dev

DATABASE_URL=jdbc:postgresql://localhost:5432/flowpilot
DATABASE_USERNAME=flowpilot
DATABASE_PASSWORD=flowpilot_dev_only

FLOWPILOT_PUBLIC_BASE_URL=http://localhost:5173
FLOWPILOT_CORS_ALLOWED_ORIGINS=http://localhost:5173
FLOWPILOT_COOKIE_SECURE=false
FLOWPILOT_COOKIE_SAMESITE=Lax
FLOWPILOT_SESSION_IDLE=PT2H
FLOWPILOT_SESSION_ABSOLUTE=P7D

# Leave BREVO_API_KEY empty to print emails (with their links) in the console instead of sending them
BREVO_ENABLED=true
BREVO_API_KEY=
BREVO_SENDER_EMAIL=
BREVO_SENDER_NAME=FlowPilot

FLOWPILOT_ADMIN_BOOTSTRAP_EMAIL=admin@example.com
FLOWPILOT_ADMIN_BOOTSTRAP_PASSWORD=ChangeMe-Long-Pass-123!
FLOWPILOT_ADMIN_BOOTSTRAP_NAME=Administrator

CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
```
> `.env` rules: one `KEY=VALUE` per line, **no comment after a value**, no quotes needed, no spaces around `=`. Real environment variables always override `.env`.

### 6.5 Run the backend
```bash
cd backend
mvn spring-boot:run                 # development
# or
mvn -DskipTests package && java -jar target/flowpilot-api-0.1.0-SNAPSHOT.jar
```
API: `http://localhost:8080` · health: `http://localhost:8080/actuator/health` · OpenAPI UI (dev, admin session needed): `/swagger-ui.html`.

### 6.6 Run the frontend
```bash
cd frontend
cp .env.example .env                # contains VITE_API_BASE_URL=http://localhost:8080/api
npm install
npm run dev                         # http://localhost:5173
```

### 6.7 First sign-in
- **User:** open `/user/register`, register, then verify. With no Brevo key the verification link is printed in the backend console (look for `DEV MODE - email NOT sent`).
- **Admin:** with the two `FLOWPILOT_ADMIN_BOOTSTRAP_*` variables set, the admin is created at start-up. Sign in at `/admin/login`. Remove the variables afterwards.

### 6.8 Everyday commands
| Task | Command |
|---|---|
| Backend unit tests | `cd backend && mvn test` |
| Backend unit + integration (needs Docker) | `cd backend && mvn verify` |
| Frontend unit tests | `cd frontend && npm test` |
| Frontend type check | `cd frontend && npm run typecheck` |
| Frontend production build | `cd frontend && npm run build` (`npm run build:check` = type check + build) |
| Browser smoke tests | `cd frontend && npm run e2e` (stack running) |

---

## 7. Environment variables

### 7.1 Backend (`.env` locally · Render dashboard in production)
| Variable | Default | Required in production | Purpose |
|---|---|---|---|
| `DATABASE_URL` | _(empty)_ | Yes | JDBC URL of PostgreSQL, e.g. `jdbc:postgresql://HOST/DB?sslmode=require` (Neon: use the **direct** host, not `-pooler`). |
| `DATABASE_USERNAME` | _(empty)_ | Yes | Database role name only (no password, no `KEY=` prefix). |
| `DATABASE_PASSWORD` | _(empty)_ | Yes | Database password. Secret. |
| `DB_POOL_SIZE` | `5` | No | Maximum Hikari connections. Small on purpose for Neon/free tiers. |
| `PORT` | `8080` | Set by Render | HTTP port. Render injects it; locally 8080. |
| `FLOWPILOT_PUBLIC_BASE_URL` | `http://localhost:5173` | Yes | Frontend origin used inside emails (verify / reset links). `https://…` in production, no trailing slash. |
| `FLOWPILOT_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Yes | Comma-separated exact frontend origins allowed by CORS and by the WebSocket origin check. Wildcards are refused in prod. |
| `FLOWPILOT_COOKIE_SECURE` | `false` | Yes (`true`) | `Secure` flag for session and CSRF cookies. `false` only for http://localhost. Production refuses to start unless `true`. |
| `FLOWPILOT_COOKIE_SAMESITE` | `Lax` | Yes | `Lax` (same-site: own domain or localhost), `Strict`, or `None` (cross-site, e.g. vercel.app + onrender.com; requires Secure). |
| `FLOWPILOT_SESSION_IDLE` | `PT2H` | No | Idle session timeout, ISO-8601 duration (default `PT2H`). Every request refreshes it. |
| `FLOWPILOT_SESSION_ABSOLUTE` | `P7D` | No | Absolute session lifetime (default `P7D`) no matter how active. |
| `BREVO_ENABLED` | `true` | No | `true` (default). Real emails are sent whenever `BREVO_API_KEY` is set; `false` forces log-only mode. |
| `BREVO_API_KEY` | _(empty)_ | Yes | Brevo v3 API key (`xkeysib-…`). Secret. Empty ⇒ dev log-only sender (refused in prod). |
| `BREVO_SENDER_EMAIL` | _(empty)_ | Yes | Sender address that is **verified in Brevo**. |
| `BREVO_SENDER_NAME` | `FlowPilot` | No | Display name on emails (default `FlowPilot`). |
| `FLOWPILOT_ADMIN_BOOTSTRAP_EMAIL` | _(empty)_ | First start only | Email of the first administrator (valid address). Remove after the first successful start. |
| `FLOWPILOT_ADMIN_BOOTSTRAP_PASSWORD` | _(empty)_ | First start only | Password of the first admin (12+ chars, 3 character classes). Secret. Remove afterwards. |
| `FLOWPILOT_ADMIN_BOOTSTRAP_NAME` | `Administrator` | No | Display name of the first admin. |
| `FLOWPILOT_ADMIN_BOOTSTRAP_RESET` | `false` | Recovery only | `true` for ONE start replaces the first admin’s email + password from the two variables above and revokes its sessions. |
| `CLOUDINARY_CLOUD_NAME` | _(empty)_ | Yes | Cloudinary cloud name (letters, digits, `-`, `_`). |
| `CLOUDINARY_API_KEY` | _(empty)_ | Yes | Cloudinary API key. |
| `CLOUDINARY_API_SECRET` | _(empty)_ | Yes | Cloudinary API secret. Secret; never leaves the backend. |
| `FLOWPILOT_TRUSTED_PROXY_REGEX` | `10\.\d{1,3…` | No | Regex of proxy IPs trusted for `X-Forwarded-*` (prod; default covers Render’s private ranges). |
| `SPRING_PROFILES_ACTIVE` | — | Yes (`prod`) | `dev` locally (DEBUG logs, validates schema), `prod` in production (strict config validation, JSON logs, Swagger off). |
| `JAVA_TOOL_OPTIONS` | — | Set in Dockerfile | JVM tuning for small instances (`MaxRAMPercentage=70`, SerialGC…). |
| `TZ` | — | Set in Dockerfile | `UTC`. |

**Precedence (highest → lowest):** real OS/Render environment variables → imported `.env` → `application-<profile>.yml` → `application.yml` defaults.

**Production guard.** With `SPRING_PROFILES_ACTIVE=prod` the application **refuses to start** (message names the variable, never its value) unless: the public URL is `https`, cookies are `Secure`, CORS origins are exact `https` origins (no `*`), Brevo key + verified sender are set, Cloudinary keys are set.

### 7.2 Frontend (`frontend/.env` locally · Vercel → Environment Variables in production)
| Variable | Local value | Production value | Notes |
|---|---|---|---|
| `VITE_API_BASE_URL` | `http://localhost:8080/api` | `https://<your-api-host>/api` | Must include `/api`. **Public**: it ends up in the browser bundle, so never put a secret in a `VITE_*` variable. |

### 7.3 Where each secret lives
| Secret | Stored in | Never in |
|---|---|---|
| DB password, Brevo key, Cloudinary secret, admin bootstrap password | `.env` (local, git-ignored) / Render environment | git, the frontend, logs, screenshots, chat |
| Session / CSRF / email tokens | generated per use; only SHA-256 hashes of session and email tokens are stored | anywhere in plaintext |

---

## 8. Booting: what happens at start-up

1. **Configuration is assembled** (see precedence above) and `SPRING_PROFILES_ACTIVE` selects `dev` or `prod`.
2. **`ProductionConfigValidator`** (prod only) checks the configuration and stops the boot with a clear message if something unsafe or missing.
3. **Connection pool** (HikariCP, `DB_POOL_SIZE`, default 5) connects to PostgreSQL.
4. **Flyway** applies pending migrations `V1…V14` (idempotent; already-applied versions are skipped). *Forward-only: never edit an applied migration.*
5. **Hibernate** validates entities against the schema in `dev` (`ddl-auto: validate`).
6. **Beans start:** security filter chain, WebSocket endpoint `/ws`, schedulers, Brevo or dev email sender.
7. **`AdminBootstrap`** creates the first admin if both bootstrap variables are set and no admin exists (or resets it when `FLOWPILOT_ADMIN_BOOTSTRAP_RESET=true`).
8. **Tomcat listens** on `PORT` (default 8080). Readiness turns green at `/actuator/health/readiness`.

**Healthy start-up log (shape):**
```
The following 1 profile is active: "prod"
EMAIL DELIVERY: Brevo is ACTIVE. Real emails will be sent from <sender>.
Migrating schema "public" to version "N - …"          (only when there is something to apply)
Tomcat started on port 8080 (http)
Started FlowPilotApplication in NN seconds
```
**Never in the log:** database URL/host/user/password, API keys, tokens. Third-party chatter is silenced (Flyway/Hikari/Hibernate at WARN) and `SecretLogFilter` drops any log line containing `jdbc:`, `postgresql://`, `neon.tech`, `password=` or a Brevo key. If you see `EMAIL DELIVERY: LOG-ONLY mode`, emails are **not** being sent (key missing).

**Start-up time:** 20–60 s on small CPUs; on Render's free plan the first request after idle takes about a minute (cold start).
**Graceful shutdown:** in-flight requests are allowed to finish; WebSockets close.

**Health endpoints (public, no details):** `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` (includes the database).

---

## 9. Database

PostgreSQL 17. Schema is owned by Flyway (`backend/src/main/resources/db/migration`).

### 9.1 Migrations (56 tables)
| # | File | Purpose | Tables created |
|---|---|---|---|
| 1 | `V1__identity_sessions_audit.sql` | V1: identity, tokens, server-side sessions, append-only audit log | `users`, `user_profiles`, `email_tokens`, `auth_sessions`, `audit_events` |
| 2 | `V2__finance_core_ledger.sql` | V2: finance core. NOT yet exposed by any API (see docs/STATUS.md) - the schema is fixed first | `categories`, `accounts`, `transactions`, `ledger_entries`, `idempotency_keys` |
| 3 | `V3__default_categories.sql` | Platform default categories (user_id NULL). Users can add their own later | — |
| 4 | `V4__budgets_bills_goals.sql` | V4: budgets, bills & subscriptions, savings goals | `budgets`, `bills`, `bill_price_changes`, `savings_goals`, `goal_contributions` |
| 5 | `V5__notifications_settings_onboarding.sql` | V5: notifications (+preferences), profile settings, onboarding | `user_preferred_categories`, `notifications`, `notification_preferences` |
| 6 | `V6__files_receipts_memos.sql` | V6: file metadata, receipts (+status history), memos | `stored_files`, `receipts`, `receipt_status_history`, `memos` |
| 7 | `V7__finance_templates.sql` | V7: private, reusable recording forms ("My Finance Templates") + four built-in templates | `finance_templates`, `template_fields` |
| 8 | `V8__payment_simulator.sql` | V8: SIMULATED payments between FlowPilot users. No real money, banks or UPI rails are involved | `payment_profiles`, `payment_requests`, `payments`, `payment_events`, `payment_refunds`, `payment_disputes` |
| 9 | `V9__money_automation.sql` | V9: money automation. Rules create PLANNED allocations only (intent + reservation); they never move money | `automation_rules`, `automation_actions`, `automation_runs`, `automation_allocations` |
| 10 | `V10__planning_sandbox.sql` | V10: hypothetical scenarios. These tables hold ASSUMPTIONS only; nothing here ever touches balances or the ledger | `sandbox_scenarios`, `sandbox_events` |
| 11 | `V11__reconciliation.sql` | V11: statement reconciliation. Imports never modify existing transactions: they only record match decisions | `statement_imports`, `statement_rows` |
| 12 | `V12__support_admin.sql` | V12: complaint tickets + live chat, admin announcements, investigations, platform settings | `support_tickets`, `support_messages`, `support_events`, `announcements`, `admin_investigations`, `platform_settings` |
| 13 | `V13__audit_trail_households_documents.sql` | V13: user-facing financial audit trail, shared households, custom document layouts | `financial_audit`, `households`, `household_members`, `household_invitations`, `household_expenses`, `household_splits`, `household_settlements`, `household_budgets`, `document_templates`, `document_fields`, `generated_documents` |
| 14 | `V14__dashboard_layout.sql` | V14: per-user dashboard widget layout (visibility + order). Purely presentational | `dashboard_layouts` |

### 9.2 Guarantees enforced by the database itself
- `ledger_entries`, `audit_events`, `financial_audit`, `support_events` reject `UPDATE`/`DELETE` (trigger `forbid_mutation`).
- Account balance cannot go negative on normal accounts (CHECK); a balance always equals the sum of its ledger entries.
- Unique indexes make retries and races safe: idempotency keys, one open payment per request, one automation run per rule+trigger, notification de-duplication keys, one pending household invitation, statement-file hashes.
- Foreign keys use `ON DELETE RESTRICT` for anything financial or audited.
- Money columns are `numeric(19,4)`; timestamps are `timestamptz` (UTC).

---

## 10. API reference

**Base URL:** `http://localhost:8080/api` locally · `https://<api-host>/api` in production · **170 endpoints**.

### 10.1 Conventions
| Topic | Rule |
|---|---|
| Authentication | Cookie `fp_session` (HttpOnly, Secure in prod). Obtained from `POST /api/auth/login` (users) or `POST /api/auth/admin/login` (admins). |
| CSRF | Every non-GET request needs header `X-XSRF-TOKEN: <token>`. Get the token with `GET /api/auth/csrf` (JSON). The `fp_csrf` cookie must accompany it. File uploads also carry the token in a `_csrf` form field. |
| Roles | `public` · `signed-in` (any role) · `USER` · `ADMIN`. A wrong role gets `403`; a missing session gets `401`. |
| Bodies | JSON (`Content-Type: application/json`) except uploads (`multipart/form-data`, file field `file`). |
| Money | Decimal **strings** (`"12.50"`), never floating-point numbers. |
| Dates | ISO-8601 (`2026-10-28`, `2026-10-28T12:00:00Z`). |
| Pagination | `?page=0&size=20` (size is capped; lists return `{items, page, size, total}`). |
| Idempotency | `Idempotency-Key` header (≤ 80 chars) on `POST /api/transactions` and payment initiation: a retry returns the original result. |
| Errors | `{code, message, fieldErrors?, requestId, timestamp}`; common codes: `VALIDATION_FAILED`, `UNAUTHENTICATED`, `FORBIDDEN`, `CSRF_INVALID`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED` (429), `PAYLOAD_TOO_LARGE` (413). Messages never contain stack traces or SQL. |
| Request id | Every response carries `X-Request-Id`; quote it when reporting a problem. |

### 10.2 Example (curl)
```bash
BASE=http://localhost:8080/api
curl -s -c jar -b jar $BASE/auth/csrf                                   # → {"token":"…"}
curl -s -c jar -b jar -H "X-XSRF-TOKEN: <token>" -H "Content-Type: application/json" \
     -d '{"email":"you@example.com","password":"your-password"}' $BASE/auth/login
curl -s -b jar $BASE/accounts                                           # authenticated GET
```

### 10.3 Rate limits (per instance, in memory)
| Action | Limit |
|---|---|
| Register | 10 / hour / IP · 5 / hour / email |
| Resend verification | 10 / hour / IP · 3 / hour / email |
| Verify email | 30 / hour / IP |
| Login | 30 / 10 min / IP · 10 / 10 min / email · lockout after **5** wrong passwords for **15 min** |
| Forgot password | 10 / hour / IP · 3 / hour / email · reset 20 / hour / IP |
| Change password | 5 / 10 min / user |
| Data export / account deletion | 5 / hour · 3 / hour |
| Payments | initiate 30/min · lookups 30/min · requests 20/min |
| Support | 5 tickets / hour · 30 messages / min |
| Report exports | 20 / 10 min (user and admin) · admin refunds 10 / 10 min |

### 10.4 Endpoint tree
```text
/
└── api
    ├── accounts  [GET, POST]
    │   └── {id}  [DELETE]
    ├── admin
    │   ├── announcements  [GET, POST]
    │   │   └── {id}  [DELETE, PUT]
    │   │       └── publish  [POST]
    │   ├── audit  [GET]
    │   ├── dashboard  [GET]
    │   ├── oversight
    │   │   ├── categories  [GET, POST]
    │   │   │   └── {id}  [PUT]
    │   │   │       ├── archive  [POST]
    │   │   │       └── restore  [POST]
    │   │   └── investigations  [POST]
    │   │       └── {id}  [GET]
    │   ├── payments  [GET]
    │   │   └── {id}  [GET]
    │   │       ├── dispute
    │   │       │   └── reject  [POST]
    │   │       └── refund  [POST]
    │   ├── reports
    │   │   └── {type}  [GET]
    │   │       └── export  [GET]
    │   ├── settings  [GET]
    │   │   └── {key}  [PUT]
    │   ├── timeline  [GET]
    │   └── users  [GET]
    │       └── {id}
    │           ├── reactivate  [POST]
    │           └── suspend  [POST]
    ├── audit
    │   └── financial  [GET]
    │       └── types  [GET]
    ├── auth
    │   ├── admin
    │   │   └── login  [POST]
    │   ├── csrf  [GET]
    │   ├── forgot-password  [POST]
    │   ├── login  [POST]
    │   ├── logout  [POST]
    │   ├── me  [GET]
    │   ├── register  [POST]
    │   ├── resend-verification  [POST]
    │   ├── reset-password  [POST]
    │   └── verify-email  [POST]
    ├── automation
    │   ├── allocations
    │   │   └── {id}
    │   │       └── release  [POST]
    │   ├── rules  [GET, POST]
    │   │   └── {id}  [PUT]
    │   │       ├── archive  [POST]
    │   │       ├── pause  [POST]
    │   │       ├── preview  [POST]
    │   │       ├── resume  [POST]
    │   │       └── run  [POST]
    │   └── runs  [GET]
    │       └── {id}
    │           └── retry  [POST]
    ├── bills  [GET, POST]
    │   ├── upcoming  [GET]
    │   └── {id}  [DELETE, PUT]
    │       ├── pay  [POST]
    │       └── status  [PATCH]
    ├── budgets  [GET, POST]
    │   └── {id}  [DELETE]
    ├── calendar  [GET]
    ├── categories  [GET, POST]
    │   ├── manage  [GET]
    │   ├── reorder  [PUT]
    │   └── {id}  [PUT]
    │       ├── archive  [POST]
    │       └── restore  [POST]
    ├── csv
    │   └── preview  [POST]
    ├── dashboard  [GET]
    ├── dashboard-layout  [DELETE, GET, PUT]
    ├── documents
    │   └── templates  [GET, POST]
    │       └── {id}  [PUT]
    │           ├── archive  [POST]
    │           ├── duplicate  [POST]
    │           ├── logo  [DELETE, POST]
    │           ├── pdf  [POST]
    │           ├── preview  [POST]
    │           └── restore  [POST]
    ├── goals  [GET, POST]
    │   └── {id}  [DELETE]
    │       ├── allocations  [POST]
    │       └── transfers  [POST]
    ├── households  [GET, POST]
    │   ├── invitations  [GET]
    │   │   └── {id}
    │   │       ├── accept  [POST]
    │   │       └── decline  [POST]
    │   └── {id}  [GET]
    │       ├── archive  [POST]
    │       ├── budgets  [POST]
    │       │   └── {bid}  [DELETE]
    │       ├── expenses  [GET, POST]
    │       │   └── {eid}  [DELETE]
    │       ├── invitations  [POST]
    │       │   └── {inv}  [DELETE]
    │       ├── members
    │       │   └── {userId}  [DELETE]
    │       └── settlements  [POST]
    ├── notifications  [GET]
    │   ├── preferences  [GET, PUT]
    │   ├── read-all  [POST]
    │   ├── unread-count  [GET]
    │   └── {id}
    │       └── read  [POST]
    ├── onboarding  [GET]
    │   ├── complete  [POST]
    │   └── draft  [PUT]
    ├── payments  [GET, POST]
    │   ├── lookup
    │   │   └── {handle}  [GET]
    │   ├── profile  [GET]
    │   │   └── receive-account  [PUT]
    │   ├── requests  [GET, POST]
    │   │   ├── by-reference
    │   │   │   └── {ref}  [GET]
    │   │   ├── {id}  [DELETE]
    │   │   └── {ref}
    │   │       └── qr  [GET]
    │   └── {id}  [GET]
    │       ├── cancel  [POST]
    │       ├── confirm  [POST]
    │       ├── dispute  [DELETE, POST]
    │       ├── receipt  [GET]
    │       └── refund  [POST]
    ├── pipeline  [GET]
    ├── reconciliation
    │   ├── imports  [GET, POST]
    │   │   └── {id}  [DELETE, GET]
    │   └── rows
    │       └── {id}
    │           ├── confirm  [POST]
    │           ├── record  [POST]
    │           ├── reject  [POST]
    │           └── undo  [POST]
    ├── recurring
    │   └── detect  [POST]
    ├── reports
    │   └── {type}  [GET]
    │       └── export  [GET]
    ├── safe-to-spend  [GET]
    ├── sandbox
    │   ├── scenarios  [GET, POST]
    │   │   └── {id}  [DELETE]
    │   └── simulate  [POST]
    ├── templates  [GET, POST]
    │   └── {id}  [PUT]
    │       ├── archive  [POST]
    │       ├── duplicate  [POST]
    │       ├── records  [POST]
    │       └── restore  [POST]
    ├── transactions  [GET, POST]
    │   └── {id}
    │       └── reverse  [POST]
    └── users
        └── me
            ├── avatar  [DELETE, POST]
            ├── delete  [POST]
            ├── demo-data  [POST]
            ├── export  [POST]
            ├── password  [POST]
            ├── profile  [GET, PUT]
            └── sessions  [GET]
                ├── revoke-others  [POST]
                └── {id}  [DELETE]
```

### 10.5 Endpoint tables (by module)
#### Authentication (public sign-in, verification, password reset)  (`AuthController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/auth/csrf` | csrf | public |
| `POST` | `/api/auth/register` | register | public |
| `POST` | `/api/auth/resend-verification` | resend | public |
| `POST` | `/api/auth/verify-email` | verify | public |
| `POST` | `/api/auth/login` | login | public |
| `POST` | `/api/auth/admin/login` | adminLogin | public |
| `POST` | `/api/auth/logout` | logout | public |
| `GET` | `/api/auth/me` | me | signed-in |
| `POST` | `/api/auth/forgot-password` | forgot | public |
| `POST` | `/api/auth/reset-password` | reset | public |

#### Account security (sessions/devices, password change)  (`AccountController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/users/me/sessions` | sessions | signed-in |
| `DELETE` | `/api/users/me/sessions/{id}` | revoke | signed-in |
| `POST` | `/api/users/me/sessions/revoke-others` | revokeOthers | signed-in |
| `POST` | `/api/users/me/password` | changePassword | signed-in |

#### Profile, onboarding, categories, data export, account deletion  (`AccountSettingsController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/users/me/profile` | profile | signed-in |
| `PUT` | `/api/users/me/profile` | update | signed-in |
| `POST` | `/api/users/me/export` | export | signed-in |
| `POST` | `/api/users/me/delete` | delete | signed-in |
| `GET` | `/api/onboarding` | onboardingState | signed-in |
| `PUT` | `/api/onboarding/draft` | draft | signed-in |
| `POST` | `/api/onboarding/complete` | complete | signed-in |
| `GET` | `/api/categories/manage` | manage | signed-in |
| `POST` | `/api/categories` | createCategory | signed-in |
| `PUT` | `/api/categories/{id}` | updateCategory | signed-in |
| `POST` | `/api/categories/{id}/archive` | archive | signed-in |
| `POST` | `/api/categories/{id}/restore` | restore | signed-in |
| `PUT` | `/api/categories/reorder` | reorder | signed-in |

#### Admin console  (`AdminController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/admin/dashboard` | dashboard | ADMIN |
| `GET` | `/api/admin/users` | users | ADMIN |
| `POST` | `/api/admin/users/{id}/suspend` | suspend | ADMIN |
| `POST` | `/api/admin/users/{id}/reactivate` | reactivate | ADMIN |
| `GET` | `/api/admin/payments` | payments | ADMIN |
| `GET` | `/api/admin/payments/{id}` | payment | ADMIN |
| `POST` | `/api/admin/payments/{id}/refund` | refund | ADMIN |
| `POST` | `/api/admin/payments/{id}/dispute/reject` | reject | ADMIN |
| `GET` | `/api/admin/audit` | audit | ADMIN |
| `GET` | `/api/admin/timeline` | timeline | ADMIN |
| `GET` | `/api/admin/oversight/categories` | categories | ADMIN |
| `POST` | `/api/admin/oversight/categories` | createCategory | ADMIN |
| `PUT` | `/api/admin/oversight/categories/{id}` | renameCategory | ADMIN |
| `POST` | `/api/admin/oversight/categories/{id}/archive` | archive | ADMIN |
| `POST` | `/api/admin/oversight/categories/{id}/restore` | restore | ADMIN |
| `POST` | `/api/admin/oversight/investigations` | openInvestigation | ADMIN |
| `GET` | `/api/admin/oversight/investigations/{id}` | investigation | ADMIN |
| `GET` | `/api/admin/announcements` | announcements | ADMIN |
| `POST` | `/api/admin/announcements` | createAnnouncement | ADMIN |
| `PUT` | `/api/admin/announcements/{id}` | editAnnouncement | ADMIN |
| `POST` | `/api/admin/announcements/{id}/publish` | publish | ADMIN |
| `DELETE` | `/api/admin/announcements/{id}` | deleteAnnouncement | ADMIN |
| `GET` | `/api/admin/reports/{type}` | report | ADMIN |
| `GET` | `/api/admin/reports/{type}/export` | export | ADMIN |
| `GET` | `/api/admin/settings` | settings | ADMIN |
| `PUT` | `/api/admin/settings/{key}` | updateSetting | ADMIN |

#### Money automation rules  (`AutomationController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/automation/rules` | rules | USER |
| `POST` | `/api/automation/rules` | create | USER |
| `PUT` | `/api/automation/rules/{id}` | update | USER |
| `POST` | `/api/automation/rules/{id}/pause` | pause | USER |
| `POST` | `/api/automation/rules/{id}/resume` | resume | USER |
| `POST` | `/api/automation/rules/{id}/archive` | archive | USER |
| `POST` | `/api/automation/rules/{id}/preview` | preview | USER |
| `POST` | `/api/automation/rules/{id}/run` | run | USER |
| `GET` | `/api/automation/runs` | runs | USER |
| `POST` | `/api/automation/runs/{id}/retry` | retry | USER |
| `POST` | `/api/automation/allocations/{id}/release` | release | USER |

#### Demo data  (`DemoController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `POST` | `/api/users/me/demo-data` | load | USER |

#### Custom documents  (`DocumentController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/documents/templates` | list | USER |
| `POST` | `/api/documents/templates` | create | USER |
| `PUT` | `/api/documents/templates/{id}` | update | USER |
| `POST` | `/api/documents/templates/{id}/duplicate` | duplicate | USER |
| `POST` | `/api/documents/templates/{id}/archive` | archive | USER |
| `POST` | `/api/documents/templates/{id}/restore` | restore | USER |
| `POST` | `/api/documents/templates/{id}/logo` | logo | USER |
| `DELETE` | `/api/documents/templates/{id}/logo` | removeLogo | USER |
| `POST` | `/api/documents/templates/{id}/preview` | preview | USER |
| `POST` | `/api/documents/templates/{id}/pdf` | pdf | USER |

#### Receipts, memos, avatar  (`FilesController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `POST` | `/api/users/me/avatar` | avatar | signed-in |
| `DELETE` | `/api/users/me/avatar` | removeAvatar | signed-in |

#### Accounts, transactions, dashboard, categories  (`FinanceController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/accounts` | listAccounts | USER |
| `POST` | `/api/accounts` | createAccount | USER |
| `DELETE` | `/api/accounts/{id}` | archiveAccount | USER |
| `GET` | `/api/categories` | listCategories | USER |
| `GET` | `/api/transactions` | listTransactions | USER |
| `POST` | `/api/transactions` | post | USER |
| `POST` | `/api/transactions/{id}/reverse` | reverse | USER |
| `GET` | `/api/dashboard` | dashboard | USER |

#### Financial audit trail  (`FinancialAuditController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/audit/financial` | list | USER |
| `GET` | `/api/audit/financial/types` | types | USER |

#### Shared households  (`HouseholdController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/households` | mine | USER |
| `POST` | `/api/households` | create | USER |
| `GET` | `/api/households/invitations` | incoming | USER |
| `POST` | `/api/households/invitations/{id}/accept` | accept | USER |
| `POST` | `/api/households/invitations/{id}/decline` | decline | USER |
| `GET` | `/api/households/{id}` | detail | USER |
| `POST` | `/api/households/{id}/archive` | archive | USER |
| `POST` | `/api/households/{id}/invitations` | invite | USER |
| `DELETE` | `/api/households/{id}/invitations/{inv}` | cancel | USER |
| `DELETE` | `/api/households/{id}/members/{userId}` | remove | USER |
| `GET` | `/api/households/{id}/expenses` | expenses | USER |
| `POST` | `/api/households/{id}/expenses` | add | USER |
| `DELETE` | `/api/households/{id}/expenses/{eid}` | deleteExpense | USER |
| `POST` | `/api/households/{id}/settlements` | settle | USER |
| `POST` | `/api/households/{id}/budgets` | budget | USER |
| `DELETE` | `/api/households/{id}/budgets/{bid}` | archiveBudget | USER |

#### Notifications & preferences  (`NotificationController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/notifications` | list | signed-in |
| `GET` | `/api/notifications/unread-count` | unread | signed-in |
| `POST` | `/api/notifications/{id}/read` | read | signed-in |
| `POST` | `/api/notifications/read-all` | readAll | signed-in |
| `GET` | `/api/notifications/preferences` | prefs | signed-in |
| `PUT` | `/api/notifications/preferences` | savePrefs | signed-in |

#### Payment simulator  (`PaymentController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/payments/profile` | profile | USER |
| `PUT` | `/api/payments/profile/receive-account` | receive | USER |
| `GET` | `/api/payments/lookup/{handle}` | lookup | USER |
| `GET` | `/api/payments` | list | USER |
| `GET` | `/api/payments/{id}` | get | USER |
| `POST` | `/api/payments` | initiate | USER |
| `POST` | `/api/payments/{id}/confirm` | confirm | USER |
| `POST` | `/api/payments/{id}/cancel` | cancel | USER |
| `POST` | `/api/payments/{id}/refund` | refund | USER |
| `POST` | `/api/payments/{id}/dispute` | dispute | USER |
| `DELETE` | `/api/payments/{id}/dispute` | withdraw | USER |
| `GET` | `/api/payments/{id}/receipt` | receipt | USER |
| `POST` | `/api/payments/requests` | createRequest | USER |
| `GET` | `/api/payments/requests` | requests | USER |
| `GET` | `/api/payments/requests/by-reference/{ref}` | byRef | USER |
| `DELETE` | `/api/payments/requests/{id}` | cancelRequest | USER |
| `GET` | `/api/payments/requests/{ref}/qr` | qr | USER |

#### Budgets, bills & subscriptions, savings goals  (`PlanningController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/budgets` | budgets | USER |
| `POST` | `/api/budgets` | createBudget | USER |
| `DELETE` | `/api/budgets/{id}` | archiveBudget | USER |
| `GET` | `/api/bills` | bills | USER |
| `GET` | `/api/bills/upcoming` | upcoming | USER |
| `POST` | `/api/bills` | createBill | USER |
| `PUT` | `/api/bills/{id}` | updateBill | USER |
| `PATCH` | `/api/bills/{id}/status` | billStatus | USER |
| `POST` | `/api/bills/{id}/pay` | payBill | USER |
| `DELETE` | `/api/bills/{id}` | archiveBill | USER |
| `GET` | `/api/goals` | goals | USER |
| `POST` | `/api/goals` | createGoal | USER |
| `POST` | `/api/goals/{id}/allocations` | allocate | USER |
| `POST` | `/api/goals/{id}/transfers` | transfer | USER |
| `DELETE` | `/api/goals/{id}` | archiveGoal | USER |

#### Statement import & reconciliation, recurring charges  (`ReconciliationController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `POST` | `/api/csv/preview` | preview | USER |
| `POST` | `/api/reconciliation/imports` | create | USER |
| `GET` | `/api/reconciliation/imports` | imports | USER |
| `GET` | `/api/reconciliation/imports/{id}` | rows | USER |
| `DELETE` | `/api/reconciliation/imports/{id}` | discard | USER |
| `POST` | `/api/reconciliation/rows/{id}/confirm` | confirm | USER |
| `POST` | `/api/reconciliation/rows/{id}/reject` | reject | USER |
| `POST` | `/api/reconciliation/rows/{id}/undo` | undo | USER |
| `POST` | `/api/reconciliation/rows/{id}/record` | record | USER |
| `POST` | `/api/recurring/detect` | detect | USER |

#### Reports, exports, calendar, pipeline, safe-to-spend, dashboard layout  (`ReportController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/reports/{type}` | report | USER |
| `GET` | `/api/reports/{type}/export` | export | USER |
| `GET` | `/api/pipeline` | pipeline | USER |
| `GET` | `/api/safe-to-spend` | safeToSpend | USER |
| `GET` | `/api/dashboard-layout` | layout | USER |
| `PUT` | `/api/dashboard-layout` | saveLayout | USER |
| `DELETE` | `/api/dashboard-layout` | resetLayout | USER |
| `GET` | `/api/calendar` | calendar | USER |

#### Planning sandbox  (`SandboxController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `POST` | `/api/sandbox/simulate` | simulate | USER |
| `GET` | `/api/sandbox/scenarios` | scenarios | USER |
| `POST` | `/api/sandbox/scenarios` | save | USER |
| `DELETE` | `/api/sandbox/scenarios/{id}` | delete | USER |

#### Finance templates  (`TemplateController`)

| Method | Path | Handler | Access |
|---|---|---|---|
| `GET` | `/api/templates` | list | USER |
| `POST` | `/api/templates` | create | USER |
| `PUT` | `/api/templates/{id}` | update | USER |
| `POST` | `/api/templates/{id}/duplicate` | duplicate | USER |
| `POST` | `/api/templates/{id}/archive` | archive | USER |
| `POST` | `/api/templates/{id}/restore` | restore | USER |
| `POST` | `/api/templates/{id}/records` | createRecord | USER |

**Also exposed:** `GET /actuator/health[/liveness|/readiness]` (public) · `WS /ws` (cookie-authenticated, see §11) · `/v3/api-docs`, `/swagger-ui.html` (dev only, admin session).

---

## 11. Real-time (WebSocket)

- **Endpoint:** `/ws` (same cookie session; the `Origin` must be in `FLOWPILOT_CORS_ALLOWED_ORIGINS`). Max 6 connections per user.
- **Direction:** server → client **hints only**: `{"type":"refresh","topic":"payments"}`. Clients then re-fetch through the normal REST API, so permissions are always re-checked. Nothing sensitive travels on the socket.
- **Topics → data refreshed:** `notifications` (bell, unread) · `payments` (payments, accounts, transactions, dashboard) · `support` (tickets, chat) · `timeline` (admin dashboard + timeline).
- **Resilience:** automatic reconnect with back-off; full reload of affected data after a reconnect; sockets of revoked sessions are closed every minute; if the socket cannot be used, polling keeps the UI correct.

---

## 12. Background jobs
| Job | Class.method | Schedule | What it does |
|---|---|---|---|
| Nightly retention clean-up | `MaintenanceJobs.purgeExpired` | `cron 0 15 3 * * *, UTC` | Deletes used/expired email tokens (>7 days) and dead sessions (>30 days). |
| Live sockets | `LiveUpdateHandler.closeRevokedConnections` | `fixedDelay = 60_000` | Closes WebSockets whose session was revoked or expired. |
| Reminders | `ReminderJobs.run` | `cron 0 */15 * * * *, UTC` | Creates bill-due and budget-threshold notifications (deduplicated). |
| Automation schedules | `AutomationController.daily` | `cron 0 30 2 * * *, UTC` | Runs scheduled money-automation rules (idempotent per rule + day). |
| Payment expiry | `PaymentJobs.expire` | `fixedDelay = 60_000` | Expires pending simulated payments and open requests past their deadline. |

All jobs are idempotent (safe if two instances run at once). Schedules are UTC and missed runs are **not** back-filled.

---

## 13. Frontend

### 13.1 Routes
| URL | Component |
|---|---|
| `/` | LandingPage |
| `/user/register` | RegisterPage |
| `/user/check-email` | CheckEmailPage |
| `/user/verify-email` | VerifyEmailPage |
| `/user/login` | LoginPage |
| `/user/forgot-password` | ForgotPasswordPage |
| `/user/reset-password` | ResetPasswordPage |
| `/admin/login` | AdminLoginPage |
| `/legal/:doc` | LegalPage |
| `/onboarding` | OnboardingPage |
| `/app` | redirects to `/app/dashboard` |
| `/app/dashboard` | DashboardPage |
| `/app/accounts` | AccountsPage |
| `/app/transactions` | TransactionsPage |
| `/app/budgets` | BudgetsPage |
| `/app/bills` | BillsPage |
| `/app/savings` | GoalsPage |
| `/app/households` | HouseholdsPage |
| `/app/documents` | DocumentsPage |
| `/app/audit` | AuditTrailPage |
| `/app/support` | SupportPage |
| `/app/reconcile` | ReconcilePage |
| `/app/recurring` | RecurringPage |
| `/app/sandbox` | SandboxPage |
| `/app/automation` | AutomationPage |
| `/app/payments` | PaymentsPage |
| `/app/templates` | TemplatesPage |
| `/app/receipts` | ReceiptsPage |
| `/app/calendar` | CalendarPage |
| `/app/reports` | ReportsPage |
| `/app/notifications` | NotificationsPage |
| `/app/settings` | SettingsPage |
| `/admin` | AdminHome |
| `/admin/users` | AdminUsersPage |
| `/admin/payments` | AdminPaymentsPage |
| `/admin/support` | AdminSupportPage |
| `/admin/audit` | AdminAuditPage |
| `/admin/timeline` | AdminAuditPagetimeline |
| `/admin/oversight` | AdminOversightPage |
| `/admin/announcements` | AdminAnnouncementsPage |
| `/admin/reports` | AdminReportsPage |
| `/admin/config` | AdminConfigPage |
| `/admin/settings` | AdminSettingsPage |
| `/admin/notifications` | NotificationsPage |
| `/admin/*` | NotFoundPage |

Route guards: anonymous visitors are redirected to the matching login; `USER` and `ADMIN` can't open each other's area; users who have not finished onboarding are sent to `/onboarding`.

### 13.2 How it is organised
| Folder | Contents |
|---|---|
| `src/lib/api.ts` | Axios client: credentials, CSRF token (fetched once, serialised so parallel requests share it), one automatic retry on `CSRF_INVALID`, uniform error mapping |
| `src/lib/live.ts` | WebSocket hook mapping hints → React Query invalidation |
| `src/lib/money.ts`, `validation.ts`, `templates.ts` | money formatting, Zod schemas that mirror server rules, template field rules |
| `src/auth/` | `AuthContext` (current user) and `ProtectedRoute` |
| `src/theme/` | `ThemeProvider` (dark/light/system, no flash) |
| `src/components/` | design-system primitives, charts, pipeline, chat panel, uploaders |
| `src/landing/` | landing page, curtain, 3D scenes with CSS fallback |
| `src/pages/user`, `pages/admin` | screens |

### 13.3 UX and accessibility
Dark and Light themes from CSS variables · keyboard-reachable controls and visible focus · labelled inputs and `aria-live` status regions · charts have text alternatives · motion respects `prefers-reduced-motion` · no information conveyed by colour alone.

---

## 14. Security model

| Area | What is implemented |
|---|---|
| **Passwords** | Argon2id; minimum 12 characters with 3 character classes; rejects passwords containing the email; constant-time dummy hashing for unknown emails |
| **Sessions** | Opaque 256-bit random token in an HttpOnly cookie; only its SHA-256 is stored (`auth_sessions`); idle timeout (`PT2H`) and absolute timeout (`P7D`); revoke one/all devices; all sessions revoked on password reset/change and on suspension; new-device alert |
| **Cookies** | `fp_session` and `fp_csrf`: `Secure` in production (enforced), configurable `SameSite` |
| **CSRF** | Double-submit token (header + cookie) on every non-GET; uploads also send `_csrf` |
| **CORS / WebSocket origin** | Explicit origin list only; wildcard refused in prod |
| **Email tokens** | 256-bit, hashed, single-use, expiring (verify: 24 h; reset: short-lived); removed from the browser URL after reading |
| **Account enumeration** | Registration, resend, forgot-password return the same generic answer; login errors are generic |
| **Brute force** | Per-IP and per-email rate limits + 15-minute lockout after 5 failures; every attempt audited |
| **Authorization** | URL rules + `@PreAuthorize` per endpoint; every data read is scoped by owner in SQL (other users' IDs return 404) |
| **Injection** | Parameterised SQL everywhere (the few dynamic fragments are fixed constants); input validated server-side with Bean Validation |
| **XSS** | React escaping, no `dangerouslySetInnerHTML`; API CSP `default-src 'none'`; strict CSP on the SPA (Vercel); `nosniff`, `frame-ancestors 'none'`, `no-referrer` |
| **File uploads** | Type decided from **content** (magic bytes), size and image-dimension limits, PDFs with scripts/embedded files rejected, server-generated storage keys, private files streamed through the API after an ownership check with a sandbox CSP |
| **Exports** | CSV formulas neutralised; PDFs generated server-side |
| **Audit** | Append-only security and financial trails; admin reads of sensitive data are themselves audited |
| **Admin safety** | Reason required for suspend/refund/reject; investigations expire in 24 h and show counts only; runtime settings are whitelisted and validated; security settings are environment-only |
| **Secrets in logs** | `SecretLogFilter` + log levels; no secret is ever logged by the application |
| **Production guard** | Refuses to boot with unsafe configuration |
| **Privacy** | Full JSON export; account deletion de-identifies the user while keeping the ledger for financial integrity; retention job purges expired tokens and old sessions |

**Not implemented (yet):** two-factor authentication, email-address change, OAuth sign-in. Independent security review and dependency scanning before real-world use are strongly recommended.

---

## 15. Testing & quality

| Layer | Where | Count | Run |
|---|---|---|---|
| Backend unit tests | `backend/src/test/**/*Test.java` | 80 | `mvn test` |
| Backend integration (real PostgreSQL via Testcontainers) | `backend/src/test/java/com/flowpilot/it/PlatformIT.java` | 7 | `mvn verify` (Docker required) |
| Frontend unit/component | `frontend/src/__tests__` | 37 | `npm test` |
| Browser smoke (Playwright) | `frontend/e2e` | 5 | `npm run e2e` |

*(Counts are the number of test methods at the time of writing.)*

**What is verified:** ledger invariants and immutability, overdraft refusal, idempotency, payment atomicity/conservation/refund-once, tenant isolation (support, households), audit immutability, password policy, token handling, rate limiter, CSV parsing/mapping, recurring detection, statement matching, split & settle-up maths, bill maths, sandbox and automation engines, file validation (magic bytes, active PDF content), Cloudinary signature, CSV-injection guard, settings validators, theme persistence, cookie consent, route guards, registration validation, template field rules, landing page structure.

**CI (`.github/workflows/ci.yml`):** backend build + tests, frontend type-check + tests + build, e2e smoke, `npm audit`, secret scan (gitleaks) and dependency check.

**Not covered:** authenticated browser journeys, HTTP-level security matrix tests, automated accessibility/Lighthouse budgets, load tests.

---

## 16. Production deployment

Topology: **Vercel** (SPA) → **Render** (API, Docker runtime) → **Neon** (PostgreSQL), plus **Brevo** and **Cloudinary**. Render has no native Java runtime, so it builds `backend/Dockerfile` on its own servers: you do **not** need Docker installed.

### 16.1 Decide how the two sites talk (controls login/session behaviour)
| Mode | URLs | `FLOWPILOT_COOKIE_SAMESITE` | Notes |
|---|---|---|---|
| **A. Own domain (recommended)** | `app.yourdomain.com` (Vercel) + `api.yourdomain.com` (Render) | `Lax` | Same "site": sessions and live chat work in every browser |
| **B. Default URLs** | `*.vercel.app` + `*.onrender.com` | `None` | Cross-site cookies. Works in Chrome/Edge; **Safari/Firefox/incognito may block them** (symptom: `CSRF rejected … csrfCookiePresent=false` in the API log, or login that "forgets" you) |

### 16.2 Neon (database)
1. Create a Neon project/database. Copy the connection string:
   `postgresql://USER:PASSWORD@ep-xxxx-pooler.<region>.aws.neon.tech/neondb?sslmode=require&channel_binding=require`
2. Convert it into three Java values:
   - `DATABASE_URL=jdbc:postgresql://ep-xxxx.<region>.aws.neon.tech/neondb?sslmode=require`  
     (remove `USER:PASSWORD@`, **remove `-pooler`** from the host so Flyway works, **delete `&channel_binding=require`**)
   - `DATABASE_USERNAME=USER` (just the name, without any `KEY=` prefix)
   - `DATABASE_PASSWORD=PASSWORD`
3. Nothing else: Flyway creates every table on first start. Never commit these values; never put them in `application-*.yml`.

### 16.3 GitHub
```bash
cd frontend && npm install            # creates/updates package-lock.json (commit it)
cd .. && git add -A && git commit -m "release" && git push
```
Confirm `.env` is **not** tracked (`git status`, `.gitignore` covers it).

### 16.4 Render (backend)
1. **New → Blueprint** and pick the repository (uses `render.yaml`), or **New → Web Service → Language: Docker**, Dockerfile `./backend/Dockerfile`, Docker context `./backend`.
2. Set environment variables (Render → Environment):

| Variable | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | from §16.2 |
| `DB_POOL_SIZE` | `5` |
| `FLOWPILOT_PUBLIC_BASE_URL` | frontend URL, `https://…`, no trailing slash |
| `FLOWPILOT_CORS_ALLOWED_ORIGINS` | exactly the same frontend URL |
| `FLOWPILOT_COOKIE_SECURE` | `true` |
| `FLOWPILOT_COOKIE_SAMESITE` | `Lax` (mode A) or `None` (mode B) |
| `BREVO_API_KEY`, `BREVO_SENDER_EMAIL`, `BREVO_SENDER_NAME` | Brevo values (sender must be verified) |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | Cloudinary dashboard |
| `FLOWPILOT_ADMIN_BOOTSTRAP_EMAIL`, `FLOWPILOT_ADMIN_BOOTSTRAP_PASSWORD` | first admin (remove after first start) |

3. Health check path: `/actuator/health/readiness`. Auto-deploy on push is enabled in `render.yaml`.
4. First boot takes 1–3 minutes. Copy the service URL (`https://<service>.onrender.com`).
5. **Plans:** *free* = 512 MB RAM and sleeps when idle (cold start ≈ 1 min). Use a paid plan for always-on.

### 16.5 Vercel (frontend)
1. **Add New → Project**, import the repo, **Root Directory = `frontend`**, framework Vite, build `npm run build`, output `dist`.
2. Environment variable (Production): `VITE_API_BASE_URL=https://<api-host>/api` (must end with `/api`).
3. Deploy, copy the URL, then set `FLOWPILOT_PUBLIC_BASE_URL` and `FLOWPILOT_CORS_ALLOWED_ORIGINS` on Render to it and redeploy the API.
4. `frontend/vercel.json` provides the SPA rewrite and the security headers, including the CSP. Its `connect-src` allows `https://*.onrender.com` and `wss://*.onrender.com`; **if you use a custom API domain, add `https://api.yourdomain.com wss://api.yourdomain.com` there.**
5. Note: the build script is `vite build`; run `npm run build:check` locally to see TypeScript errors.

### 16.6 Brevo (email)
Brevo → *Senders, Domains & dedicated IPs* → add and **verify** the sender address (ideally authenticate your domain with SPF/DKIM). New accounts may need activation before transactional sending. If sending fails the API log shows `Brevo returned HTTP <code>: <reason>`.

### 16.7 Cloudinary (files)
Copy cloud name, API key and secret. Private files (receipts, support attachments) are stored as private `authenticated` assets and only streamed by the API after an ownership check; avatars are the only public images. If an upload fails, the message shown includes Cloudinary's reason (for example *Invalid Signature* = wrong secret).

### 16.8 First administrator
Start the API with `FLOWPILOT_ADMIN_BOOTSTRAP_EMAIL` (a real-looking address) and `FLOWPILOT_ADMIN_BOOTSTRAP_PASSWORD` → sign in at `https://<frontend>/admin/login` → **delete both variables** in Render and redeploy. Lost access or typo in the email? Set `FLOWPILOT_ADMIN_BOOTSTRAP_RESET=true` with the corrected values for **one** start, then remove all three.

### 16.9 Post-deploy smoke test (≈5 minutes)
Register → email arrives → click link → log in → add account → add transaction → upload avatar → create a support ticket → admin replies (appears live) → export data → log out everywhere. Then check the API log for the healthy-boot lines from §8 and for the absence of any database URL.

### 16.10 Release & rollback
- **Release:** merge to `main` through a green CI → Render and Vercel deploy → Flyway applies new migrations on start → run the smoke test → tag.
- **Frontend rollback:** promote the previous Vercel deployment.
- **Backend rollback:** redeploy the previous Render release. Migrations are **forward-only**: ship a compensating migration instead of editing applied ones, and make schema changes additive first so the previous app version still works.
- **Data incident:** restore Neon to a point in time into a new branch, verify, then repoint `DATABASE_URL`.

### 16.11 Production checklist
- [ ] CI green, `package-lock.json` committed, no `.env` in git
- [ ] Every secret that has ever appeared in a screenshot/chat has been **rotated**
- [ ] Neon uses the direct host; `channel_binding` removed
- [ ] Render env complete; API boots with `prod` profile and no config error
- [ ] CORS/public URL equal the real frontend origin; cookie mode chosen and tested in Chrome **and** Safari/Firefox
- [ ] Brevo sender verified; test email delivered
- [ ] Cloudinary upload works (avatar + receipt)
- [ ] Admin bootstrap variables removed after first start
- [ ] Legal pages replaced with reviewed text
- [ ] Backups understood (Neon PITR; Cloudinary assets are not part of it)

---

## 17. Operations & maintenance

| Topic | How |
|---|---|
| **Logs** | Local: readable text. Production: JSON (ECS) with a `requestId` per request. Secrets/DB URLs are filtered out. |
| **Health** | `/actuator/health/readiness` (Render uses it); admin dashboard shows DB latency, live sockets, heap, uptime, stale background work. |
| **Runtime settings** | Admin → Configuration: supported currencies, payment max amount and pending TTL, ticket reopen window. Applied within ~30 s, audited. Security settings are environment-only. |
| **Secrets rotation** | Change the value in Render/Neon/Brevo/Cloudinary, redeploy. Rotate immediately if a value was ever shared. |
| **Users in trouble** | Admin → Users (suspend/reactivate), Support queue, Investigations (reasoned, 24 h). |
| **Data retention** | Nightly job removes expired tokens (>7 d) and dead sessions (>30 d). Ledger and audit data are kept. |
| **Backups** | Neon point-in-time restore for PostgreSQL. Cloudinary assets need their own plan. |
| **Scaling** | Stateless API (sessions in the database) so it can scale horizontally; note that rate limits and the cache are per instance, and background jobs are idempotent. |
| **Dependency hygiene** | CI runs `npm audit` and OWASP dependency-check; review monthly. |

---

## 18. Troubleshooting

| Symptom | Cause → fix |
|---|---|
| Register/Login: **"Security token missing or invalid"**; API log `CSRF rejected … csrfCookiePresent=false` | Cross-site cookies are blocked (frontend and API on different "sites"). Use an own domain with `Lax`, or `FLOWPILOT_COOKIE_SAMESITE=None` + `FLOWPILOT_COOKIE_SECURE=true` and a browser that allows third-party cookies. |
| Same message but only on the first click after loading | Stale token race; reload once. (The client now serialises the token fetch and retries once.) |
| Browser console CORS error | `FLOWPILOT_CORS_ALLOWED_ORIGINS` is not **exactly** the frontend origin (scheme, host, no trailing slash). |
| Verification email never arrives; console shows `DEV MODE - email NOT sent` | `BREVO_API_KEY` not visible to the app: `.env` must be in the project root or `backend/`, named exactly `.env`, restart the API; or set the variable in Render. |
| Brevo log `Brevo returned HTTP 4xx` | Sender not verified, key invalid, or account not activated: read the reason printed after the status. |
| Admin login says *Enter a valid email address* | The admin was created with an invalid email. Set `FLOWPILOT_ADMIN_BOOTSTRAP_RESET=true` with a valid email + password for one start. |
| Avatar/receipt upload fails | The message shows Cloudinary's reason; check the three `CLOUDINARY_*` values (cloud name exact). |
| Flyway errors / lock timeouts on Neon | You used the `-pooler` host: use the direct host. |
| `unrecognized configuration parameter "channel_binding"` | Remove `&channel_binding=require` from `DATABASE_URL`. |
| `Invalid production configuration: …` | Set the variable it names (the message never prints values). |
| First request after idle is slow / 502 | Render free instance was asleep; wait ~1 minute or use a paid plan. |
| Vercel build fails with type errors | Production build uses `vite build`; run `npm run build:check` to see them. |
| Live updates not appearing | WebSocket blocked by origin/cookie rules; the app keeps working through polling. Check `wss://` is allowed in the CSP `connect-src`. |

---

## 19. Known limitations & roadmap

- No two-factor authentication, email-address change, or OAuth sign-in.
- Payments are a **simulator** between FlowPilot users; refunds are full only.
- Household expenses can be added and removed but not edited; household investigations by admins are not included.
- Rate limiting and caching are per instance (in memory); background jobs run in UTC and do not back-fill.
- Legal pages are placeholder text requiring review. Accessibility has been designed for but not formally audited.
- Test coverage is strongest on domain logic; authenticated end-to-end journeys and HTTP-level security tests are still to be added.
- On free hosting tiers the API sleeps when idle.

---

## 20. Documentation index

| File | Contents |
|---|---|
| `README.md` | this document |
| `docs/ARCHITECTURE.md` | modules, data flow, real-time and idempotency design |
| `docs/SECURITY.md` | security controls and recommended hardening |
| `docs/DEPLOYMENT.md` | deployment notes (schedulers, WebSockets, Cloudinary, backups) |
| `docs/PRODUCTION_CHECKLIST.md` | go-live checklist, release and rollback |
| `docs/TESTING.md` | test strategy and gaps |
| `docs/STATUS.md` | honest build/verification ledger and assumptions |
| `.env.example`, `frontend/.env.example` | environment templates (placeholders only) |

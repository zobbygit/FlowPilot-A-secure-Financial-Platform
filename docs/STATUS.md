# Build status — read this first

**Every file in this repository was written in an environment that could not compile or run it** (no `javac`, Maven, PostgreSQL,
Docker or package-registry access). Treat it as a complete, carefully reviewed first draft, not a verified release.
What *was* checked here: Java brace/paren balance and missing JDK imports (script), TypeScript syntax/undefined-name/unused-variable
check, cross-file consistency by reading. What was **not**: compilation, type checking against real libraries, Flyway migration
execution, any test, any email, any storage call, any browser rendering.

**First hour on your machine**
1. `cd backend && mvn -B verify` (unit tests + Testcontainers integration tests; needs Docker). Fix compile errors, send them to me.
2. `cd frontend && npm install && npm run typecheck && npm test && npm run build`.
3. Start Postgres (`docker compose up -d db`), run the API with the `dev` profile, run `npm run dev`, click through.

## Version / API assumptions most likely to need a tweak
| Assumption | Why it may break |
|---|---|
| Spring Boot **4.0.7**; starters `spring-boot-starter-webmvc`, `-flyway`, `-testcontainers`; Spring Security 7, Hibernate 7 | Names/packages chosen from research, never resolved |
| `springdoc 3.0.0`, `bcprov 1.82`, `openpdf 2.0.3` (`com.lowagie.*`), `zxing 3.5.3`, OWASP plugin 12.1.3 | Pinned from memory |
| Testcontainers 2.x package `org.testcontainers.postgresql.PostgreSQLContainer` | Package moved in 2.0 |
| Jackson 3 serialises records/`Map`/`Instant` as expected (money is sent as **strings** on purpose) | Date format defaults |
| Cloudinary **private download** endpoint (`/raw/download?public_id=…&type=authenticated`) and signature | Written from docs knowledge; the signing helper is unit-tested against Cloudinary's documented example, the endpoint is not |
| lucide-react icon names (`Home`, `BarChart3`, `FlaskConical`…), `three ^0.180`, `@react-three/fiber ^9.3`, zod 3 + resolvers 5 | Caret ranges, some icons are renamed between releases |
| Neon Object Storage | Could not verify availability → receipts/avatars/evidence use **Cloudinary** (authenticated raw assets for private files, public type only for avatars). Swap `FileStorageService` if you prefer S3-compatible storage |

## What exists (all ⚠️ = written, not executed)
**Foundation & security** — Spring Boot modular monolith, 13 Flyway migrations, Argon2id, opaque server-side sessions (hashed), CSRF, CORS allow-list, security headers, rate limits, lockout, Brevo verification/reset emails, audit tables (append-only triggers), prod config validator, JSON error model, structured logging.

**Landing & shell** — 12-section cinematic landing (curtain reveal, skippable, once per session, reduced-motion aware; React-Three-Fiber hero + scroll-linked "money universe" with lazy loading, pause off-screen, WebGL/context-loss fallback), dark/light themes (no flash), cookie consent, legal pages (draft text).

**User app (sidebar)** — Dashboard (+ Financial Activity Pipeline), Accounts & Wallets, Transactions, Receipts & Memos, Finance Templates (+builder), Budgets, Bills & Subscriptions, Money Automation, Savings & Planning, Payment Simulator (QR, requests, review→confirm, refunds, disputes, PDF confirmation, live updates), Planning Sandbox, Reconcile Statements, Recurring Charges, Financial Calendar, Reports & Exports (CSV/PDF), Shared Households, Custom Documents, Audit Trail, Support (tickets + live chat + attachments), Notifications, Settings (profile, formats, categories, notification prefs, devices/sessions, data export, account deletion), Onboarding wizard.

**Admin console** — Dashboard (health, activity), Users (suspend/reactivate with reason), Payment monitoring (flags, refunds, dispute rejection), Support queue + chat, Audit logs, Activity timeline (live), Oversight (default categories, 24h reasoned investigations), Announcements, Platform reports (CSV/PDF), Configuration (whitelisted settings), Admin profile & settings.

**Tests written** — 87 backend tests (unit + Testcontainers ITs), 42 frontend tests, Playwright smoke suite, CI (build, tests, e2e, npm audit, gitleaks, OWASP), deploy workflow.

## Deliberate decisions you should know about
- **No real money anywhere.** Payments are simulated and labelled; automation only *plans* allocations; goals count real transfers only; documents carry a permanent "personal record, not official" notice.
- Ledger is immutable; corrections are reversals. Account deletion de-identifies and **retains** the ledger.
- Financial audit rows are written in the same DB transaction as the change; security audit rows use their own transaction so failures are still recorded.
- Rate limiting is **per instance** (in-memory). Schedulers run at UTC and do **not** back-fill missed days; uniqueness constraints make repeats safe.
- Cross-site cookies (Vercel domain ≠ Render domain) need `SameSite=None; Secure`; same-site custom domains are strongly preferred.
- Admin accounts have no email-based password reset (by design); the first admin comes from one-time env vars.

## Added in the final pass
Safe-to-spend (formula shown in the UI), customizable dashboard widgets (visibility + order, saved per user), optional demo data (real ledger entries, "(demo)" accounts), caching of admin default categories (Caffeine, evicted on write), OpenAPI/Swagger UI config (admins only), and `PRODUCTION_CHECKLIST.md` with release + rollback steps.

## Known gaps / not built
- 2FA, email-address change, OAuth sign-in.
- Authenticated browser E2E journeys, HTTP-level security tests, axe/Lighthouse automation, load tests (see `TESTING.md`).
- Household *investigation* oversight (admin summary covers personal data only), household expense editing (add/remove only), partial refunds.
- Real-time typing indicators in chat; push/email notifications beyond verification & reset emails.
- Legal text is placeholder and needs legal review. Marketing claims (user counts, ratings) were intentionally **not** invented.
- Admin UI e2e, Playwright for mobile viewport beyond smoke.

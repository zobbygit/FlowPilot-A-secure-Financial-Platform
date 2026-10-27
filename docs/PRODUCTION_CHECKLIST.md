# Production readiness & release checklist

## Before the first deploy (all must be ✅)
- [ ] `mvn verify` green (unit + Testcontainers) and `npm run typecheck && npm test && npm run build` green; CI green on `main`.
- [ ] Neon database created; `DATABASE_URL` uses `sslmode=require`; a **separate least-privilege DB user** for the app (no TRUNCATE/ALTER/DROP; migrations run with the owner role or a one-off job).
- [ ] Brevo: sender/domain verified (SPF + DKIM + DMARC), API key stored only in Render; send a real verification email to a mailbox you control.
- [ ] Cloudinary credentials set (backend only); upload a receipt, view it, delete it.
- [ ] `FLOWPILOT_COOKIE_SECURE=true`; `FLOWPILOT_CORS_ALLOWED_ORIGINS` = exact https frontend origin; `SameSite=Lax` on one registrable domain (or `None` if cross-site).
- [ ] `vercel.json` CSP `connect-src` lists the real API host (https + wss); security headers visible in browser dev tools.
- [ ] First admin created via bootstrap variables, **variables then removed**; sign-in at `/admin/login` works; a normal user cannot open `/admin`.
- [ ] `prod` profile starts (it refuses unsafe config); `/actuator/health/readiness` is green on Render.
- [ ] Gitleaks clean; `npm audit` and OWASP dependency-check reviewed.
- [ ] Legal pages replaced with reviewed text; cookie banner behaves; consent respected.
- [ ] Backups: Neon PITR enabled; decide how Cloudinary assets are retained.

## Smoke test after every deploy
Register → verify email → log in → add account → record income + expense → dashboard totals match → create bill/budget/goal → simulated payment between two users → support ticket + admin reply (live) → export data → log out everywhere.

## Release process
1. Merge to `main` only through a PR with green CI. 2. CI triggers the Render deploy hook (Vercel deploys the frontend from Git). 3. Flyway applies new migrations on startup. 4. Run the smoke test. 5. Tag the release.

## Rollback
- **Frontend**: promote the previous Vercel deployment.
- **Backend**: redeploy the previous Render release. Migrations are **forward-only**: if a release included one, ship a *compensating* migration instead of editing an applied file. Write migrations so the previous app version still works with the new schema (additive first, destructive later).
- **Data incident**: restore Neon to a point in time into a new branch, verify, then repoint `DATABASE_URL`.

## Operating notes
Schedulers run in UTC and are idempotent. Rate limits are per instance. Watch: DB latency and stale background work on the admin dashboard, failed payment count, Brevo rejections in logs (`The … email was not accepted by the provider`).

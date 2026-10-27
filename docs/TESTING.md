# Testing strategy

| Layer | Where | Runs with | Needs |
|---|---|---|---|
| Backend unit tests (pure logic + Mockito) | `backend/src/test/**/*Test.java` | `mvn test` | JDK 25 |
| Backend integration tests (real PostgreSQL) | `backend/src/test/java/com/flowpilot/it/*IT.java` | `mvn verify` | Docker |
| Frontend unit/component tests | `frontend/src/__tests__` | `npm test` | Node 22 |
| Browser smoke tests | `frontend/e2e` | `npm run e2e` | running stack + Playwright |

## What the tests prove
- **Ledger**: balance == sum of entries; entries immutable (DB trigger); overdraft refused by service *and* by a CHECK constraint; idempotency keys prevent double posting.
- **Payments**: nothing moves before confirmation; confirming twice moves money once; total money conserved; refund once only.
- **Isolation**: a second user gets 404 for another user's ticket or household; audit tables reject UPDATE/DELETE.
- **Pure engines**: password policy, tokens, rate limiter, CSV parser, recurring detector, statement matcher, split calculator, bill math, sandbox projection, automation planner, settings validators, file validator (magic bytes, active PDF content), Cloudinary signature (documented example), CSV-injection-safe export.
- **Frontend**: theme persistence & system preference, cookie consent versioning, route guards, form validation parity with the server, template field rules, finance schemas, accessible chart legend, landing page (≥12 sections, skippable curtain, no fake social links, "sample data" labels).
- **E2E smoke**: landing without WebGL, theme toggle persistence, registration validation, protected-route redirects, legal pages, cookie banner.

## Not covered yet (be honest in reviews)
- Authenticated browser journeys (register → verify → log in → create account → transaction) need a seeded user; add once Brevo is stubbed in CI.
- HTTP-level security tests (CSRF, CORS, 401/403 matrix) with MockMvc/RestTestClient.
- Automated accessibility (axe) and Lighthouse budgets; manual keyboard/screen-reader review is still required.
- Load testing.

**Nothing in this repository had been executed when it was handed over** (see STATUS.md). Run `mvn verify` and `npm test` first and fix whatever surfaces.

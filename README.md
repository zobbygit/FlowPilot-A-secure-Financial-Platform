# FlowPilot

Personal finance command center: Java 25 / Spring Boot modular monolith + React/TypeScript/Tailwind SPA, PostgreSQL (Neon), Brevo email, Cloudinary storage.

> **Read [`docs/STATUS.md`](docs/STATUS.md) first.** Every README section is implemented, but this code was written in an environment with
> no Maven, `javac`, PostgreSQL, Docker or package registry, so **nothing has been compiled or run yet**. Expect to fix compile errors on first build.

## Layout
```
backend/    Spring Boot 4 API  (Flyway migrations in src/main/resources/db/migration)
frontend/   Vite + React 19 + TS + Tailwind 3, Jest + React Testing Library
docs/       ARCHITECTURE.md, SECURITY.md, STATUS.md, DEPLOYMENT.md, TESTING.md
.github/    CI (build, tests, audits, secret scan) and deploy workflows
```

## Run locally
Prereqs: JDK 25, Maven 3.9+, Node 22+, Docker (for Postgres).
```bash
docker compose up -d db                       # PostgreSQL 17
cd backend && SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
# in another terminal
cd frontend && cp .env.example .env && npm install && npm run dev
```
With `BREVO_ENABLED=false` (default in dev) no email is sent: the verification link is printed in the backend log
under a "DEV MODE - email NOT sent" warning. Set `BREVO_ENABLED=true` + `BREVO_API_KEY` + `BREVO_SENDER_EMAIL` to use Brevo.

First admin: set `FLOWPILOT_ADMIN_BOOTSTRAP_EMAIL` and `FLOWPILOT_ADMIN_BOOTSTRAP_PASSWORD` (policy-compliant), start once, then remove them.
Admin sign-in is at `/admin/login`; users at `/user/login`.

## Tests
```bash
cd backend && mvn verify          # unit tests + Testcontainers integration tests (Docker required)
cd frontend && npm test           # Jest + React Testing Library
cd frontend && npm run e2e        # Playwright smoke tests (stack must be running)
```
See `docs/DEPLOYMENT.md` for Neon, Render, Vercel, Brevo and GitHub Actions setup.

## Environment variables
See `.env.example` (backend) and `frontend/.env.example`. Required in production: `DATABASE_*`, `FLOWPILOT_PUBLIC_BASE_URL`, `FLOWPILOT_CORS_ALLOWED_ORIGINS`,
`FLOWPILOT_COOKIE_SECURE=true`, `BREVO_*`, `CLOUDINARY_*`. The `prod` profile refuses to start if any is unsafe or missing.

## Roles & entry points
`/user/register`, `/user/login` → user app at `/app/*`. `/admin/login` → admin console at `/admin/*` (first admin via one-time bootstrap variables).

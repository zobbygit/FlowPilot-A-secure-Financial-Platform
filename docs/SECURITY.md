# Security notes
- Passwords: Argon2id (Spring Security defaults), policy enforced server-side (12–128 chars, 3 of 4 classes, no email name).
- Tokens (email/session): 256-bit `SecureRandom`, hash-only storage, single use (atomic `UPDATE … WHERE consumed_at IS NULL`), 24 h verify / 30 min reset.
- No account enumeration on register/resend/forgot/login; unknown-user logins burn equal Argon2 time.
- Lockout: 5 failures → 15 min; plus per-IP and per-email rate limits (429 + Retry-After).
- Secrets only via environment; `ProductionConfigValidator` refuses unsafe prod config and never prints values.
- Audit table is append-only (trigger). Never log tokens, passwords, cookies or API keys.
- Client IP comes only from Tomcat's RemoteIpValve for trusted proxies; set `FLOWPILOT_TRUSTED_PROXY_REGEX` for your host.
- Frontend: CSP via `vercel.json`; no tokens in localStorage (only theme and consent flags); source maps disabled.
- Pending: Testcontainers authorization tests, dependency-scan baseline, penetration review.

## Additional controls
- Uploads: type decided from content (magic bytes), size re-checked, image pixel cap, PDFs with scripts/embedded files rejected, server-generated object keys, ownership checked on every read, `nosniff` + sandbox CSP on served files.
- CSV exports neutralise spreadsheet formulas; CSV imports are size/row/column limited and never executed.
- Admin: every `/api/admin/**` call needs ROLE_ADMIN (filter chain + method security); mutations need a written reason where they affect users or money; privileged reads (audit log, payment detail, investigations) are themselves audited; investigations expire after 24 h and expose aggregates only.
- WebSocket: cookie-authenticated handshake, explicit Origin allow-list, server-to-client hints only, 256-byte inbound cap.
- Post-commit side effects (email, notifications, audit pushes) can never turn a committed success into an error response.
- Still recommended before launch: independent security review, DB role separation (app user without TRUNCATE/ALTER), dependency-scan baseline, HTTP-level authorization tests.

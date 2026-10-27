# Deployment
1. **Neon**: create a project/database; copy the JDBC URL (`sslmode=require`), user, password into Render env.
2. **Brevo**: create an API key; verify a sender address/domain (SPF/DKIM) — unverified senders are rejected. Set `BREVO_*`.
3. **Render** (backend): New → Blueprint from `render.yaml`; fill every `sync:false` variable. Health check `/actuator/health/readiness`.
   Flyway runs migrations at startup. Set `FLOWPILOT_PUBLIC_BASE_URL` and `FLOWPILOT_CORS_ALLOWED_ORIGINS` to the exact https Vercel origin.
   Prefer one registrable domain (`app.example.com` + `api.example.com`) with `FLOWPILOT_COOKIE_SAMESITE=Lax`; otherwise `None`.
4. **Vercel** (frontend): root `frontend/`, env `VITE_API_BASE_URL=https://<api-host>/api`; edit `vercel.json` CSP `connect-src` host.
5. **GitHub**: secrets `RENDER_DEPLOY_HOOK_URL`, `NVD_API_KEY`. Disable Render auto-deploy if you want CI-gated deploys only.
6. **First admin**: set bootstrap vars once, deploy, sign in at `/admin/login`, then delete the vars.
7. Rollback: redeploy the previous Render release; migrations are forward-only, so write compensating migrations rather than editing applied ones.

## Additional setup notes
- **Cloudinary**: create a product environment; set `CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET` on the backend only. Private files are stored as `authenticated` raw assets and streamed through the API after an ownership check. Avatars are the only public assets.
- **WebSockets on Render**: the API serves `/ws`; the frontend derives `wss://<api-host>/ws` from `VITE_API_BASE_URL`. Allowed origins come from `FLOWPILOT_CORS_ALLOWED_ORIGINS`. Add the `wss://` host to the CSP `connect-src` in `frontend/vercel.json`.
- **Schedulers** (UTC): reminders every 15 min, payment/request expiry every minute, retention purge 03:15, automation schedules 02:30. They are idempotent, so multiple instances are safe but give no extra throughput.
- **Platform settings** (currencies, payment limits, ticket reopen window) are editable by admins at runtime; security-critical settings stay in the environment.
- **Backups**: Neon point-in-time restore covers the database; Cloudinary assets are not part of it — export or replicate them if retention matters.

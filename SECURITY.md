# Security Notes

How O-RAN Network Defender meets the brief's security requirements, plus the known gaps.
Auth is intentionally lightweight (per the brief), but the model is made explicit below.

## Controls in place

| Requirement (brief) | How it's done | Where |
|---------------------|---------------|-------|
| Input validation on API requests | Bean Validation on request records (`@NotBlank`, `@NotNull`, `@Positive`) checked via `@Valid` on controllers | `*Controller` request records |
| Server-side validation of player actions | `submitAction` verifies the player is in the session, the incident **belongs to that player**, the session is `ACTIVE`, and the incident is `OPEN` before doing anything | `IncidentService.submitAction` |
| Players cannot modify another player's score | No endpoint sets a score. Score only changes inside `recordScoreEvent`, called server-side from the action outcome; the client submits a chosen action, never a score | `ScoreService.recordScoreEvent` |
| Game state changes via controlled backend logic | The engine decides outcomes; clients can't write cells/incidents. The hidden `rootCause` is **never** serialised to clients (DTOs omit it) | `engine/*`, `IncidentResponse` |
| No secrets in source control | `.env` is gitignored (only `.env.example` placeholders committed); K8s uses a `Secret` (only `secret.yaml.example` placeholders committed) | `.gitignore`, `k8s/secret.yaml.example` |
| Safe handling of config values | DB credentials and the ingest token come from env / K8s `Secret`, never hardcoded | `application.yml`, `k8s/*` |
| Protection against injection | All DB access is Spring Data JPA / parameterised queries — no string-built SQL | repositories |
| Error messages don't leak internals | `GlobalExceptionHandler` returns a minimal `{status, error, message}`; `server.error.include-stacktrace: never` and `include-message: never` | `GlobalExceptionHandler`, `application.yml` |
| Explicit authorization for sessions | A player may only act on **their own** session/incident (ownership checks in the service layer); creating/joining is by user id + session id/code | `SessionService`, `IncidentService` |
| Internal (simulator) endpoints not for clients | `/api/internal/*` is namespaced and guarded by an optional shared token (`sim.ingest-token` ↔ `X-Internal-Token`); in K8s the backend is a ClusterIP service with no Ingress, so it isn't reachable from outside the cluster | `SimulationController`, `k8s/` |

## The authentication model (explicit, and lightweight by design)

- A "user" is just a **username** (`POST /api/users/login` find-or-create). There are **no passwords**.
- A player is identified to the API by a numeric `playerId` passed in the request body.

### Known gaps / accepted risks
- **No real authentication** — anyone can claim any username, and a client could submit an
  action with another player's `playerId`. Mitigation in place: a player can only act on an
  incident that belongs to them, so cross-player tampering is limited, but it is **not**
  prevented. A real deployment needs proper auth (JWT or session cookie) binding `playerId`
  to an authenticated identity. This is the main item to harden before production.
- **`/api/internal` ingest** trusts the shared token + network isolation. Sufficient for the
  MVP/cluster, but it accepts `rootCause`/metrics, so it must never be exposed publicly.
- **CORS**: not needed as deployed — the browser only talks to the frontend origin, which
  reverse-proxies `/api` to the backend (Vite proxy in dev, nginx in Docker/K8s).

## Quick checklist for reviewers
- [x] No secrets committed (`.env`, `secret.yaml` gitignored; only `*.example`)
- [x] Validation + server-side action checks
- [x] Scores/state only mutated server-side
- [x] Hidden root cause never sent to clients
- [x] Errors don't leak stack traces
- [ ] Real authentication (lightweight only today — documented gap)

## SonarQube review (Step 7 — what was found / fixed / accepted)

First full multi-language scan: **Quality Gate passed**; Coverage 44.6%, Duplications 0.0%,
Maintainability **A**. Issues triaged honestly (fixed real problems; accepted style findings
with reasons) — we did **not** disable rules or suppress issues to move ratings.

**Fixed (genuine):**
- **`S2819` (CRITICAL, `App.jsx`)** — `window.postMessage(..., '*')` used a wildcard target
  origin. Changed to `window.location.origin` (same-window listener), clearing the only
  critical issue → Security rating back to A.
- **Simulator Dockerfile ran as root** — added a non-root `USER` (the simulator only makes
  outbound calls).
- **Unused vars / dead stores** (`S1481`/`S1854`) in `App.jsx`, `NetworkMap3D.jsx` — removed.

**Security hotspots (6) — reviewed:**
- 5× "pseudorandom generator" (`simulator.py` match seeding, `NetworkMap3D.jsx` 3D visuals)
  marked **Safe** with justifications: randomness is for gameplay determinism / visuals, never
  for secrets, tokens or auth.
- 1× Dockerfile-root — addressed by the non-root `USER` above.

**Accepted (not defects — documented, not suppressed):**
- ~141× `S6774` "validate props" and ~30 a11y/style rules across the React UI. The frontend is
  **plain JS without PropTypes/TypeScript**; these are convention findings, not reliability or
  security defects. Maintainability is already rated **A**, so no rule was disabled — the issues
  remain visible on the dashboard with this written rationale.

### Current status (after the feature consolidation + gameplay/UX work)

The quality gate is configured **clean-as-you-code** (0 new violations on new code) and currently
**PASSES**. As new code landed we **fixed findings in code rather than accepting them** — including:
- a genuine **rules-of-hooks bug** (`js:S6440`) in the incident screen (hooks declared after an
  early return — a latent crash, not a style nit) — caught by Sonar and fixed;
- **PropTypes added** to the new/changed React components (so new UI doesn't add to the S6774
  backlog), plus refactors for nested ternaries, array-index keys and modal accessibility;
- Java duplicate-literal constants (`S1192`).

Nothing was rule-disabled or per-issue suppressed to pass. Coverage has since risen well above the
first scan (backend instruction coverage ~93%; the full frontend suite now runs). The older
untouched UI still carries the documented S6774 convention findings above (maintainability **A**).

## OWASP ZAP scan (DAST) — what was found / fixed / accepted

Scanned the running app (`http://localhost:5173`) with OWASP ZAP. No High/Critical findings.
All reported alerts were missing-security-header / external-dependency issues.

**Fixed:**
- **CSP not set** → added a strict `Content-Security-Policy` (all `default-src 'self'`, no
  external origins; explicit `form-action`/`frame-src`/`worker-src`/`manifest-src`).
- **Missing anti-clickjacking** → `X-Frame-Options: DENY` + CSP `frame-ancestors 'none'`.
- **X-Content-Type-Options missing** → `nosniff`.
- **Server version leak** (`nginx/1.31.1`) → `server_tokens off`.
- **Sub-Resource-Integrity missing** (Google Fonts `<link>`) → **fonts are now self-hosted**
  (`@fontsource`, bundled by Vite); the external font CDN is gone entirely, so there's no
  third-party resource to integrity-check and CSP is tightened to `font-src 'self'`.
- Bonus hardening: `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`,
  `Cross-Origin-Resource-Policy`.

All headers are set in `frontend/nginx.conf`. Re-scan: **0 FAIL**, 63 passing rules.

**Accepted (with reasons):**
- **CSP `style-src 'unsafe-inline'`** — required because the React UI uses inline `style={{…}}`
  attributes throughout; a documented trade-off, not a defect.
- **Cross-Origin-Embedder-Policy not set** — `require-corp` can break legitimate same-origin/
  `data:` resources and isn't needed for this app's threat model; COOP + CORP are set instead.
- **Storable/Cacheable content** and **"Modern Web Application"** — informational (caching static
  assets is intended; the second is just ZAP detecting an SPA).

## Diagnostic console (looks like a shell, isn't one)

The investigation console is a **pure emulator** — a safety-relevant design point:
- Typed commands are **never executed**. There is no shell, `eval`, process or filesystem access.
- The server matches input against a **whitelist** of known commands and returns generated text;
  anything else returns "command not found". Input is length-bounded (`@Size(max=200)`) and the raw
  string is never passed to any interpreter → no command injection / RCE despite the terminal look.
- Console commands run under the same ownership/state guards as other player actions (your own
  OPEN incident in an ACTIVE session), and the emulated output never contains the hidden root cause.

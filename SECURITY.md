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

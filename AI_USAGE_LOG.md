# AI Usage Log

All AI-assisted changes must be logged here before merging. See CLAUDE.md for policy.

> **Reviewer column:** add the initials of the team member who reviewed each change before
> it was committed. Entries marked _pending_ still need a human review recorded.
> AI tool used: Claude Code (Claude Opus). Branch: `front-end` (earlier work on `back-end-rest-api`).
> **Many 2026-05-29 entries are marked _(pending review)_** — they were verified to compile/build
> and were exercised end-to-end against a throwaway backend, but still need a human review pass
> before the team relies on them, per the AI policy.

| Date | Author | Area | What AI generated | How it was reviewed | Changes made before commit |
|------|--------|------|------------------|--------------------|-----------------------------|
| 2026-05-28 | Team + Claude | Project docs (`CLAUDE.md`, `ARCHITECTURE.md`, `GAME_LOGIC.md`, `AI_USAGE_LOG.md` template) | Read the project brief PDF and drafted the four shareable project/context docs | Team read against the brief | Trimmed/edited to match the team's own decisions |
| 2026-05-28 | Team + Claude | Runnable skeleton (Docker Compose + MySQL, Spring Boot/Maven/Java 21, React+Vite, K8s manifests, `.env.example`, `.gitignore`) | Generated the multi-service skeleton to get the team running | Team confirmed services start via Docker Compose | AI initially over-built full API code; team had it stripped back to a **runnable skeleton only** |
| 2026-05-28 | Team + Claude | JPA entity identifiers | Added `@Id`/`@GeneratedValue` to empty entity skeletons so Spring Boot could boot | Team verified the app starts and connects to MySQL | None |
| 2026-05-28 | Team (AI-assisted) | Game database schema (entities + repositories) | AI assisted; **team authored** the `Incident` entity in full and extended the repository finders | Team authored/reviewed directly | Substantial team authorship beyond AI suggestions |
| 2026-05-28 | Team + Claude | REST controllers + service stubs | Generated controller endpoints and empty service stubs from the team's project-goals document | Team reviewed endpoint design against their document | Endpoints chosen by the team; AI matched them |
| 2026-05-28 | Team + Claude | `IncidentResponse` DTO | Generated DTO so the API hides `rootCause` from clients (anti-cheat / security) | Team reviewed; compiled against real entity | Fixed to match entity structure (`@ManyToOne` relations, enum `.name()`); removed non-existent fields |
| 2026-05-29 | Team + Claude | Game engine (`ActionType`, `RootCause`, `EvaluationResult`, `IncidentEvaluator`, `ScoreCalculator`) | Generated the pure, dependency-free game-logic core; rules based on the `GAME_LOGIC.md` incident/action matrix | Compiles on JDK 21; **unit tests to be written by the team** | None — kept pure (no Spring/DB) so the team can test it exhaustively |
| 2026-05-29 | Team + Claude | Head-to-head schema change | Added `player_id` FKs to `network_cells`/`incidents`, `duration_seconds` to `game_sessions`, built out `ScoreEvent`, deleted duplicate `User` entity, added per-player repo finders | Team confirmed clean compile | None |
| 2026-05-29 | Team + Claude | `GAME_LOGIC.md` update | Documented the 2-player head-to-head mode and the engine (rules, root-cause→action map, scoring formula) | Team reviewed | None |
| 2026-05-29 | Team + Claude | Service layer + error handling _(pending review)_ | Implemented all five service bodies, wired the engine into `IncidentService.submitAction`, added exception classes + `GlobalExceptionHandler`, added `durationSeconds` to the create-session request | Compiles on JDK 21; **human review pending before relying on it** | Flagged for review: (1) `SessionController` request changed to accept `durationSeconds`; (2) network/incident **generation not included** (simulator's job); (3) `playerId` trusted from the request until real auth exists |
| 2026-05-29 | Team + Claude | H2 test environment | H2 (test scope), `application-test.yml`, `data.sql` seed, a context smoke test; `sql.init.mode=never` for prod | Smoke test passes on H2 | `@ColumnDefault` added later so the seed loads after schema |
| 2026-05-29 | Team + Claude | Frontend import (Claude-designed UI) | Imported the Claude-designed React UI into `frontend/`, keeping the Docker/nginx/`/api`-proxy setup | Builds via Vite | Dropped redundant generated config; deleted `vite-export/` |
| 2026-05-29 | Team + Claude | REST API client + Lobby connect flow | `api.js` client + `Lobby.jsx` (identify → create/join → match room); `App` gated on a connection | Verified create/join end-to-end vs the live backend | — |
| 2026-05-29 | Team + Claude | DTO responses + per-player API _(pending review)_ | All controllers return DTOs (fixed Jackson lazy-proxy 500s); `?playerId=` filtering on cells/incidents | Verified full flow on a throwaway instance; **human review pending** | Initial Java generation here was later replaced by the simulator |
| 2026-05-29 | Team + Claude | Frontend store wired to backend _(pending review)_ | Rewrote `store.js` to fetch/poll the backend and map shapes (recommended actions hidden); `/api/actions`; screen tweaks | Builds; manual play; **human review pending** | — |
| 2026-05-29 | Team + Claude | Ready-check + 3s countdown | `Player.ready`, `POST /sessions/{id}/ready`, lobby ready buttons + countdown; removed auto-start | Verified both-ready → ACTIVE | — |
| 2026-05-29 | Team + Claude | Python network simulator (sole generator) + ingest _(pending review)_ | New stdlib `simulator/` service; `/api/internal` ingest endpoints + optional token guard; removed `GameInitializer` | Verified seeding, mirroring, ongoing incidents; **human review pending** | Security: internal endpoints need cluster isolation / token — flagged |
| 2026-05-29 | Team + Claude | Username login (find-or-create) | `POST /api/users/login` returns the existing user or registers | Verified idempotent (same name → same id) | Impersonation possible without auth — flagged |
| 2026-05-29 | Team + Claude | Bug fix: UI not populating | Fixed `api.js` `getCells`/`getIncidents` signatures (playerId was sent as `status` → 400 → empty UI) | Verified UI populates | — |
| 2026-05-29 | Team + Claude | Cell heal/degrade on resolve | `IncidentService.applyOutcome` heals the cell on a correct fix, degrades it on a trap | Verified GOOD/CRITICAL transitions | — |
| 2026-05-29 | Team + Claude | Difficulty ramp + cascade + match timer _(pending review)_ | Simulator difficulty curve; backend cascade (a trap spawns incidents on neighbours); frontend countdown timer | Verified cascade spawns neighbour incidents; **human review pending** | Cascade can snowball on repeated mistakes (by design) |
| 2026-05-29 | Team + Claude | Cheat sheet + end-of-game screen | `GAMEPLAY_CHEATSHEET.md`; `GameOver.jsx` winner/summary screen | Verified scoring math (+140/−95/−10) and ENDED transition | — |

## Notes / known gaps to revisit

- **No authentication yet** — `playerId`/username are taken from the request; anyone can claim
  any username and the `/api/internal` simulator endpoints rely on namespace + an optional
  shared token. The brief's "explicit authorization" still needs a real auth model.
- **Simulator is the sole generator** — a match has no cells/incidents until the Python
  simulator (its own container) picks it up; if the simulator is down, matches stay empty.
- **Cascade can snowball** — repeated wrong actions keep spawning neighbour incidents
  (bounded by `MAX_INCIDENT_CELLS` + per-cell de-dupe); tune if playtests feel too harsh.
- **Build with JDK 21**, not the default JDK 26 — Lombok cannot process 26.
- **Tests still to be written by the team** — AI did not write tests. The engine, `submitAction`
  (+ heal/cascade), and the `api.js` signature bug are all things unit/integration/frontend
  tests should now cover.

# AI Usage Log

All AI-assisted changes must be logged here before merging. See CLAUDE.md for policy.

> **Reviewer column:** add the initials of the team member who reviewed each change before
> it was committed. Entries marked _pending_ still need a human review recorded.
> AI tool used: Claude Code (Claude Opus). Branch: `back-end-rest-api`.

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

## Notes / known gaps to revisit

- **No authentication yet** — `playerId` is taken from the request body. A defence-in-depth
  check (you may only act on your own incident) is in place, but the brief's "explicit
  authorization" needs real auth. Record in security notes.
- **No incident/metric generation yet** — the Python simulator (or a Java seeder) must create
  the mirrored per-player networks before a full create→join→incident→action→score flow runs.
- **Build with JDK 21**, not the default JDK 26 — Lombok cannot process 26 and accessors fail
  to generate.
- **Tests are written by the team**; AI did not generate tests (per the AI policy that AI must
  not generate tests without human authorship/review).

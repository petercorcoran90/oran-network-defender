# Testing & Quality Strategy

Living plan for the testing phase. We build quality plumbing first (SonarQube + Postgres +
GitHub Actions), then add tests **one layer at a time**, committing after each layer so a
breakage is easy to isolate and Sonar produces a fresh report at every step.

> Maps to the brief's **Testing Requirements** + **Code Quality** + Definition of Done
> ("automated tests present and passing", "SonarQube quality gate met or justified",
> "run all tests with a single command").

---

## Tooling decisions

| Concern | Choice | Why |
|--------|--------|-----|
| Java unit/slice | JUnit 5 + Mockito + `@WebMvcTest` | fast, no DB; ships with `spring-boot-starter-test` |
| Java integration/system | **Testcontainers (MySQL 8)** via `@SpringBootTest` + `@ServiceConnection` | tests against real MySQL = matches prod, avoids H2 drift |
| Java coverage | **JaCoCo** → XML report for Sonar | Sonar reads `sonar.coverage.jacoco.xmlReportPaths` |
| Frontend | **Vitest + React Testing Library** (already in `package.json`); Playwright for one E2E | component + action + display tests; LCOV coverage for Sonar |
| Python | **pytest** (+ `pytest-cov`) | simulator/data-generation tests; coverage.xml for Sonar |
| Quality | **SonarQube Community** + **PostgreSQL** (Sonar's own DB) | brief allows SonarQube/SonarCloud; Community is self-hosted |
| CI | **GitHub Actions** | build + test + coverage on each push |

---

## Phase 0 — Quality plumbing (do first, its own commit)

1. **SonarQube Community + Postgres** via a dedicated compose file (e.g. `quality/docker-compose.sonar.yml`):
   - `postgres` (Sonar's backing store — separate from the app's MySQL) + `sonarqube:community`.
   - Bring up with `docker compose -f quality/docker-compose.sonar.yml up -d`, log in at
     `http://localhost:9000`, create a project + token.
2. **JaCoCo** in `backend/pom.xml` (prepare-agent + report) so coverage XML is produced by `mvn verify`.
3. **`sonar-project.properties`** at repo root covering all three languages:
   - `sonar.sources` = backend `src/main`, `frontend/src`, `simulator`
   - `sonar.tests` = the test dirs
   - coverage paths: JaCoCo XML (Java), LCOV (frontend), coverage.xml (Python)
4. **GitHub Actions** workflow (`.github/workflows/ci.yml`): JDK 21 → `mvn verify` (Testcontainers
   needs Docker — available on GitHub runners) → Node `npm ci && npm test` → Python `pytest` →
   `sonar-scanner`.

> ⚠️ **Reachability gotcha:** a SonarQube running locally in Docker is **not reachable from
> GitHub-hosted runners.** Options: (a) run the Sonar **scan locally** after each phase (simplest;
> show the report in the demo) and let GitHub Actions just build+test+coverage; (b) self-host
> Sonar somewhere public + set `SONAR_HOST_URL`/`SONAR_TOKEN` secrets; (c) switch to SonarCloud.
> **Recommendation for this project: (a)** — local SonarQube Community for the report, GitHub
> Actions for build/test/coverage gating.

> **Community Edition limits:** single-branch analysis only (no PR decoration / multi-branch).
> Fine here — analyse the working branch on each push/commit.

---

## Test layers (the pyramid) — what each covers

| Layer | Tool | Covers (brief) |
|------|------|----------------|
| **Unit** (no Spring/DB) | JUnit | game rules, scoring, incident handling — `IncidentEvaluator` (all 72 incident×action combos) + `ScoreCalculator` (time-bonus boundaries, costs) |
| **Controller/API** | `@WebMvcTest` (mocked services) | routing, Bean Validation, **negative tests** for invalid actions, error-body shape |
| **Integration** | Testcontainers MySQL (`@DataJpaTest`/`@SpringBootTest`) | repositories + persistence; `submitAction` heal/cascade; score events |
| **System / E2E** | Testcontainers MySQL (`@SpringBootTest`) | the brief's mandatory flow: create → join → (seed via ingest) → submit action → score/state changes |
| **Frontend** | Vitest + RTL (+ Playwright) | key components, action submission, incident/score display, one full-flow E2E |
| **Python** | pytest | simulator output is **repeatable** (seeded), generated incidents are **valid** (rootCause/severity in range) |

---

## Incremental rollout — one commit (and one Sonar run) per step

Do them in this order; each is a self-contained commit so regressions are easy to bisect.
(Per-step results are noted inline as they land; full detail + reviewers live in `AI_USAGE_LOG.md`.)

- [x] **Step 0** — Phase 0 plumbing (Sonar+Postgres compose, JaCoCo, `sonar-project.properties`, GitHub Actions skeleton). Commit. First Sonar baseline.
  - _Done:_ `quality/docker-compose.sonar.yml` + README, JaCoCo in `pom.xml`, root `sonar-project.properties`, `.github/workflows/ci.yml` (3 jobs). `mvn verify` emits `jacoco.xml`; CI green.
- [x] **Step 1** — **Backend unit tests**: engine (72-combo matrix + scoring). Commit → Sonar.
  - _Done:_ `IncidentEvaluatorTest` (hand-written correct/trap + full 72 sweep + contextual-IGNORE) + `ScoreCalculatorTest` (golden scores + time-bonus boundaries). **120 tests, 100% line+branch on the engine package.**
- [x] **Step 2** — **Controller/API + negative tests** (`@WebMvcTest`). Commit → Sonar.
  - _Done:_ `SessionControllerTest` (8), `IncidentControllerTest` (6), `UserControllerTest` (4). Validation 400s, exception→status (404/409/400), leak-free error body. **18 tests.**
- [x] **Step 3** — **Integration tests** (Testcontainers MySQL): repositories + `submitAction`/heal/cascade. Commit → Sonar.
  - _Done:_ retired H2; `AbstractMySqlIntegrationTest` (one shared container), `PersistenceIntegrationTest` (2), `SubmitActionIntegrationTest` (4: correct→heal/score, trap→fail/cascade, ineffective→open, cross-player rejected). Local fix: pinned docker-java API to 1.41 (Surefire) for daemons that reject API<1.40.
- [x] **Step 4** — **System/E2E** full-flow test (the brief's required one). Commit → Sonar.
  - _Done:_ `SystemFlowTest` (`@SpringBootTest` RANDOM_PORT + `TestRestTemplate`): create→join×2→ready→ACTIVE→ingest cell+incident→correct action→score & incident RESOLVED, + root-cause-not-leaked check. **Backend total: 145 tests.**
- [x] **Step 5** — **Frontend tests** (Vitest/RTL). Commit → Sonar.
  - _Done:_ `api.test.js` (URL/body incl. playerId regression, ApiError, 204 → **api.js 100%**), `store.test.js` (statusOf/Selectors + backend→UI mapping incl. root-cause-hidden + applyAction → **store.js 98%**), `screens.test.jsx` (Incidents/Scoreboard display + IncidentDetail action submission). **18 tests.** LCOV emitted for Sonar. (Playwright E2E dropped — the backend `SystemFlowTest` already covers the full flow.)
- [ ] **Step 6** — **Python tests** (pytest for the simulator). Commit → Sonar.
- [ ] **Step 7** — Review the **quality gate**, fix/justify findings, document coverage delta.

> **Coverage feeds are per-language, not merged into JaCoCo.** JaCoCo = Java only
> (`backend/target/site/jacoco/jacoco.xml`); frontend = LCOV (`frontend/coverage/lcov.info`);
> Python = `simulator/coverage.xml`. Sonar ingests all three separately and aggregates them into
> the project-level Coverage %.

---

## Quality gate (per the brief)

- No critical / blocker issues
- Meaningful unit-test coverage (set a realistic threshold once the engine/service tests land)
- No obvious duplication
- Security hotspots reviewed
- No hardcoded secrets
- Be ready to explain: what Sonar found, what was fixed, what was accepted and why, coverage trend.

## "Run all tests with a single command"

- Backend: `mvn verify` (unit + Testcontainers integration/system + JaCoCo).
- Frontend: `npm test`.
- Python: `pytest`.
- A top-level `make test` (or `scripts/test.sh`) chains all three so the whole suite runs from one command for the demo.

## Open notes / decisions
- **Testcontainers needs Docker** in whatever runs the tests (local + CI). GitHub runners have it.
- Keep the existing **H2 test profile** only if we want ultra-fast local repo tests; otherwise
  retire it once Testcontainers MySQL is in, to avoid maintaining two DB behaviours.
- Reuse **one shared MySQL container** across the suite (don't boot per-test) to keep it quick.
- Per the AI policy, **tests are team-authored/reviewed** — AI may draft, humans verify they
  assert meaningful behaviour before committing.

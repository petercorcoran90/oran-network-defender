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
| Java unit — engine | JUnit 5 (pure, no Spring) | game rules / scoring are pure functions |
| Java unit — services | JUnit 5 + **Mockito** (`@ExtendWith(MockitoExtension.class)`, mocked repositories) | fast, readable; asserts service logic without a Spring context or DB |
| Java unit — controllers | **Direct unit tests** — `new XController(mock(service))`, call the method, assert the DTO/exception (+ `ControllerMappingTest`) | the team's convention: clearer and faster than `@WebMvcTest`/MockMvc |
| Java integration | **Testcontainers (MySQL 8)** via `@SpringBootTest` | persistence + service wiring against real MySQL (avoids H2 drift) |
| Java system / API | **Testcontainers + `TestRestTemplate`** (`SystemFlowTest`, RANDOM_PORT) | the brief's mandatory full-flow test, over real HTTP. **No Karate** — this is the "Testcontainers API test". |
| Java coverage | **JaCoCo** → XML report for Sonar | Sonar reads `sonar.coverage.jacoco.xmlReportPaths` |
| Frontend | **Vitest + React Testing Library** | components, action submission, incident/score display; LCOV coverage for Sonar |
| Python | **pytest** (+ `pytest-cov`) | simulator/data-generation tests; coverage.xml for Sonar |
| Quality | **SonarQube Community** + **PostgreSQL** (Sonar's own DB) | brief allows SonarQube/SonarCloud; Community is self-hosted |
| CI | **GitHub Actions** | build + test + coverage on each push |

> **Backend test convention (team):** the service layer and controllers are **unit-tested with
> Mockito** — instantiate the class with mocked collaborators and assert behaviour directly (no
> MockMvc). Testcontainers covers persistence/integration, and `SystemFlowTest` (Testcontainers +
> `TestRestTemplate`) is the one full-flow API/E2E test. Earlier AI-drafted `@WebMvcTest` controller
> tests were rewritten by the team in this style for readability.

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
| **Unit — engine** | JUnit (pure) | game rules, scoring, incident handling — `IncidentEvaluatorTest` (72 incident×action combos) + `ScoreCalculatorTest` |
| **Unit — services** | JUnit + Mockito | service logic with mocked repos — `IncidentServiceTest`, `ScoreServiceTest`, `SessionServiceTest` (incl. **negative cases** for invalid actions) |
| **Unit — controllers** | Direct (mocked service) | routing/mapping + exception→status — `SessionControllerTest`, `IncidentControllerTest`, `UserControllerTest`, `ControllerMappingTest` |
| **Integration** | Testcontainers MySQL (`@SpringBootTest`) | persistence + service wiring; `submitAction` heal/cascade; **session isolation** (`ServiceIsolationIntegrationTest`); `SessionServiceIntegrationTest`; `PersistenceIntegrationTest` |
| **System / API** | Testcontainers + `TestRestTemplate` | the brief's mandatory flow over real HTTP: create → join → seed via ingest → submit action → score/state change (`SystemFlowTest`) |
| **Frontend** | Vitest + RTL | components + flows — `App`, `Lobby`, `GameOver`, `screens`, `ui`, `store`, `api` |
| **Python** | pytest | simulator output is **repeatable** (seeded), generated incidents are **valid** (rootCause/severity in range) |

---

## Incremental rollout — one commit (and one Sonar run) per step

Do them in this order; each is a self-contained commit so regressions are easy to bisect.
(Per-step results are noted inline as they land; full detail + reviewers live in `AI_USAGE_LOG.md`.)

- [x] **Step 0** — Phase 0 plumbing (Sonar+Postgres compose, JaCoCo, `sonar-project.properties`, GitHub Actions skeleton). Commit. First Sonar baseline.
  - _Done:_ `quality/docker-compose.sonar.yml` + README, JaCoCo in `pom.xml`, root `sonar-project.properties`, `.github/workflows/ci.yml` (3 jobs). `mvn verify` emits `jacoco.xml`; CI green.
- [x] **Step 1** — **Backend unit tests**: engine (72-combo matrix + scoring). Commit → Sonar.
  - _Done:_ `IncidentEvaluatorTest` (hand-written correct/trap + full 72 sweep + contextual-IGNORE) + `ScoreCalculatorTest` (golden scores + time-bonus boundaries). **120 tests, 100% line+branch on the engine package.**
- [x] **Step 2** — **Service & controller unit tests** (Mockito + direct). Commit → Sonar.
  - _Done (team):_ Mockito service tests (`IncidentServiceTest`, `ScoreServiceTest`, `SessionServiceTest`) + direct controller unit tests (`SessionController`/`Incident`/`User` + `ControllerMappingTest`). Negative cases for invalid actions, exception→status (404/409/400). The team rewrote the earlier AI-drafted `@WebMvcTest` controller tests in this style for readability.
- [x] **Step 3** — **Integration tests** (Testcontainers MySQL): repositories + `submitAction`/heal/cascade. Commit → Sonar.
  - _Done:_ retired H2; `AbstractMySqlIntegrationTest` (one shared container), `PersistenceIntegrationTest` (2), `SubmitActionIntegrationTest` (4: correct→heal/score, trap→fail/cascade, ineffective→open, cross-player rejected). Local fix: pinned docker-java API to 1.41 (Surefire) for daemons that reject API<1.40.
- [x] **Step 4** — **System/E2E** full-flow test (the brief's required one). Commit → Sonar.
  - _Done:_ `SystemFlowTest` (`@SpringBootTest` RANDOM_PORT + `TestRestTemplate`): create→join×2→ready→ACTIVE→ingest cell+incident→correct action→score & incident RESOLVED, + root-cause-not-leaked check. **Backend total: 145 tests.**
- [x] **Step 5** — **Frontend tests** (Vitest/RTL). Commit → Sonar.
  - _Done:_ `api.test.js` (URL/body incl. playerId regression, ApiError, 204 → **api.js 100%**), `store.test.js` (statusOf/Selectors + backend→UI mapping incl. root-cause-hidden + applyAction → **store.js 98%**), `screens.test.jsx` (Incidents/Scoreboard display + IncidentDetail action submission). **18 tests.** LCOV emitted for Sonar. (Playwright E2E dropped — the backend `SystemFlowTest` already covers the full flow.)
- [x] **Step 6** — **Python tests** (pytest for the simulator). Commit → Sonar.
  - _Done:_ `simulator/test_simulator.py` (12): archetypes match the backend RootCause enum + valid severity/health/config, `severity_pool` ramp, `build_plan` repeatable + valid incidents, `drift` repeatable & keeps incident-free cells GOOD, `derive_health` thresholds, `elapsed_fraction` bounds/safety. `coverage.xml` for Sonar.
- [x] **Step 7** — Review the **quality gate**, fix/justify findings, document coverage delta.
  - _Done:_ first full multi-language scan — **Quality Gate passed**, Coverage **44.6%**, Duplications **0.0%**, Maintainability **A**. Fixed the one CRITICAL (`S2819` postMessage origin), the Dockerfile-root hotspot, and unused-var smells; reviewed 6 hotspots (5 pseudorandom → Safe w/ reasons); accepted (not suppressed) the ~141 prop-validation + a11y findings as plain-JS-React conventions. Full write-up in `SECURITY.md`.
  - _Coverage rationale:_ 44.6% overall is dragged down by presentational React (`App`, `Lobby`, `GameOver`, `TweaksPanel`) and the WebGL map (`NetworkMap3D`, excluded). The **logic** is strongly covered: engine **100%** (line+branch), `api.js` **100%**, `store.js` **98%**, services/controllers via the integration + slice tests.

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

## Consolidation status (branches)

The competitive game + tests live on `test-containers` (team-owned: Mockito service/controller
unit tests, Testcontainers integration, `SystemFlowTest`, broader frontend set, plus a
session-isolation refactor in `f2703f4`). The **learning-progression feature** (investigation
console + curriculum + training mode) was built on a *stack of feature branches*
(`feat/investigation → feat/console → feat/progression`) off an **older** `test-containers`, and its
tests were AI-drafted in a different style.

**To consolidate**, the feature **production code** is brought onto current `test-containers`, and the
feature's **tests are (re)written in the team's conventions** above (Mockito for services/controllers,
Testcontainers for integration). A straight merge is avoided because (a) both sides changed the
service layer (session-isolation vs diagnostics/progression) and (b) it would drag the AI-style tests
in. See the consolidation plan agreed with the team.

## Open notes / decisions
- **No Karate** — the API/system layer is `SystemFlowTest` (Testcontainers + `TestRestTemplate`).
- **Testcontainers needs Docker** in whatever runs the tests (local + CI). GitHub runners have it.
- Keep the existing **H2 test profile** only if we want ultra-fast local repo tests; otherwise
  retire it once Testcontainers MySQL is in, to avoid maintaining two DB behaviours.
- Reuse **one shared MySQL container** across the suite (don't boot per-test) to keep it quick.
- Per the AI policy, **tests are team-authored/reviewed** — AI may draft, humans verify they
  assert meaningful behaviour before committing.

# Code Quality — SonarQube (local)

We run **SonarQube Community** locally in Docker (with its own PostgreSQL) and analyse the
repo after each testing step, so every commit in the testing phase produces a fresh report.
GitHub Actions handles build/test/coverage; the Sonar scan is run locally because a
localhost SonarQube isn't reachable from GitHub-hosted runners.

## 1. Start SonarQube

```bash
docker compose -f quality/docker-compose.sonar.yml up -d
# wait ~1 min for startup, then open http://localhost:9000
```

First login is `admin` / `admin` (you'll be asked to set a new password).
Create a project (key `oran-network-defender`) and generate an **analysis token**
(My Account → Security → Generate Token).

Install the scanner once: `brew install sonar-scanner` (macOS) or grab it from
https://docs.sonarsource.com/sonarqube/latest/analyzing-source-code/scanners/sonarscanner/

## 2. Produce coverage, then scan

Run from the repo root after the layer's tests pass:

```bash
# Java coverage (also runs the backend tests)
( cd backend && mvn -B verify )

# Frontend coverage (once Step 5 adds the coverage provider)
( cd frontend && npm test -- --coverage )

# Python coverage (once Step 6 adds tests)
( cd simulator && pytest --cov=. --cov-report=xml )

# Analyse everything (paths come from ../sonar-project.properties)
sonar-scanner -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<YOUR_TOKEN>
```

Then check the quality gate at http://localhost:9000.

## Notes
- Postgres here is **Sonar's own database**, unrelated to the app's MySQL.
- SonarQube needs `vm.max_map_count >= 262144` (Docker Desktop sets this automatically).
- Community Edition analyses a single branch only — fine for this project.

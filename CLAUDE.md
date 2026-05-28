# O-RAN Network Defender — Claude Code Context

## Project Summary
Browser-based multiplayer strategy game where teams operate a simplified 5G network
management system. Players monitor simulated network cells, detect faults, respond to
incidents, and keep the network healthy. Inspired by real O-RAN / SMO management scenarios.

Graduate onboarding project: emphasis on software quality, test automation, security,
scalability, and responsible AI-assisted development.

---

## Tech Stack

| Layer | Required |
|-------|----------|
| Backend | Java + Spring Boot |
| Frontend | Browser-based (React / Angular / Vue — team choice) |
| Real-time | WebSocket, Server-Sent Events, or polling |
| Persistence | JPA / JDBC + DB of team's choice |
| Simulation | Python (network metrics, incident generation) |
| Containers | Docker (all services) |
| Orchestration | Kubernetes manifests |
| Quality | SonarQube / SonarCloud |

---

## Architecture Overview

```
Browser UI
    |
    |  REST / WebSocket
    v
Spring Boot Game API
    |
    |  Service layer
    v
Game Engine / Rules Engine
    |
    |  Events
    v
Python Network Simulator
    |
    v
Database
```

Key separation of concerns:
- **Game Engine**: pure game logic, scoring, rule evaluation — no HTTP/persistence concerns
- **Game API**: thin Spring controllers that delegate to the engine
- **Python Simulator**: standalone service that generates metric changes and incidents; communicates via REST or message queue
- **Frontend**: display only; all game-state changes go through the backend API

---

## Game Logic Reference

### Cell Health Metrics
Each simulated network cell exposes: signal quality, user load, latency, packet loss, alarm
count, energy usage, configuration status.

### Incident Types
- Cell overload
- Configuration change causing degraded performance
- Transport link instability
- Alarm storm masking the real root cause
- Neighbour cell interference
- Software-upgrade fault
- Rogue automation making things worse

### Player Actions (examples)
Rebalance traffic, restart cell, roll back neighbour config, ignore alarm, increase transmit
power. **The same action must not always be correct** — context determines the best choice.

### Scoring Factors (minimum 3)
Network health, incident response time, action correctness, customer impact, remediation cost.

### Game Modes (pick one for MVP)
- Co-operative: all players share the network
- Competitive: each player/team owns a region
- Hybrid: shared incidents, individual scores

### Gameplay Loop
1. Player joins session in browser
2. Backend assigns player to a network region
3. Metrics change over time
4. Incidents appear as alerts
5. Player inspects cell, chooses action
6. Action affects game state (server-side rules only)
7. Score updates
8. End-of-game summary

---

## API Design Guidance

Minimum endpoints:
- `POST /sessions` — create game session
- `POST /sessions/{id}/players` — join session
- `GET /sessions/{id}/state` — current network state
- `GET /sessions/{id}/incidents` — active incidents
- `POST /sessions/{id}/actions` — submit player action
- `GET /sessions/{id}/scores` — scoreboard

All game-state mutations must be server-side. Clients may never directly modify scores or
network state.

---

## Testing Requirements

| Layer | What to test |
|-------|-------------|
| Unit (Java) | Game rules, scoring logic, incident handling |
| Controller / API | Valid and invalid requests |
| Integration | Persistence layer |
| Python | Simulator output validity, repeatability |
| Frontend | Key components, action submission, incident display |
| E2E / System | Full flow: create session → join → incident → action → score change |

Run all tests with a single command. At minimum one automated test proves the full flow.

---

## Security Requirements (non-negotiable)

- Input validation on all API requests
- Server-side validation of player actions
- Players cannot modify another player's score directly
- No secrets in source control
- No injection vulnerabilities (SQL, command, etc.)
- Error messages must not leak implementation details
- Explicit authorization rules for joining/managing sessions

---

## Quality Gate (SonarQube)

- No critical / blocker issues
- Meaningful unit test coverage
- No obvious duplication
- Security hotspots reviewed
- No hardcoded secrets

Be ready to explain: what was found, what was fixed, what was accepted and why, how
coverage changed over the project.

---

## Scalability Considerations

Design so the system is explainable at scale. Be ready to answer:
- What breaks first at 100 players?
- What breaks first with 1,000 simulated cells?
- What breaks first with 10 concurrent sessions and frequent updates (seconds interval)?

Minimum: HPA for at least one service in Kubernetes; game state not coupled to a single
browser session; avoid unnecessary synchronous blocking.

---

## AI Usage Policy

Maintain an **AI Usage Log** (a file in the repo). For every AI-assisted change, log:
- What was generated
- Who reviewed it
- What changes were made before committing

AI **must not** generate security logic, tests, or large code blocks without human review.
No code committed that the team cannot explain.

---

## Deliverables Checklist

- [ ] Source code repository
- [ ] Browser-playable application
- [ ] Docker configuration (all services)
- [ ] Kubernetes deployment manifests
- [ ] Automated test suite (single-command run)
- [ ] SonarQube quality report
- [ ] Architecture document
- [ ] AI Usage Log
- [ ] Security notes
- [ ] Final demonstration

### Definition of Done
Game is browser-playable, multiple players can join, incidents occur and resolve, scores
update, Docker works, K8s manifests exist, tests pass, SonarQube gate met, security and
scalability are documented, AI usage is logged.

---

## Stretch Goals (post-MVP reference)

**Gameplay**: player roles (Incident Commander, Radio Engineer, Automation Engineer,
Security Analyst), hidden root causes, incident chains, replay mode, leaderboard, difficulty levels.

**O-RAN fidelity**: model SMO, Non-RT RIC, Near-RT RIC, O-CU, O-DU, O-RU; policy
optimization; xApp/rApp-inspired automation; config drift; SLA breaches.

**Technical**: message queues for incident events, distributed tracing, Prometheus + Grafana,
load testing, blue-green deployment, feature flags, dedicated rules engine.

**AI in gameplay**: AI generates incident explanations (after engine determines cause), hint
assistant with limited confidence, players judge AI recommendations, AI cannot directly change
game state.

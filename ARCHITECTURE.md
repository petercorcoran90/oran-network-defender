# O-RAN Network Defender — Architecture Reference

## System Overview

O-RAN Network Defender is a browser-based multiplayer strategy game. Players manage a
simulated 5G network, respond to incidents, and are scored on network health and decision
quality. The architecture mirrors real O-RAN SMO concerns at a simplified level.

---

## Component Map

```
┌─────────────────────────────────────────────────────────────┐
│                        Browser (UI)                         │
│  Network Map · Incident List · Action Controls · Scoreboard │
└────────────────────────┬────────────────────────────────────┘
                         │  REST (game actions, state queries)
                         │  WebSocket / SSE (live updates)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Spring Boot Game API (Java)                    │
│  Session Controller · Incident Controller · Score Controller│
│                        │                                    │
│              ┌─────────▼──────────┐                        │
│              │    Game Engine     │                        │
│              │  (pure game logic) │                        │
│              │  Rules · Scoring · │                        │
│              │  Action Validation │                        │
│              └─────────┬──────────┘                        │
│                        │  JPA / JDBC                       │
│              ┌─────────▼──────────┐                        │
│              │      Database      │                        │
│              │  Sessions · Cells  │                        │
│              │  Incidents · Scores│                        │
│              └────────────────────┘                        │
└──────────────────────┬──────────────────────────────────────┘
                       │  REST / Message Queue (events)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              Python Network Simulator                       │
│  Metric Generation · Incident Factory · Seed-Repeatable    │
└─────────────────────────────────────────────────────────────┘
```

---

## Service Responsibilities

### Spring Boot Game API
- Owns all game-state mutations (no direct client writes to DB)
- Exposes REST endpoints for session lifecycle, action submission, state queries
- Pushes real-time updates to connected clients (WebSocket or SSE)
- Delegates game logic exclusively to the Game Engine

### Game Engine (internal module, not a separate service for MVP)
- Validates player actions against current game state
- Applies action consequences to network cell state
- Calculates scores (health, response time, action quality, cost, customer impact)
- Determines incident resolution and generates outcome feedback
- Has no I/O dependencies — injectable, unit-testable without a container

### Python Network Simulator
- Runs as a separate process/container
- Produces time-series metric changes for each simulated cell
- Generates incidents with enough evidence for player decision-making
- Incident output must be deterministic given a seed (for test repeatability)
- Communicates changes to the Game API via REST POST or a message queue

### Database
- Stores: game sessions, players, network cells, incidents, player actions, scores
- Access patterns: frequent reads of cell state; write on every player action
- Avoid N+1 queries on cell-state reads; consider bulk fetch per session

### Frontend
- Renders current game state polled or pushed from the backend
- Never computes game outcomes locally
- Key views: network map/cell dashboard, incident list, action form, scoreboard, summary

---

## API Contract (Minimum)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/sessions` | Create game session |
| GET | `/sessions/{id}` | Session metadata |
| POST | `/sessions/{id}/players` | Join session |
| GET | `/sessions/{id}/state` | Full network state |
| GET | `/sessions/{id}/incidents` | Active incidents |
| POST | `/sessions/{id}/actions` | Submit player action |
| GET | `/sessions/{id}/scores` | Scoreboard |

WebSocket channel (or SSE stream): push cell metric updates and new incidents to all connected
players in a session without polling.

Error responses: use standard HTTP status codes; response bodies must not leak stack traces or
internal implementation details.

---

## Data Model (Sketch)

```
GameSession
  id, status (WAITING | ACTIVE | ENDED), mode (COOP | COMPETITIVE | HYBRID),
  createdAt, endedAt

Player
  id, sessionId, displayName, score

NetworkCell
  id, sessionId, signalQuality, userLoad, latency, packetLoss,
  alarmCount, energyUsage, configStatus, healthScore

Incident
  id, sessionId, affectedCellIds[], severity, evidenceJson,
  createdAt, resolvedAt, rootCause

PlayerAction
  id, playerId, incidentId, actionType, submittedAt,
  outcome (CORRECT | INCORRECT | NEUTRAL), scoreImpact

Score
  id, playerId, sessionId, totalScore, healthComponent,
  responseTimeComponent, correctnessComponent, costComponent
```

---

## Real-Time Strategy

Pick one approach for MVP; document the choice:

| Option | Complexity | Fit |
|--------|-----------|-----|
| WebSocket (STOMP/SockJS) | Medium | Best for frequent updates and bi-directional |
| Server-Sent Events | Low | Good for server-push only |
| Short-polling | Low | Simplest, works everywhere, fine for low frequency |

Recommendation: SSE for metric updates and incident pushes; REST for action submission.
Upgrade to WebSocket if bi-directional interaction is needed for a stretch goal.

---

## Deployment Architecture

### Docker Compose (local dev / demo)
```
services:
  db          # PostgreSQL or equivalent
  backend     # Spring Boot Game API
  simulator   # Python Network Simulator
  frontend    # Static build served by Nginx (if SPA)
  sonarqube   # Optional, local quality analysis
```

### Kubernetes (production-style)
```
Deployments:
  backend     — HPA enabled (scale on CPU / request rate)
  simulator   — single replica or HPA
  frontend    — single replica behind Ingress

Services:
  backend-svc     ClusterIP
  simulator-svc   ClusterIP
  frontend-svc    ClusterIP → Ingress

ConfigMaps:  game settings, simulator parameters
Secrets:     DB credentials, API keys

Probes (all deployments):
  livenessProbe:  /actuator/health/liveness
  readinessProbe: /actuator/health/readiness
```

---

## Scalability Notes

| Bottleneck | Risk | Mitigation |
|-----------|------|-----------|
| Game state in memory | Breaks with HPA | Store all state in DB; stateless backend |
| WebSocket session affinity | Breaks with HPA | Use sticky sessions or externalise session (Redis) |
| Metric update frequency | DB write flood | Batch writes; consider in-memory cache + periodic flush |
| Incident query per client | N+1 on large sessions | Bulk-fetch incidents per sessionId |
| Simulator → API coupling | Tight sync call | Async queue (Kafka / RabbitMQ) for stretch goal |

---

## Security Checklist

- All player actions validated server-side before state change
- Players cannot submit actions on behalf of another player (check `playerId == authenticated user`)
- Input validation via Bean Validation (`@Valid`, `@NotNull`, size constraints) on all request bodies
- No SQL built by string concatenation — use JPA / parameterised queries
- Secrets in environment variables / K8s Secrets, never in source code
- API errors return generic messages; stack traces never reach the client
- Auth may be lightweight (JWT or session cookie) but the model must be explicit and documented

---

## O-RAN Terminology Glossary (for context)

| Term | Meaning in this project |
|------|------------------------|
| SMO | Service Management and Orchestration — the top-level control layer the game simulates |
| Non-RT RIC | Non-Real-Time RAN Intelligent Controller — policy-level decisions (stretch goal) |
| Near-RT RIC | Near-Real-Time RIC — fast automated responses (stretch goal) |
| O-CU | O-RAN Central Unit — upper layers of the base station |
| O-DU | O-RAN Distributed Unit — lower layers of the base station |
| O-RU | O-RAN Radio Unit — the antenna/radio front end |
| xApp | Application running on Near-RT RIC for real-time optimization |
| rApp | Application running on Non-RT RIC for policy and analytics |
| Cell | One simulated radio base station sector in the game |
| Alarm storm | Many alarms firing simultaneously, masking the real fault |

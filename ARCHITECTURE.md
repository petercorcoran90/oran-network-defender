# O-RAN Network Defender — Architecture

A browser-based **2-player head-to-head** strategy game. Two players operate identical,
mirrored simulated 5G/O-RAN networks at the same time; they detect and remediate incidents,
and the higher score when the timer runs out wins. The framing mirrors the O-RAN **SMO**
(Service Management & Orchestration) role: monitor, configure, optimise the RAN.

> Companion docs: **API_REFERENCE.md** (endpoints), **GAME_LOGIC.md** (rules/scoring),
> **GAMEPLAY_CHEATSHEET.md** (answer key), **SECURITY.md** (security model).

---

## Component map

```
                 Browser (React + Vite, served by nginx)
                          │  REST + short polling (every ~2.5s), /api/*
                          ▼
           ┌─────────────────────────────────────────────┐
           │        Spring Boot Game API (Java 21)        │
           │  User / Session / NetworkCell / Incident /   │
           │  Score / HighScore / Simulation controllers  │
           │                    │                         │
           │        ┌───────────▼───────────┐             │
           │        │  Game Engine (pure)    │  IncidentEvaluator
           │        │  rules + scoring       │  ScoreCalculator
           │        └───────────┬───────────┘             │
           │                    │  JPA                     │
           │            ┌───────▼────────┐                 │
           │            │   MySQL 8      │                 │
           │            └────────────────┘                 │
           └───────────────▲─────────────────────────────┘
                           │  REST: poll /api/sessions, POST /api/internal/*
              ┌────────────┴─────────────┐
              │  Python Network Simulator │  (own container; sole world generator)
              └───────────────────────────┘
```

## Service responsibilities

- **Frontend** (React + Vite; nginx in prod): renders state, submits actions, polls for
  updates. Never computes outcomes. nginx reverse-proxies `/api` to the backend.
- **Game API** (Spring Boot): owns all state mutation; thin controllers → services →
  repositories. Returns **DTOs only** (never JPA entities) so the hidden `rootCause` and
  lazy relations are never serialised.
- **Game Engine** (pure, no Spring/DB): `IncidentEvaluator` maps a hidden `RootCause` + chosen
  `ActionType` → `CORRECT/INEFFECTIVE/HARMFUL`; `ScoreCalculator` turns that into a points
  delta (correctness + response-time bonus − action cost). Unit-testable in isolation.
- **Python simulator** (own container): the **sole** generator of cells, incidents and metric
  changes. Polls the API for `ACTIVE` sessions and writes via `/api/internal/*`. No incoming
  connections.
- **MySQL**: sessions, players, cells, incidents, actions, player actions, score events, match
  results.

## Data model (as built)

```
AppUser(id, username, role, createdAt)
GameSession(id, sessionCode, name, status[WAITING|ACTIVE|ENDED], difficulty[EASY|MEDIUM|HARD],
            durationSeconds, startedAt, endedAt, forfeitedByPlayerId, createdByUser)
Player(id, user, gameSession, teamName, score, ready, joinedAt)
NetworkCell(id, gameSession, player, cellName, signalQuality, userLoad, latency, packetLoss,
            alarmCount, energyUsage, healthStatus[GOOD|WARNING|CRITICAL],
            configStatus[STABLE|CHANGED|DRIFT])
Incident(id, gameSession, player, cell, incidentType, severity[LOW|MEDIUM|HIGH],
         status[OPEN|RESOLVED|FAILED], description, rootCause /* server-only */, createdAt, resolvedAt)
Action(id, actionName, description)              # the 9-action catalog
PlayerAction(id, player, incident, action, result[SUCCESS|PARTIAL|FAILED], pointsAwarded, submittedAt)
ScoreEvent(id, player, gameSession, reason, points, createdAt)   # append-only score history
MatchResult(id, gameSessionId, winnerName, winnerScore, loserName, difficulty,
            durationSeconds, forfeit, createdAt)                 # high-score table
```
Each player owns a **private, mirrored** copy of the network: `NetworkCell` and `Incident`
carry a `player_id`, so one player's actions never affect the other's cells.

## Match lifecycle

```
login (username) → create match (name, difficulty, minutes) OR join (code / browse list)
   → both players Ready  → backend ACTIVE  → client 3-2-1 countdown → game console
   → simulator seeds the network + opening incidents, then keeps each board topped up
   → players inspect incidents, submit actions; engine scores them, cells heal/degrade
   → timer expires (lazy on read) OR a player leaves (ragequit = forfeit)  → ENDED
   → end-of-game summary; a MatchResult row is recorded for the high-score table
```

## Real-time strategy

**Short polling** (frontend refreshes every ~2.5s). Chosen over WebSocket/SSE for MVP
simplicity and because the backend is stateless (state lives in the DB), which keeps it
horizontally scalable. SSE/WebSocket is a documented upgrade path.

## Simulator contract

Per `ACTIVE` session the simulator:
1. seeds cells (3/6/9 by difficulty) + a couple of opening incidents, **identical for both
   players** (seeded from the session id → deterministic/repeatable, fair start);
2. each tick, **refills** toward a target that grows with tower count and elapsed time, and
   **drifts** healthy cells within the healthy band (degradation only ever comes from incidents);
3. writes everything through `/api/internal/*` (cells, incidents, metric updates). Each incident
   carries a hidden `rootCause` from the engine's enum so the engine can score actions.

## Deployment

### Docker Compose (local/demo)
```
db (MySQL) · backend (Spring Boot) · simulator (Python) · frontend (nginx)
```
`docker compose up --build` brings up all four; the frontend's nginx proxies `/api` → `backend`.

### Kubernetes
```
Deployments:  backend (HPA 2→5 on CPU), simulator (single replica), frontend (LoadBalancer svc)
StatefulSet:  db (+ headless db-service, PVC)
Services:     backend-service, frontend-service, db-service
Config:       ConfigMap (DB host/port/name, server port) + Secret (DB creds, SIM_INGEST_TOKEN)
Probes:       backend liveness/readiness via /actuator/health/*
```
The frontend nginx targets `backend-service` via the `BACKEND_HOST` env (envsubst). The
simulator is **single-replica** (two would double-generate). The `/api/internal` endpoints
stay cluster-internal (no Ingress) and are guarded by `SIM_INGEST_TOKEN`.

## Scalability notes

| Bottleneck | Risk | Mitigation |
|-----------|------|-----------|
| Game state in memory | Breaks with HPA | All state is in MySQL; backend is stateless → scales horizontally |
| Polling frequency | DB read load at scale | Short, per-session reads; cache / SSE if needed |
| Simulator throughput | One process for many sessions | Partition sessions across simulator instances (by session id) for a stretch goal |
| Incident queries | N+1 on large sessions | Per-player/per-session fetches via indexed FKs |

What breaks first: at ~100 players / ~1,000 cells / 10 sessions with frequent updates, the
**polling read volume** and the **single simulator** are the first limits — addressed by SSE +
sharding the simulator.

## Security

See **SECURITY.md**. Summary: server-side validation + action checks, scores only mutated
server-side, hidden root cause, no secrets in source, errors don't leak internals. Auth is
lightweight (username only) — a documented gap to harden before production.

## O-RAN glossary

| Term | Meaning here | Interfaces (in the topology view) |
|------|--------------|-----------------------------------|
| SMO | Service Management & Orchestration; hosts the Non-RT RIC | O1 to managed elements |
| Non-RT RIC | Non-real-time controller, rApps (>1s loops) | A1 → Near-RT RIC |
| Near-RT RIC | Near-real-time controller, xApps (10ms–1s) | E2 → O-CU/O-DU |
| O-CU | Central Unit (RRC/PDCP/SDAP) | F1 ↔ O-DU; N2/N3 ↔ 5GC |
| O-DU | Distributed Unit (RLC/MAC/High-PHY) | Open Fronthaul ↔ O-RU |
| O-RU | Radio Unit (RF + Low-PHY); the game's "cells" live here | — |
| AMF / UPF | 5G Core control / user plane | N2 (control) / N3 (user) |

> O-RAN structure/interfaces verified against the O-RAN Software Community architecture docs
> (docs.o-ran-sc.org) and Wikipedia "Open RAN".

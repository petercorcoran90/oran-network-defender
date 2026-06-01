# Network Simulator

Standalone service that generates the game world. It is the **sole** source of network cells,
incidents, and ongoing metric changes — the Spring backend no longer seeds anything.

## How it works

It polls the Game API and pushes state in via the internal ingest endpoints (it serves no
requests itself):

1. `GET /api/sessions` — find `ACTIVE` sessions.
2. For a session it hasn't seeded: `GET /api/sessions/{id}/players`, build a **deterministic
   plan seeded from the session id**, then `POST` identical cells + incidents for **both**
   players (so the match is mirrored and fair).
3. Each tick after that: drift healthy cells (`POST /api/internal/cells/{id}/metrics`) and
   occasionally inject a new incident on both players' matching cell.

Because the plan is seeded by the session id, a given session is **repeatable** (the spec's
determinism requirement). Incident root causes match the backend `RootCause` enum so the game
engine can evaluate player actions against them.

## Run locally

```bash
BACKEND_URL=http://localhost:8080 python3 simulator.py
```

Stdlib only (uses `urllib`) — no dependencies to install.

## Configuration (env vars)

| Var | Default | Meaning |
|-----|---------|---------|
| `BACKEND_URL` | `http://localhost:8080` | Game API base URL |
| `SIM_INGEST_TOKEN` | _(empty)_ | If the backend sets `sim.ingest-token`, send the same value here |
| `POLL_SECONDS` | `3` | Poll interval |
| `CELL_COUNT` | `6` | Cells per player |
| `DRIFT_EVERY_TICKS` | `2` | Drift healthy cells every N ticks |
| `NEW_INCIDENT_EVERY_TICKS` | `8` | Inject a new incident every N ticks |
| `MAX_INCIDENT_CELLS` | `5` | Cap on simultaneously-degraded cells |

## Docker

Built and wired as the `simulator` service in the repo's `docker-compose.yml`
(`depends_on: backend`). In Kubernetes it runs as its own Deployment and only needs to reach
the backend's ClusterIP — it is never exposed via Ingress.

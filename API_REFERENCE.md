# O-RAN Network Defender — API Reference

All paths are under the backend at `http://localhost:8080`. The frontend calls them with the
relative prefix **`/api`** (the Vite dev server proxies `/api` → `:8080`, so no CORS setup).

- Request/response bodies are JSON (`Content-Type: application/json`).
- Timestamps are ISO-8601 UTC (e.g. `2026-05-29T15:19:02.669506Z`).
- `rootCause` is **never** returned to clients (it's the answer key).

### Enums
| Field | Values |
|-------|--------|
| session `status` | `WAITING`, `ACTIVE`, `ENDED` |
| session `difficulty` | `EASY` (3 towers), `MEDIUM` (6), `HARD` (9) |
| incident `status` | `OPEN`, `RESOLVED`, `FAILED` |
| `severity` | `LOW`, `MEDIUM`, `HIGH` |
| cell `healthStatus` | `GOOD`, `WARNING`, `CRITICAL` |
| cell `configStatus` | `STABLE`, `CHANGED`, `DRIFT` |
| action `result` | `SUCCESS`, `PARTIAL`, `FAILED` |

### Error responses
Any 4xx/5xx returns this shape (no stack traces):
```json
{ "status": 404, "error": "Not Found", "message": "Session not found" }
```
Common codes: `400` invalid body / bad query, `404` not found, `409` conflict (e.g. session
full, already joined, match already started).

---

## Typical play flow
1. `POST /api/users/login` → get your `userId`
2. `POST /api/sessions` (creator) → get `sessionId` + `sessionCode`
3. `POST /api/sessions/{id}/join` (both players) → each gets a `playerId`
4. `POST /api/sessions/{id}/ready` (both) → session becomes `ACTIVE`
5. Poll `GET /api/sessions/{id}/cells?playerId=…` and `…/incidents?playerId=…`
6. `POST /api/sessions/{id}/incidents/{incidentId}/actions` → score updates
7. Poll `GET /api/sessions/{id}/scores`; at time-up the session is `ENDED`

---

## Users

### `POST /api/users/login`  ← use this for the lobby
Logs in by username, registering on first use (no "name taken" error). **Request:**
```json
{ "username": "alice" }
```
**Response 200:**
```json
{ "id": 1, "username": "alice", "role": "PLAYER", "createdAt": "2026-05-29T14:03:32.007Z" }
```

### `POST /api/users`  (strict register — 409 if name exists)
**Request:** `{ "username": "alice", "role": "PLAYER" }` → **201** same shape as above.

### `GET /api/users/{id}`
**Response 200:** same `AppUser` shape. `404` if missing.

---

## Sessions

### `POST /api/sessions`
**Request:** (`durationSeconds` optional positive int; `difficulty` optional — `EASY`/`MEDIUM`/`HARD` → 3/6/9 towers, defaults `MEDIUM`)
```json
{ "name": "Friday match", "createdByUserId": 1, "durationSeconds": 600, "difficulty": "MEDIUM" }
```
**Response 201 (`SessionResponse`):**
```json
{
  "id": 5, "sessionCode": "L5Y9FS", "name": "Friday match", "status": "WAITING",
  "durationSeconds": 600, "difficulty": "MEDIUM",
  "startedAt": null, "endedAt": null, "forfeitedByPlayerId": null, "createdByUserId": 1
}
```
> `forfeitedByPlayerId` is set if a player left mid-match (ragequit) — that player forfeits and the other wins regardless of score.

### `GET /api/sessions`
All sessions with status `WAITING` or `ACTIVE`. **Response 200:** `[ SessionResponse, … ]`.

### `GET /api/sessions/{id}`
**Response 200:** one `SessionResponse`. Once started, `startedAt`/`endedAt` are set; reading an
expired `ACTIVE` session flips it to `ENDED`.

### `POST /api/sessions/{id}/join`
**Request:** (`teamName` optional — defaults to the username)
```json
{ "userId": 2, "teamName": "Bravo" }
```
**Response 201 (`PlayerResponse`):**
```json
{ "id": 7, "teamName": "Bravo", "score": 0, "ready": false }
```
`409` if the session is full (max 2) or the user already joined.

### `POST /api/sessions/{id}/ready`
Marks a player ready; when **both** players are ready the match activates. **Request:**
```json
{ "playerId": 7 }
```
**Response 200:** the `SessionResponse` (its `status` becomes `ACTIVE` once both are ready).

### `POST /api/sessions/{id}/leave`
A player leaves mid-match — **ends the session for both** (the leaver forfeits). **Request:**
`{ "playerId": 7 }` → **200** `SessionResponse` (status `ENDED`, `forfeitedByPlayerId` set).

### `POST /api/sessions/{id}/start`
Manual start (needs 2 players). **Response 200:** `SessionResponse`. (The lobby uses `ready`.)

### `GET /api/sessions/{id}/players`
**Response 200 (`[ PlayerResponse, … ]`):**
```json
[ { "id": 6, "teamName": "alice", "score": 140, "ready": true },
  { "id": 7, "teamName": "Bravo", "score": 0,   "ready": true } ]
```

---

## Network cells

### `GET /api/sessions/{sessionId}/cells`
Query param **`playerId`** (recommended) returns just that player's network; omit it for all
cells in the session. **Response 200 (`[ CellResponse, … ]`):**
```json
[ {
  "id": 75, "playerId": 19, "cellName": "Cell-01",
  "signalQuality": 95.0, "userLoad": 30.0, "latency": 25.0, "packetLoss": 1.0,
  "alarmCount": 0, "energyUsage": 45.0, "healthStatus": "GOOD", "configStatus": "STABLE"
} ]
```

### `GET /api/sessions/{sessionId}/cells/{cellId}`
**Response 200:** one `CellResponse`. `404` if the cell isn't in that session.

---

## Incidents

### `GET /api/sessions/{sessionId}/incidents`
Query params (both optional): **`playerId`** (this player's incidents) and **`status`**
(`OPEN`/`RESOLVED`/`FAILED`). e.g. `?playerId=19&status=OPEN`.
**Response 200 (`[ IncidentResponse, … ]`):**
```json
[ {
  "id": 57, "gameSessionId": 13, "playerId": 19, "cellId": 75,
  "incidentType": "Alarm Storm", "severity": "HIGH", "status": "OPEN",
  "description": "A burst of alarms is masking the underlying fault on this cell.",
  "createdAt": "2026-05-29T15:04:25.744Z", "resolvedAt": null
} ]
```
> Note: no `rootCause` — that's hidden on purpose.

### `GET /api/sessions/{sessionId}/incidents/{incidentId}`
**Response 200:** one `IncidentResponse`.

### `POST /api/sessions/{sessionId}/incidents/{incidentId}/actions`
Submit a remediation action. **Request:** (`actionId` from `GET /api/actions`)
```json
{ "playerId": 19, "actionId": 1 }
```
**Response 200 (`PlayerActionResponse`):**
```json
{
  "id": 1, "playerId": 19, "incidentId": 57, "actionId": 1,
  "result": "SUCCESS", "pointsAwarded": 140, "submittedAt": "2026-05-29T15:04:30.111Z"
}
```
`400` if the incident isn't yours, the session isn't `ACTIVE`, or the incident is already
resolved. `result` is `SUCCESS` (correct), `PARTIAL` (ineffective), or `FAILED` (trap).

### `GET /api/sessions/{sessionId}/incidents/{incidentId}/actions`
**Response 200:** `[ PlayerActionResponse, … ]` — all attempts on that incident.

---

## Scores

### `GET /api/sessions/{sessionId}/scores`
Scoreboard, players ordered by score desc. **Response 200:** `[ PlayerResponse, … ]` (same shape
as `/players`).

### `GET /api/sessions/{sessionId}/scores/events`
Full score history (why points moved). **Response 200 (`[ ScoreEventResponse, … ]`):**
```json
[ { "id": 1, "playerId": 19, "reason": "Cell Overload / CORRECT", "points": 140,
    "createdAt": "2026-05-29T15:04:30.120Z" } ]
```

---

## Actions catalog

### `GET /api/actions`
The 9 remediation actions and their ids (use the `id` in the submit-action call).
**Response 200 (`[ ActionResponse, … ]`):**
```json
[ { "id": 1, "actionName": "REBALANCE_TRAFFIC", "description": "Move load to neighbouring cells to reduce congestion." },
  { "id": 2, "actionName": "RESTART_CELL",      "description": "Restart a cell to clear transient faults." } ]
```
Full list: `REBALANCE_TRAFFIC`, `RESTART_CELL`, `ROLLBACK_CONFIG`, `ROLLBACK_SOFTWARE`,
`INCREASE_TRANSMIT_POWER`, `FILTER_ALARMS`, `DISABLE_AUTOMATION`, `ESCALATE`, `IGNORE`.

---

## High scores

### `GET /api/highscores`
Top 20 finished matches by winner score. **Response 200:**
```json
[ { "winnerName": "alice", "winnerScore": 740, "loserName": "bob",
    "difficulty": "HARD", "durationSeconds": 600, "forfeit": false,
    "createdAt": "2026-05-30T18:00:00Z" } ]
```
A result is recorded once per session when it ends (timer expiry or forfeit).

---

## Internal (do NOT call from the frontend)
`POST /api/internal/sessions/{id}/cells`, `…/incidents`, and `POST /api/internal/cells/{id}/metrics`
exist **only** for the Python simulator (they accept `rootCause`/metrics). The UI never uses
these — they're cluster-internal and may require an `X-Internal-Token` header.

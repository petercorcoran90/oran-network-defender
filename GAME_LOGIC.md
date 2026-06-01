# O-RAN Network Defender — Game Logic Reference

## Game Mode — 2-Player Head-to-Head (MVP)

The MVP is a **1-versus-1 duel**. Two players join one session and play simultaneously.

- Each player gets their **own private copy** of the network (cells + incidents).
- Both copies are **mirrored**: the same incidents, with the same type, severity and hidden
  root cause, appear on both players' networks at the same time.
- Players solve their own incidents independently — one player restarting a cell has **no
  effect** on the other player's network.
- The match runs for a fixed time, chosen by the session creator (`durationSeconds`).
- When the timer expires the session moves to `ENDED`. **Highest score wins**; equal = draw.

Because both players face identical problems, the only variable is decision quality — which
makes the game provably fair and the scoring trivial to settle.

> **Data model implication:** `network_cells` and `incidents` both carry a `player_id` FK
> (in addition to `game_session_id`) so each player owns their mirrored copy. `game_sessions`
> carries `duration_seconds`. See `ARCHITECTURE.md` for the full schema.

---

## Gameplay Loop (step by step)

```
1. Player A opens browser → enters name → creates a GameSession (picks durationSeconds)
2. Player B joins the session via its session code
3. When the 2nd player joins, the session starts: each player is given an identical,
   private copy of the network (mirrored cells + incidents from the same seed)
4. Python Simulator drifts each player's cell metrics over time
5. Game API pushes live metric updates to connected players (SSE / WebSocket)
6. Incidents appear in each player's Incident List with evidence (root cause hidden)
7. Player inspects evidence (cell metrics, alarms, neighbour changes)
8. Player selects and submits a remediation action
9. Game Engine evaluates the action against the hidden root cause → verdict + points
10. The player's own cell state and score update; opponent is unaffected
11. Scoreboard refreshes for both players
12. When the session timer expires → game ends
13. End-of-game summary shown: per-player breakdown, best/worst decisions, winner
```

---

## Network Cell State

Each cell has these health indicators. All are numeric (e.g. 0–100 scale or raw values).

| Metric | Description | High value = |
|--------|-------------|-------------|
| `signalQuality` | Signal strength / SNR | Good |
| `userLoad` | % capacity used | Bad (overload risk) |
| `latency` | Round-trip time (ms) | Bad |
| `packetLoss` | % packets dropped | Bad |
| `alarmCount` | Active alarms | Bad |
| `energyUsage` | Power consumption | Neutral / monitored |
| `configStatus` | STABLE / CHANGED / DRIFT | STABLE = good |

**Cell health score** = composite of the above. Suggested formula (team can adjust):

```
health = (signalQuality * 0.2)
       + ((100 - userLoad) * 0.2)
       + ((100 - latency_normalised) * 0.2)
       + ((100 - packetLoss) * 0.2)
       + ((100 - alarm_normalised) * 0.1)
       + (energyUsage_ok ? 5 : 0)
       + (configStatus == STABLE ? 5 : 0)
```

---

## Incident Types

| Incident | Key evidence | Best action | Trap (wrong action) |
|----------|-------------|------------|-------------------|
| Cell overload | High `userLoad`, high `latency` | Rebalance traffic to neighbours | Restart cell (causes brief outage, loses more points) |
| Config-change degradation | `configStatus = CHANGED` on neighbour, rising `packetLoss` | Roll back neighbour config | Restart affected cell (doesn't fix root cause) |
| Transport link instability | `packetLoss` spikes, `latency` variable | Investigate transport path, escalate | Restart cell repeatedly |
| Alarm storm | Many `alarmCount` spikes across cells | Filter alarms, identify primary fault | Act on every alarm (wastes actions, lowers score) |
| Neighbour interference | Low `signalQuality` on two adjacent cells | Adjust frequency/power on one cell | Restart both cells |
| Software-upgrade fault | `configStatus = DRIFT`, after maintenance window | Roll back software version | Increase transmit power (masks symptom) |
| Rogue automation | Metrics worsening after automation action | Disable automation, roll back | Apply more automation |

**Rule**: the same action must not always win. Context (neighbouring cell state, recent
changes, alarm patterns) determines the correct choice.

---

## Player Actions

Actions available to players (backend enforces validity):

| Action | Cost | Effect if correct | Effect if wrong | Effect if neutral |
|--------|------|-----------------|----------------|------------------|
| `REBALANCE_TRAFFIC` | 10 | Reduces load, improves health | Overloads neighbour, spreads fault | No change |
| `RESTART_CELL` | 20 | Clears transient faults | Causes brief downtime, negative score | Wastes time |
| `ROLLBACK_CONFIG` | 10 | Fixes config-change incidents | No effect on non-config incidents | No change |
| `ROLLBACK_SOFTWARE` | 15 | Fixes upgrade faults | No effect otherwise | No change |
| `INCREASE_TRANSMIT_POWER` | 10 | Fixes interference (sometimes) | Worsens interference | Marginal |
| `FILTER_ALARMS` | 5 | Helps find root cause in alarm storm | Misses real alarm | No change |
| `DISABLE_AUTOMATION` | 10 | Stops rogue automation | Removes good automation | Neutral |
| `ESCALATE` | 5 | Correct for unresolvable faults | Wastes time if resolvable | Slight negative |
| `IGNORE` | 0 | Sometimes correct (false alarm) | Misses real incident | Large negative |

`Cost` = `remediationCost` in `ActionType`, subtracted from the score on every use.

---

## Scoring System

A player's `score` is the running sum of per-action deltas, each recorded as a `score_event`
(append-only, so scores can't be silently edited). The delta is computed by the engine's
`ScoreCalculator` and combines **three** of the brief's scoring factors:

| Factor | How it enters the delta | Direction |
|--------|-------------------------|-----------|
| **Action correctness** | CORRECT earns base points; HARMFUL loses a penalty | Correct = better |
| **Response time** | A bonus that decays the longer the player takes | Faster = better |
| **Remediation cost** | Every action subtracts its own cost | Cheaper fix = better |

### Score Delta per Action (as implemented)

```
CORRECT      →  +100  + timeBonus(responseSeconds)  − action.remediationCost
HARMFUL      →  −75   − action.remediationCost
INEFFECTIVE  →        − action.remediationCost
```

- `timeBonus` = up to **50**, decaying linearly to **0** over **60 seconds**
  (0s → 50, 30s → 25, ≥60s → 0).
- `remediationCost` is per-action (see Player Actions table). e.g. `RESTART_CELL` = 20
  (causes downtime), `IGNORE` = 0.

Constants live in `ScoreCalculator` (`BASE_POINTS`, `MAX_TIME_BONUS`, `BONUS_DECAY_SECONDS`,
`HARMFUL_PENALTY`) — change them there to rebalance.

> Network health is still shown live from the Simulator's metric stream, but the **competitive
> score** that decides the duel comes from these per-action deltas.

---

## Game Engine

The engine (`com.oran.defender.engine`) is the **pure core** of the game: no Spring context,
no database, no HTTP, no dependency on the Python simulator. It answers one question —
*given an incident's hidden root cause and the action a player chose, was it right, and what's
it worth?* Being deterministic, it judges both duelling players by identical rules and can be
unit-tested exhaustively.

### Pieces

| Type | Responsibility |
|------|----------------|
| `ActionType` | The 9 actions (names match the seeded `actions` table); each carries a `remediationCost`. |
| `RootCause` | The real underlying problem (server-side only). Each declares its **correct action** and its **trap actions**. |
| `EvaluationResult` | The verdict: `CORRECT`, `INEFFECTIVE`, or `HARMFUL`. |
| `IncidentEvaluator` | `evaluate(rootCause, action) → EvaluationResult`. |
| `ScoreCalculator` | `pointsFor(result, responseSeconds, action) → int` (the deltas above). |

### Evaluation rules

```
action == rootCause.correctAction()  → CORRECT
action ∈ rootCause.trapActions        → HARMFUL
otherwise                             → INEFFECTIVE
```

### Root-cause → action map

| Root cause | Correct action | Trap actions |
|------------|----------------|--------------|
| `CELL_OVERLOAD` | `REBALANCE_TRAFFIC` | `RESTART_CELL`, `IGNORE` |
| `NEIGHBOUR_CONFIG_CHANGE` | `ROLLBACK_CONFIG` | `RESTART_CELL`, `IGNORE` |
| `TRANSPORT_LINK_FAULT` | `ESCALATE` | `RESTART_CELL`, `IGNORE` |
| `ALARM_STORM` | `FILTER_ALARMS` | `RESTART_CELL`, `IGNORE` |
| `NEIGHBOUR_INTERFERENCE` | `INCREASE_TRANSMIT_POWER` | `RESTART_CELL`, `IGNORE` |
| `SOFTWARE_UPGRADE_FAULT` | `ROLLBACK_SOFTWARE` | `INCREASE_TRANSMIT_POWER`, `IGNORE` |
| `ROGUE_AUTOMATION` | `DISABLE_AUTOMATION` | `REBALANCE_TRAFFIC`, `IGNORE` |
| `FALSE_ALARM` | `IGNORE` | `RESTART_CELL` |

### Why it keys on root cause, not incident type

The brief's headline rule is *"the same action must not always be correct."* The engine
decides correctness from the **hidden root cause**, so the same action can be right or wrong
depending on context. Clearest example — `IGNORE`:

- `FALSE_ALARM` → `IGNORE` is **correct** (`CORRECT`, +points)
- any real incident → `IGNORE` is a **trap** (`HARMFUL`, −points)

This is also why `root_cause` is never sent to the client (`IncidentResponse` omits it) — it's
the answer key.

### Engine ↔ persistence mapping

The engine's verdict maps onto the persisted `PlayerAction.ActionResult` / incident status in
the service layer:

```
CORRECT     → ActionResult.SUCCESS , incident → RESOLVED
INEFFECTIVE → ActionResult.PARTIAL , incident stays OPEN
HARMFUL     → ActionResult.FAILED  , incident → FAILED
```

The service is thin glue: translate the stored strings to engine enums
(`RootCause.valueOf`, `ActionType.valueOf`), call `evaluate` then `pointsFor`, then persist
the `PlayerAction` + `ScoreEvent` and bump the player's score.

---

## Incident Evidence Structure

Each incident sent to the frontend should include enough information for a decision:

```json
{
  "id": "inc-42",
  "affectedCells": ["cell-17"],
  "severity": "HIGH",
  "summary": "Cell-17 showing high latency and packet loss",
  "evidence": [
    { "label": "Cell-17 user load", "value": "92%", "trend": "rising" },
    { "label": "Cell-18 config changed", "value": "15 min ago", "trend": "stable" },
    { "label": "Alarm count (both cells)", "value": "increased", "trend": "rising" },
    { "label": "Energy usage Cell-17", "value": "normal", "trend": "stable" }
  ],
  "availableActions": ["REBALANCE_TRAFFIC", "RESTART_CELL", "ROLLBACK_CONFIG", "IGNORE", "INCREASE_TRANSMIT_POWER"],
  "createdAt": "2026-01-01T10:00:00Z"
}
```

The `rootCause` is **never sent to the client** — it is stored server-side and used only by the
Game Engine to evaluate actions.

---

## Session Lifecycle

```
WAITING   → created by player 1; waiting for player 2 to join (max 2 players)
    ↓        auto-starts when the 2nd player joins; each player seeded an identical network
ACTIVE    → metrics changing, incidents firing, actions accepted (timer running)
    ↓        timer expires (started_at + duration_seconds elapsed)
ENDED     → no more actions accepted; scores compared; higher score wins (equal = draw)
```

---

## End-of-Game Summary (required)

Show per player (or team):
- Total score
- Breakdown by component
- Incidents responded to vs. ignored
- Best decision (highest score delta)
- Worst decision (lowest score delta)
- Network health at end vs. start

---

## Game Balance Notes

- At least one incident should have a **non-obvious root cause** (alarm storm masking).
- At least one incident should have a **trap action** that looks correct but is wrong.
- Difficulty can be adjusted via incident frequency, evidence clarity, and time pressure.
- Stretch: difficulty levels change these parameters dynamically.

---

## Simulator Contract

The Python simulator must produce output the Game API can consume. Suggested interface:

**Metric update event** (pushed periodically):
```json
{
  "sessionId": "sess-1",
  "cellId": "cell-17",
  "metrics": { "userLoad": 92, "latency": 180, "packetLoss": 8, "alarmCount": 5 },
  "timestamp": "2026-01-01T10:00:00Z"
}
```

**Incident creation event**:
```json
{
  "sessionId": "sess-1",
  "incidentType": "CONFIG_CHANGE_DEGRADATION",
  "affectedCells": ["cell-17"],
  "evidenceFactors": ["NEIGHBOUR_CONFIG_CHANGED", "HIGH_PACKET_LOSS"],
  "rootCause": "NEIGHBOUR_CONFIG_ROLLBACK_NEEDED",
  "severity": "HIGH"
}
```

The simulator **does not decide outcomes** — it fires events. The Game Engine resolves them.

---

## Functional Acceptance Criteria Summary

| Criterion | Pass condition |
|-----------|--------------|
| Session creation | User can create a session from the browser |
| Session join | User can join an existing session |
| Multi-player | At least 2 players in same session simultaneously |
| Clear end condition | Session moves to ENDED state when condition is met |
| Cell metrics | Each cell has visible health metrics |
| Metric change | Metrics change over time without player action |
| Incident generation | At least one incident occurs per session |
| Incident evidence | Incident has enough info for a decision |
| Action submission | Player can pick and submit an action |
| Action effect | Correct action improves state; incorrect worsens it |
| Score update | Score changes after action |
| Scoreboard | Visible in browser during and after session |
| API correctness | Invalid actions rejected with clear error |
| Full E2E test | Automated test covers create → join → incident → action → score change |

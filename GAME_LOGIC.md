# O-RAN Network Defender — Game Logic Reference

## Game Modes

Choose **one** for MVP. Hybrid is the most complex — start with Co-op or Competitive.

| Mode | Description | Scoring |
|------|-------------|---------|
| Co-operative | All players share one network; incidents affect everyone | Single shared team score |
| Competitive | Each player/team owns a separate network region | Individual or team ranking |
| Hybrid | Shared incidents, individual scoring | Per-player score + shared health indicator |

---

## Gameplay Loop (step by step)

```
1. Player opens browser → enters name / credentials
2. Player creates or joins a GameSession
3. Backend assigns player to a network region (or shared network)
4. Python Simulator begins generating metric changes for that session's cells
5. Game API pushes live metric updates to connected players (SSE / WebSocket)
6. At intervals, Game Engine triggers incident generation (via Simulator or internally)
7. Incident appears in player's Incident List with evidence
8. Player inspects incident evidence (cell metrics, alarms, neighbour changes)
9. Player selects and submits a remediation action
10. Game Engine evaluates action against root cause → applies outcome
11. Cell state updates (better or worse depending on correctness)
12. Score updates immediately; scoreboard refreshes
13. When session timer expires (or health hits 0) → game ends
14. End-of-game summary shown: performance breakdown, best/worst decisions
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

| Action | Effect if correct | Effect if wrong | Effect if neutral |
|--------|-----------------|----------------|------------------|
| `REBALANCE_TRAFFIC` | Reduces load, improves health | Overloads neighbour, spreads fault | No change |
| `RESTART_CELL` | Clears transient faults | Causes brief downtime, negative score | Wastes time |
| `ROLLBACK_CONFIG` | Fixes config-change incidents | No effect on non-config incidents | No change |
| `ROLLBACK_SOFTWARE` | Fixes upgrade faults | No effect otherwise | No change |
| `INCREASE_TRANSMIT_POWER` | Fixes interference (sometimes) | Worsens interference | Marginal |
| `FILTER_ALARMS` | Helps find root cause in alarm storm | Misses real alarm | No change |
| `DISABLE_AUTOMATION` | Stops rogue automation | Removes good automation | Neutral |
| `ESCALATE` | Correct for unresolvable faults | Wastes time if resolvable | Slight negative |
| `IGNORE` | Sometimes correct (false alarm) | Misses real incident | Large negative |

---

## Scoring System

Score = sum of components, updated in real time.

### Components (minimum required: 3)

| Component | Description | Direction |
|-----------|-------------|-----------|
| **Network health** | Average cell health score across player's region | Higher = better |
| **Response time** | Time from incident creation to action submission | Faster = better |
| **Action correctness** | CORRECT → positive delta; INCORRECT → negative delta | Correct = better |
| **Customer impact** | Penalise cells with SLA-level degradation (optional) | Lower = better |
| **Remediation cost** | Some actions cost more than others | Lower = better |

### Score Delta per Action

```
CORRECT action:
  +basePoints (e.g. 100)
  + responseTimeBonus (e.g. max 50, decays linearly over 60 seconds)
  - remediationCost (action-specific constant)

INCORRECT action:
  -penalty (e.g. 75)
  - remediationCost

NEUTRAL action:
  - remediationCost only (or 0 if free action)
```

Health component updates continuously from the Simulator's metric stream, not just on actions.

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
WAITING   → players join, minimum 2 players recommended
    ↓        start triggered manually or auto on player count
ACTIVE    → metrics changing, incidents firing, actions accepted
    ↓        timer expires OR network health reaches 0
ENDED     → no more actions accepted, summary available
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

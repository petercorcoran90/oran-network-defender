# O-RAN Network Defender — Gameplay Cheat Sheet

> Spoiler / reference for the team. In the real game the correct action is **hidden** — that's
> the whole point ("the same action is not always correct"). Use this to test and to demo.

## How scoring works

Every action you submit changes your score immediately:

| Verdict | Shown as | Points |
|---------|----------|--------|
| **Correct** action for that incident | SUCCESS | `+100  +  timeBonus  −  actionCost` |
| **Trap** action (looks plausible, makes it worse) | FAILED | `−75  −  actionCost` |
| Anything else (ineffective) | PARTIAL | `−actionCost` (incident stays open, try again) |

- **timeBonus**: up to **+50**, decaying linearly to **0 over 60 seconds** from when the
  incident appeared. Fast correct answers score best (0s → +50, 30s → +25, ≥60s → 0).
- **actionCost** (subtracted every time, right or wrong):

  | Action | Cost |
  |--------|------|
  | Ignore | 0 |
  | Filter Alarms · Escalate | 5 |
  | Rebalance Traffic · Rollback Config · Increase Transmit Power · Disable Automation | 10 |
  | Rollback Software | 15 |
  | Restart Cell | 20 |

> Worked example on a Cell Overload incident: Rebalance Traffic right away = `+100 +50 −10 = **+140**`.
> Restart Cell (a trap) = `−75 −20 = **−95**`. Rollback Config (ineffective) = `**−10**`.

## The answer key (incident → correct action)

The incident title you see maps to exactly one correct action:

| Incident (what you see) | ✅ Correct action | ❌ Traps (big loss) |
|-------------------------|------------------|--------------------|
| **Cell Overload** | Rebalance Traffic | Restart Cell, Ignore |
| **Config Drift** | Rollback Config | Restart Cell, Ignore |
| **Transport Link Fault** | Escalate | Restart Cell, Ignore |
| **Alarm Storm** | Filter Alarms | Restart Cell, Ignore |
| **Neighbour Interference** | Increase Transmit Power | Restart Cell, Ignore |
| **Software Upgrade Fault** | Rollback Software | Increase Transmit Power, Ignore |
| **Rogue Automation** | Disable Automation | Rebalance Traffic, Ignore |
| **Suspected False Alarm** | **Ignore** | Restart Cell |

### Key gotchas
- **Ignore is usually a trap** — except on a **Suspected False Alarm**, where Ignore is the
  *only* correct move. Same action, opposite outcome: that's the design in action.
- **Restart Cell is almost always wrong** (it's the tempting "turn it off and on again"
  trap) and it's the most expensive action.
- Any action that's neither correct nor a trap just wastes its cost and leaves the incident
  **open**, so you can still fix it with the right one.

## Quick decision flow
1. Read the incident **title** → look it up above → submit that action **fast** (time bonus).
2. Unsure? A cheap ineffective action (e.g. Escalate, cost 5) only loses 5 and keeps the
   incident open — but a trap loses ~95. When in doubt, don't Restart and don't Ignore.

---

*Mapping source of truth: the backend engine `RootCause` → correct/trap actions
(`backend/.../engine/RootCause.java`) and the simulator's incident archetypes
(`simulator/simulator.py`). If those change, update this sheet.*

# Learning Progression — Design (pre-implementation)

Turn the game into an onboarding curriculum: players start on a guided, click-driven version and
are weaned onto the command line as they learn, with difficulty ramping up as their skill grows.
A "field manual" fills in as they learn, to refer back to.

> Status: design only — agree the open decisions before any code. Builds on the console
> (`feat/console`). This is a sizeable system (player progression + teaching modals + arg-aware
> console + manual UI + adaptive difficulty), so it should land in phases.

---

## The vision (restated)

1. **Start guided.** No console. Low-severity incidents, spawning slowly. The player figures out the
   right **action** and clicks *Apply*.
2. **Teach on use.** When they apply an action, a **modal** shows how a real engineer would do it on
   the CLI — the command to *diagnose* the fault and the command to *apply* the fix. That action is
   now "learned".
3. **Wean off the buttons.** Once learned, that action's **button is disabled** and the **console is
   enabled** for it — the player must type the command from now on.
4. **Ramp up.** As the player learns more actions/commands and more buttons switch off, incidents
   **spawn faster** and get nastier.
5. **Field manual.** The Actions page becomes a **manual that populates as they learn** — actions,
   the Linux/telecom commands, and which fault each addresses — so they can look things up.
6. **Don't spoon-feed commands.** `help` should not print the full string (`kubectl logs
   deploy/traffic-steering`). The player learns the **command and its arguments** (via `man`) and
   assembles it themselves.

---

## Proposed model

### Player progression (persisted per user)
A new `user_skills` record per `AppUser`: the set of **learned actions** and **learned diagnostics**,
and a derived **tier** (Trainee → Operator → Engineer) from how much is learned. Carried **across
matches** (it's onboarding — learning should stick).

### The teach-once-then-CLI loop
- Apply an action via the button (guided) → **teaching modal**: "Rebalance Traffic — diagnose with
  `traceroute …`, fix with `rrmctl rebalance …`" → mark the action learned.
- Learned action → button disabled; the **console** is the only way to apply it (you type the fix
  command). Same idea for diagnostics: button first (with a lesson), console-only once learned.

### Fix-by-command (actions get authentic CLI too)
Each of the 9 actions gets a real command, so the console handles **remediation** as well as
diagnosis once learned. Drafts:

| Action | Command |
|---|---|
| Rebalance traffic | `rrmctl rebalance --cell o-ru-07` |
| Restart cell | `kubectl rollout restart deploy/o-du` |
| Roll back config | `netconf edit-config --rollback` |
| Roll back software | `kubectl rollout undo deploy/o-du` |
| Increase Tx power | `rrmctl set-power --cell o-ru-07 --delta +3` |
| Filter alarms | `fmcli suppress --correlated` |
| Disable automation | `ricctl xapp disable traffic-steering` |
| Escalate | `ticket open --team transport --priority p1` |
| Ignore | `fmcli ack --no-action` |

### Progressive disclosure (`help` / `man`)
- `help` lists only **command names you've unlocked** (not the full invocation).
- `man <cmd>` teaches what the command does and **its arguments** — the player assembles the full
  line. → the console must become **argument-aware** (validate args, not just the command prefix),
  which is the deepest part of this work and the real CLI-teaching payoff.

### Field manual (repurpose the Actions page)
A reference that fills in as the player learns: each learned fault → its symptom group, the
diagnostic command(s), and the fix command. Empty at the start; grows into a personal cheat-sheet.

### Adaptive difficulty
Spawn rate + severity scale with the player's tier (more learned → faster, harsher).

---

## The architectural fork (decide this first)

Today **both players in a match get the same mirrored cells + incidents** — that's what makes the
head-to-head score comparable. But "incidents spawn faster *as the player learns*" is **per-player**.
Those two pull in opposite directions:

- **Option A — Interface adapts per player; world stays shared.** Both players still get identical
  incidents (fair head-to-head). What differs per player is the *crutches*: buttons vs console, and
  the teaching modals. Spawn rate ramps by **match time / session difficulty**, not per-player skill.
  *Pro:* keeps multiplayer fair and is the smallest change. *Con:* "spawn faster as you learn" only
  partly holds (it ramps by match, not by you).
- **Option B — Fully personal progression (incl. spawn rate).** Each player's difficulty tracks their
  own skill, so the two boards diverge. *Pro:* matches the vision exactly. *Con:* head-to-head scores
  are no longer apples-to-apples; needs a different "who won" model (e.g. relative-to-your-tier).
- **Option C — A dedicated single-player Training mode** for the curriculum (persistent progression,
  adaptive difficulty, manual), and keep the existing head-to-head as a separate "match" mode that
  uses whatever you've learned. *Pro:* clean — learning is solo and fair; competition is separate.
  *Con:* two modes to build/maintain.

**Recommendation: C (training mode) for the curriculum, with A's "interface adapts" in matches.**
i.e. the learning journey (modals, unlocks, adaptive difficulty, manual) lives in a solo Training
mode; in head-to-head matches the world stays shared and your learned-ness just decides whether you
see buttons or must use the console. This delivers the teaching goal without breaking competitive fairness.

---

## Suggested phasing (each its own branch, tested, incremental)

1. **Progression persistence** — `user_skills` (learned actions/diagnostics, tier); endpoints to read/update; tests.
2. **Teach-once modal + button-disable** — on first use of an action, show the CLI lesson and mark it learned; disable the learned button; surface learned-state to the UI.
3. **Fix-by-command in the console** — actions get commands; console applies remediation (not just diagnosis) for learned actions.
4. **Argument-aware console + `man`** — validate args; `man` teaches them; `help` lists unlocked names only.
5. **Field manual page** — repurpose Actions into the growing reference.
6. **Adaptive difficulty** — spawn/severity by tier (Training mode).

---

## Open decisions (need your call)

1. **The fork above — A, B, or C?** (Recommend C + A.)
2. **Progression persistence** — per-user across matches (recommended), or per-match only?
3. **Scope of "learned" granularity** — per action (9) + per diagnostic (6)? Or coarser tiers?
4. **Argument-aware console** — do we go all the way (player must get the args right, `man` teaches
   them) or keep the current "command recognised, args ignored" and teach more lightly? (All-the-way
   is the real skill payoff but the most work and the hardest for players.)
5. **Build order** — start with phase 1 (progression persistence) and go in order, or prototype the
   teach-modal (phase 2) first to feel the UX?

---

# Phase 6 — Training mode + adaptive difficulty (detailed design)

> Phases 1–5 are built (progression, learn-on-use + console fixes, arg-aware console, field manual).
> This is the last piece: a **solo Training mode** where difficulty scales with the player's tier,
> per the agreed fork (C + A). Head-to-head matches are unchanged.

### What it is
A single-player session for the curriculum. No opponent, so adaptive difficulty is fair. It starts
gentle for a Trainee and gets busier/harsher as the player's tier rises — "starts slow, speeds up as
you learn." Competitive head-to-head stays exactly as it is today.

### Model
- **`GameSession.mode`** — `HEAD_TO_HEAD` (default, today's behaviour) or `TRAINING`.
- **Create training** — `POST /api/sessions/training {userId, durationSeconds}`: makes a `TRAINING`
  session, adds the one player, and **activates it immediately** (no second player, no ready-check).
- **Difficulty from tier** — at creation the session's `difficulty` is set from the player's current
  tier: **Trainee→EASY (3 cells), Operator→MEDIUM (6), Engineer→HARD (9)**. The simulator already
  ramps incident volume + severity by difficulty + match time, so this reuses what we have — more
  cells = more incidents, HARD = HIGH-severity from the start.
- **Leaving / ending** — leaving just ends *your* training (no forfeit-vs-opponent); it ends on the
  timer like any session. No competitive high-score entry — training is practice (the end screen
  shows your score + tier).

### Simulator
One small change: it currently only seeds sessions that have **2** players (`len(players) < 2: return`).
Relax that to **≥1** so it also seeds/ticks training sessions. Everything else (tower count, refill,
severity pool) already keys off `difficulty`, so no tier coupling is needed in the simulator.

### Why difficulty-at-creation (not live per-tick)
The simplest faithful model: each training session is sized to your **current** tier, and you level
up **across** sessions (learn more → next training is harder). A *live* within-session ramp (re-reading
your tier every tick as you learn mid-match) is finer-grained but needs the simulator to query each
player's tier every tick — more coupling for little gain. Recommend creation-time for the MVP; live
ramp can come later if it's wanted.

### Touch points
- Backend: `GameSession.mode`; `SessionService.createTrainingSession`; `SessionResponse.mode`;
  `/api/sessions/training`. Leave/expiry already handle a session ending.
- Simulator: relax the 2-player guard to ≥1.
- Frontend: a **Training** entry in the lobby → create a training session → drop into the solo game;
  a solo-aware end screen (no opponent).
- Tests: backend (training activates immediately with one player at the tier's difficulty; leaving
  ends it) + controller + integration; simulator (n/a — guard isn't unit-covered); frontend (lobby
  training start, solo end screen).

### Build order (each its own commit, tested)
1. Backend: `mode` + `createTrainingSession` + endpoint + `SessionResponse.mode` + tests.
2. Simulator: seed sessions with ≥1 player.
3. Frontend: lobby Training start + solo end-screen handling + tests.
4. Live verify: start training as Trainee (EASY) vs after reaching Operator (MEDIUM).

### Open decisions
1. **Difficulty granularity** — creation-time from tier (recommended), or live per-tick ramp?
2. **Training scoring** — personal score only, no competitive high-score entry (recommended), or a
   separate training leaderboard / personal best?
3. **Tier→difficulty mapping** — Trainee/Operator/Engineer → EASY/MEDIUM/HARD as above, or a
   different curve?

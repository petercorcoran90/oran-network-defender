# Diagnostic Console — Design (pre-implementation)

A simulated terminal in the incident view. Instead of clicking one-shot "diagnostic" buttons that
return a verdict, the player types realistic commands and **reads the output to deduce the cause**.
Teaches Linux/ops muscle memory *and* diagnostic reasoning at once.

> Status: design only — agree this before any code. Builds on the investigation feature
> (`SymptomGroup` / `DiagnosticEvaluator` / candidate board) already on `feat/investigation`.

---

## Goals / non-goals

**Goals**
- Replace the one-click "confirmed / ruled out" tag with **output the player interprets** — real thinking, hard to memorise.
- Teach actual skills: reading counters, logs, config diffs, KPIs; commands like `ping`, `traceroute`, `grep`, `journalctl`.
- Keep it **fair and learnable** for non-CLI players (help, autocomplete, a visible command list).
- Reuse the existing engine: the hidden root cause still decides correctness; commands just surface evidence.

**Non-goals**
- It is **not a real shell**. Nothing is executed. It's an emulator that maps recognised commands to generated text. (Security: see below.)
- Not a full Linux course — a curated, telecom-flavoured command set, not bash.

---

## Player experience

In an open incident, the **Investigation** panel becomes a terminal:

```
o-ran-defender:cell-07$ help
Diagnostics:  ping  traceroute  ethtool  netconf-diff  show-version
              journalctl  ric-xapps  show-alarms  iw-scan
Utility:      help  man <cmd>  clear
You have 2 investigation commands left for this incident. Each costs 15 pts.

o-ran-defender:cell-07$ traceroute o-ru-07
traceroute to o-ru-07 (10.42.7.7), 30 hops max
 1  o-du-gw         0.21 ms
 2  fronthaul-sw1   0.43 ms   12% loss
 3  o-ru-07         2.8 ms    58% loss        <-- packet loss on the fronthaul
1 command left.

o-ran-defender:cell-07$ _
```

The player reads "58% loss on the fronthaul hop" and concludes **transport link fault** → applies
*Escalate*. The candidate board (causes + their fixes) stays beside the terminal as a reference;
the remediation buttons are unchanged.

Diagnostic commands count against the **budget** (2 for the 4-candidate group, 1 for the others)
and cost points; **utility** commands (`help`, `man`, `clear`) are free and unlimited.

---

## Command set (MVP)

Each diagnostic command maps to one of the existing six hypotheses (`DiagnosticType`); its output is
**generated from the hidden root cause** so that the command relevant to the real cause shows an
anomaly and the others read clean.

| Command | Teaches | Hypothesis it tests |
|--------|---------|---------------------|
| `ping <node>` / `traceroute o-ru` | reachability, per-hop loss/latency | transport link fault |
| `ethtool <if>` | interface error/CRC/drop counters | transport link fault |
| `netconf-diff` (`get-config | diff baseline`) | config drift / CM audit | neighbour config change |
| `show-version` / `dpkg -l` | component versions, upgrade window | software-upgrade fault |
| `journalctl <unit>` | reading service logs | software fault / rogue automation |
| `ric-xapps` (xApp/rApp action log) | closed-loop automation audit | rogue automation |
| `iw-scan` / `show-interference` | PCI/RSRP/SINR, neighbour cells | neighbour interference |
| `show-alarms` (FM correlation) | alarm root-cause vs noise | alarm storm vs false alarm |
| `help`, `man <cmd>`, `clear` | discoverability | — (free) |

Deeper phase (optional): virtual log files you `cat` and `grep` (e.g. `grep ERROR /var/log/o-du.log`),
so reading/filtering logs is its own skill.

---

## Sample outputs (how output reveals evidence, no verdict)

**Transport link fault** — `traceroute` shows loss; the others read clean:
```
$ traceroute o-ru-07          → 58% loss at fronthaul-sw1 hop
$ ethtool fronthaul0          → rx_crc_errors: 47991, rx_dropped: 1204
$ netconf-diff                → No differences.
$ show-version                → O-DU 3.2.1 (installed 41 days ago)
```

**Neighbour config change** — the diff is the tell:
```
$ netconf-diff
- pci: 211
+ pci: 207
  neighbor-list changed 18 min ago by cm-batch-job
$ traceroute o-ru-07          → all hops 0% loss
```

**Software-upgrade fault**:
```
$ show-version   → o-du-l1 4.0.0 (installed 22 min ago); previous 3.9.4
$ journalctl o-du → ERROR l1-phy: incompatible firmware ABI after upgrade 4.0.0
```

**Congestion group — the ambiguous one (overload vs rogue automation):** both show high load, so the
*automation log* is what separates them:
```
$ show-load   → PRB utilisation 96%, active UEs 412     (same for both causes)
$ ric-xapps   → rogue automation:  traffic-steering-v2 applied 328 handovers in 5 min
              → plain overload:    traffic-steering-v2 idle, no recent actions
```

**Alarms group (storm vs false alarm):**
```
$ show-alarms → storm:  40 alarms, root=LinkDown, correlation 0.92  (real fault masked)
              → noise:  1 alarm flapping x31, KPIs nominal, correlation 0.04  (ignore)
```

The player never sees "confirmed" — they read the numbers and decide. (The candidate board can still
mark a cause ruled-out/confirmed underneath, as a learnable safety net — or we hide it for a hard mode.)

---

## Architecture

The command→output logic **must be server-side** — output is derived from the hidden root cause, which
is never sent to the client. The frontend is a dumb terminal.

```
POST /api/sessions/{id}/incidents/{incId}/console   { playerId, command }
  -> backend: parse (whitelist) → map command to a DiagnosticType (or utility)
            → DiagnosticEvaluator.diagnose(rootCause, type)  (existing truth)
            → ConsoleRenderer turns (command, evidence, cell metrics) into realistic text
            → record a DiagnosticRun (reuse budget + point cost); utility cmds are free
  <- { command, recognised, output, costApplied, commandsLeft }
```

- **Reuse** `DiagnosticType` / `DiagnosticEvaluator` / `DiagnosticRun` / budget / cost — the console is a
  richer *presentation* of the same evidence engine, not a new ruleset.
- New piece: a pure **`ConsoleRenderer`** (command + EvidenceResult + cell → text). Pure and unit-testable.
- New piece: a **command parser/whitelist** (string → known command + arg), backend.
- `help`/`man`/`clear` and unknown commands return text without touching the budget.

---

## Scoring, budget, accessibility

- Diagnostic commands reuse the **budget** (can't run everything) and the **−15 pts** cost. Utility commands free.
- Unknown command → `command not found: xyz — type 'help'` (teaches, never punishes).
- **Autocomplete (Tab)**, **command history (↑/↓)**, `help` always lists the available commands and the budget left.
- Keep the **candidate board** (causes + fixes) visible as a reference so non-CLI players aren't lost; the
  remediation buttons are unchanged. (Optional "hard mode" hides the board.)

---

## Security (must hold)

- **Emulated only — nothing is executed.** No shell, no `eval`, no filesystem/process access. Recognised
  commands map to generated strings; everything else returns a friendly "not found".
- Server-side **whitelist + input validation** (max length, allowed characters); the raw string is never
  passed to any interpreter. → no command injection / RCE despite looking like a terminal.
- Same ownership/state guards as the existing diagnostics (your own OPEN incident, ACTIVE session).
- Document this explicitly in `SECURITY.md`.

---

## Testing

- `ConsoleRenderer` (pure): for every (command × root cause), the output contains the right anomaly for the
  true cause and reads clean otherwise; deterministic.
- Parser: whitelist accepts known commands, rejects/normalises the rest safely (incl. junk/oversized input).
- Service/integration (Testcontainers): console command records a `DiagnosticRun`, respects budget + cost,
  guards ownership; utility commands are free.
- Controller (`@WebMvcTest`): endpoint shape + validation.
- Frontend (Vitest): terminal renders output, sends commands, shows budget, blocks when spent.
- Security: oversized/garbage input handled; no root cause string ever in the response.

---

## Build plan (incremental, one commit each)

1. **`ConsoleRenderer` + parser (pure engine)** + unit tests — the teachable core.
2. **API**: `POST …/console` reusing budget/cost; record `DiagnosticRun`; integration + controller tests.
3. **Frontend terminal**: input, history, autocomplete, output rendering, budget display; Vitest.
4. **Polish**: `man` pages, sample log files for `grep`/`cat` (optional deeper layer), docs + SECURITY.md note.

---

## Open decisions (need the team's call)

1. **Ambition** — MVP (fixed ~8 commands → realistic canned/generated output) first, then maybe add the
   `cat`/`grep` log-file layer? (Recommended: MVP first.)
2. **Keep the candidate board** as a reference/safety net, or hide it for a harder, console-only mode?
3. **Replace or augment** — drop the one-click diagnostic buttons entirely, or keep them as an "easy mode"
   alongside the console?
4. **Command naming** — friendly hyphenated (`netconf-diff`, `show-alarms`) vs more authentic
   (`netconf get-config | diff`, `fmcli alarms`). More authentic = better teaching, slightly harder.

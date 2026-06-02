#!/usr/bin/env python3
"""
O-RAN Network Defender — network simulator.

A standalone service (own container). It does NOT serve requests; it polls the Game API
for ACTIVE sessions and POSTs network state in via the internal ingest endpoints:

    GET  /api/sessions                         -> find ACTIVE sessions
    GET  /api/sessions/{id}/players            -> the two players
    POST /api/internal/sessions/{id}/cells     -> seed a player's mirrored cells
    POST /api/internal/sessions/{id}/incidents -> seed an incident (with hidden root cause)
    POST /api/internal/cells/{id}/metrics      -> drift a cell's metrics over time

Both players in a session get the SAME cells + incidents (seeded from the session id, so a
match is deterministic/repeatable). After seeding, the simulator drifts healthy cells and
occasionally injects a new incident — the "living network".

Stdlib only (urllib) — no third-party dependencies.
"""

import json
import os
import random
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

BACKEND = os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/")
TOKEN = os.environ.get("SIM_INGEST_TOKEN", "")
POLL_SECONDS = float(os.environ.get("POLL_SECONDS", "2"))
CELL_COUNT = int(os.environ.get("CELL_COUNT", "6"))
# Difficulty (set on the session at creation) -> number of towers.
DIFFICULTY_CELLS = {"EASY": 3, "MEDIUM": 6, "HARD": 9}
DRIFT_EVERY = int(os.environ.get("DRIFT_EVERY_TICKS", "2"))
# A single tower can hold this many simultaneous open incidents (lets the count exceed the
# number of towers, so the board can stay busy on hard/long games).
PER_CELL_MAX = int(os.environ.get("PER_CELL_MAX", "2"))
INITIAL_INCIDENTS = int(os.environ.get("INITIAL_INCIDENTS", "2"))
# Up to this many new incidents per tick while the board is below its target load — keeps a
# skilled player who clears incidents quickly from running out of things to do.
REFILL_PER_TICK = int(os.environ.get("REFILL_PER_TICK", "3"))

# Each incident PRESENTS as an ambiguous symptom group: several root causes share the exact same
# visible card (label / severity / description / symptom metrics), so a player can't read the fix
# off the symptom and must investigate (run diagnostics) to tell them apart. The hidden root cause
# — one of the group's `causes` — still decides correctness server-side. The group membership MUST
# match the backend SymptomGroup enum.
SYMPTOM_GROUPS = {
    "CONGESTION": {
        "label": "Congestion",
        "severity": "HIGH",
        "health": "CRITICAL",
        "config": "STABLE",
        "description": "User load and latency are elevated on this cell. More than one fault could "
                       "explain it — investigate before choosing a remediation.",
        "metrics": {"userLoad": 92, "latency": 160, "signalQuality": 80},
        "causes": ["CELL_OVERLOAD", "ROGUE_AUTOMATION"],
    },
    "SERVICE_DEGRADATION": {
        "label": "Service degradation",
        "severity": "HIGH",
        "health": "CRITICAL",
        "config": "STABLE",
        "description": "Raised latency and packet loss with patchy signal. The cause is unclear — "
                       "investigate to confirm it before acting.",
        "metrics": {"latency": 135, "packetLoss": 16, "signalQuality": 66},
        "causes": ["TRANSPORT_LINK_FAULT", "NEIGHBOUR_CONFIG_CHANGE",
                   "SOFTWARE_UPGRADE_FAULT", "NEIGHBOUR_INTERFERENCE"],
    },
    "ALARMS": {
        "label": "Alarm activity",
        "severity": "MEDIUM",
        "health": "WARNING",
        "config": "STABLE",
        "description": "A burst of alarms is firing. It may be masking a real fault, or it may be "
                       "noise — investigate before deciding what to do.",
        "metrics": {"alarmCount": 10, "latency": 60},
        "causes": ["ALARM_STORM", "FALSE_ALARM"],
    },
}

# root cause -> its symptom group key
ROOT_TO_GROUP = {rc: g for g, spec in SYMPTOM_GROUPS.items() for rc in spec["causes"]}
ALL_KEYS = list(ROOT_TO_GROUP)

# Difficulty ramp by ambiguity: the 2-candidate groups open the match; the harder 4-candidate
# SERVICE_DEGRADATION group joins later.
EARLY_GROUPS = ["CONGESTION", "ALARMS"]
EARLY_KEYS = [rc for g in EARLY_GROUPS for rc in SYMPTOM_GROUPS[g]["causes"]]


def present(root_cause):
    """The ambiguous card a root cause presents as — shared by every cause in its group."""
    return SYMPTOM_GROUPS[ROOT_TO_GROUP[root_cause]]


def severity_pool(difficulty, frac):
    """Which root causes can spawn. HARD gets HIGH-severity incidents from the start;
    MEDIUM brings them in past the midpoint; EASY stays low/medium."""
    if difficulty == "EASY":
        return EARLY_KEYS
    if difficulty == "HARD":
        return ALL_KEYS
    return EARLY_KEYS if frac < 0.4 else ALL_KEYS

# in-memory state per session: { cells: {name: metrics}, players: {pid: {name: cellId}},
#                                incident_cells: set(name), rng, tick }
SESSIONS = {}


def _request(method, path, body=None):
    url = BACKEND + path
    data = json.dumps(body).encode() if body is not None else None
    headers = {"Content-Type": "application/json"}
    if TOKEN:
        headers["X-Internal-Token"] = TOKEN
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=10) as resp:
        text = resp.read().decode()
        return json.loads(text) if text else None


def get(path):
    return _request("GET", path)


def post(path, body):
    return _request("POST", path, body)


def healthy_metrics():
    return {"signalQuality": 95.0, "userLoad": 30.0, "latency": 25.0, "packetLoss": 1.0,
            "alarmCount": 0, "energyUsage": 45.0, "healthStatus": "GOOD", "configStatus": "STABLE"}


def apply_symptom(metrics, root_cause):
    spec = present(root_cause)
    metrics.update(spec["metrics"])
    metrics["healthStatus"] = spec["health"]
    metrics["configStatus"] = spec.get("config", "STABLE")


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def _parse_ts(ts):
    return datetime.fromisoformat(ts.replace("Z", "+00:00"))


def elapsed_fraction(session):
    """How far through the match we are, 0.0 -> 1.0, from started_at/ended_at."""
    try:
        start = _parse_ts(session["startedAt"])
        end = _parse_ts(session["endedAt"])
        total = (end - start).total_seconds()
        if total <= 0:
            return 0.0
        return clamp((datetime.now(timezone.utc) - start).total_seconds() / total, 0.0, 1.0)
    except Exception:
        return 0.0


def derive_health(m):
    if m["userLoad"] > 85 or m["latency"] > 150 or m["packetLoss"] > 15:
        return "CRITICAL"
    if m["userLoad"] > 65 or m["latency"] > 90 or m["signalQuality"] < 70:
        return "WARNING"
    return "GOOD"


def drift(metrics, rng):
    # Gentle wiggle for a HEALTHY (incident-free) cell. Stays within healthy bounds so a cell
    # with no incident never drifts into amber/red — degradation only ever comes from incidents.
    metrics["userLoad"] = clamp(metrics["userLoad"] + rng.uniform(-5, 5), 10, 55)
    metrics["latency"] = clamp(metrics["latency"] + rng.uniform(-5, 5), 10, 60)
    metrics["signalQuality"] = clamp(metrics["signalQuality"] + rng.uniform(-3, 3), 82, 100)
    metrics["packetLoss"] = clamp(metrics.get("packetLoss", 1.0) + rng.uniform(-1, 1), 0, 5)
    metrics["energyUsage"] = clamp(metrics["energyUsage"] + rng.uniform(-2, 2), 30, 70)
    metrics["healthStatus"] = "GOOD"
    metrics["configStatus"] = "STABLE"


def cell_spec(name, m):
    return {"cellName": name, "signalQuality": m["signalQuality"], "userLoad": m["userLoad"],
            "latency": m["latency"], "packetLoss": m["packetLoss"], "alarmCount": int(m["alarmCount"]),
            "energyUsage": m["energyUsage"], "healthStatus": m["healthStatus"],
            "configStatus": m.get("configStatus", "STABLE")}


def build_plan(session_id, cell_count):
    """Deterministic per session: towers + a few easy incidents to open the match."""
    rng = random.Random(session_id)
    names = ["Cell-%02d" % (i + 1) for i in range(cell_count)]
    n_inc = min(INITIAL_INCIDENTS, max(1, cell_count - 1))
    incident_idx = rng.sample(range(cell_count), k=n_inc)
    incidents = [(i, rng.choice(EARLY_KEYS)) for i in incident_idx]
    return names, incidents


def seed_session(session):
    sid = session["id"]
    players = get("/api/sessions/%d/players" % sid)
    if len(players) < 2:
        return
    cell_count = DIFFICULTY_CELLS.get(session.get("difficulty", "MEDIUM"), CELL_COUNT)
    names, incidents = build_plan(sid, cell_count)
    canonical = {n: healthy_metrics() for n in names}
    for idx, rc in incidents:
        apply_symptom(canonical[names[idx]], rc)
    state = {"cells": canonical, "players": {}, "rng": random.Random(sid * 7919), "tick": 0}
    for p in players:
        pid = p["id"]
        created = post("/api/internal/sessions/%d/cells" % sid,
                       {"playerId": pid, "cells": [cell_spec(n, canonical[n]) for n in names]})
        name_to_id = {c["cellName"]: c["id"] for c in created}
        state["players"][pid] = name_to_id
        for idx, rc in incidents:
            name = names[idx]
            post("/api/internal/sessions/%d/incidents" % sid,
                 {"playerId": pid, "cellId": name_to_id[name], "incidentType": present(rc)["label"],
                  "severity": present(rc)["severity"], "rootCause": rc, "description": present(rc)["description"]})
    SESSIONS[sid] = state
    print("[sim] seeded session %d (%d cells, %d incidents per player)" % (sid, len(names), len(incidents)), flush=True)


def register_existing(sid):
    """Simulator restarted mid-match: rebuild state from what's already in the DB."""
    cells = get("/api/sessions/%d/cells" % sid)
    if not cells:
        return False
    players = {}
    canonical = {}
    for c in cells:
        players.setdefault(c["playerId"], {})[c["cellName"]] = c["id"]
        canonical[c["cellName"]] = {k: c[k] for k in ("signalQuality", "userLoad", "latency",
                                    "packetLoss", "alarmCount", "energyUsage")}
        canonical[c["cellName"]]["healthStatus"] = c["healthStatus"]
        canonical[c["cellName"]]["configStatus"] = c["configStatus"]
    SESSIONS[sid] = {"cells": canonical, "players": players,
                     "rng": random.Random(sid * 7919), "tick": 0}
    print("[sim] re-registered existing session %d" % sid, flush=True)
    return True


def push_metrics(sid, state, name):
    m = state["cells"][name]
    for name_to_id in state["players"].values():
        cell_id = name_to_id.get(name)
        if cell_id is not None:
            post("/api/internal/cells/%d/metrics" % cell_id, cell_spec(name, m))


def open_state(sid, state):
    """Per-cell max open-incident count across both players, plus each player's total open
    load. Read live from the backend, so cells free up as incidents get resolved."""
    per_player = []
    for pid, name_to_id in state["players"].items():
        id_to_name = {cid: n for n, cid in name_to_id.items()}
        counts = {}
        for inc in (get("/api/sessions/%d/incidents?playerId=%d&status=OPEN" % (sid, pid)) or []):
            name = id_to_name.get(inc["cellId"])
            if name:
                counts[name] = counts.get(name, 0) + 1
        per_player.append(counts)
    merged = {n: max((c.get(n, 0) for c in per_player), default=0) for n in state["cells"]}
    load = max((sum(c.values()) for c in per_player), default=0)
    return merged, load


def tick_session(session):
    sid = session["id"]
    state = SESSIONS[sid]
    rng = state["rng"]
    state["tick"] += 1
    tick = state["tick"]
    frac = elapsed_fraction(session)
    merged, load = open_state(sid, state)

    if tick % DRIFT_EVERY == 0:
        for name in state["cells"]:
            if merged[name] > 0:
                continue  # keep incident evidence stable while a cell has an open incident
            m = state["cells"][name]
            if m["healthStatus"] != "GOOD" or m.get("configStatus", "STABLE") != "STABLE":
                # The incident on this cell has been cleared, so the cell is healthy again.
                # Reset to healthy instead of drifting stale symptom values back over the
                # backend's heal (a cell with no open incident should never sit red).
                state["cells"][name] = healthy_metrics()
            else:
                drift(m, rng)
            push_metrics(sid, state, name)

    # Difficulty + time ramp: keep the board topped up to a target that grows with tower
    # count and elapsed time (not bounded by tower count — a tower holds up to PER_CELL_MAX).
    # Refilling each tick means clearing incidents fast just brings more, instead of idling.
    difficulty = session.get("difficulty", "MEDIUM")
    cell_count = len(state["cells"])
    target = max(2, round(cell_count * (0.4 + 0.8 * frac)))
    pool = severity_pool(difficulty, frac)
    spawned = 0
    while load + spawned < target and spawned < REFILL_PER_TICK:
        candidates = [n for n in state["cells"] if merged[n] < PER_CELL_MAX]
        if not candidates:
            break
        fewest = min(merged[n] for n in candidates)
        name = rng.choice([n for n in candidates if merged[n] == fewest])
        rc = rng.choice(pool)
        apply_symptom(state["cells"][name], rc)
        # Create the incident BEFORE pushing the degraded metrics, so a reader never sees a
        # red cell with no incident on it (the evidence catches up a beat later, not before).
        for pid, name_to_id in state["players"].items():
            post("/api/internal/sessions/%d/incidents" % sid,
                 {"playerId": pid, "cellId": name_to_id[name], "incidentType": present(rc)["label"],
                  "severity": present(rc)["severity"], "rootCause": rc, "description": present(rc)["description"]})
        push_metrics(sid, state, name)
        merged[name] += 1
        spawned += 1
    if spawned:
        print("[sim] +%d incident(s) (session %d, frac=%.2f, load->%d/%d, diff=%s)"
              % (spawned, sid, frac, load + spawned, target, difficulty), flush=True)


def loop_once():
    sessions = get("/api/sessions") or []
    for s in sessions:
        if s["status"] != "ACTIVE":
            continue
        if elapsed_fraction(s) >= 1.0:
            continue  # match is over (timer elapsed) — leave it alone
        sid = s["id"]
        if sid in SESSIONS:
            tick_session(s)
        elif not register_existing(sid):
            seed_session(s)


def main():
    print("[sim] starting; backend=%s poll=%ss" % (BACKEND, POLL_SECONDS), flush=True)
    while True:
        try:
            loop_once()
        except urllib.error.HTTPError as e:
            print("[sim] HTTP %s on %s" % (e.code, e.url), flush=True)
        except Exception as e:  # noqa: BLE001 - keep the loop alive
            print("[sim] error: %r" % e, flush=True)
        time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    main()

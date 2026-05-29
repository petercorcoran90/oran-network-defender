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

BACKEND = os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/")
TOKEN = os.environ.get("SIM_INGEST_TOKEN", "")
POLL_SECONDS = float(os.environ.get("POLL_SECONDS", "3"))
CELL_COUNT = int(os.environ.get("CELL_COUNT", "6"))
DRIFT_EVERY = int(os.environ.get("DRIFT_EVERY_TICKS", "2"))
NEW_INCIDENT_EVERY = int(os.environ.get("NEW_INCIDENT_EVERY_TICKS", "8"))
MAX_INCIDENT_CELLS = int(os.environ.get("MAX_INCIDENT_CELLS", "5"))

# rootCause -> how it presents. rootCause values must match the backend RootCause enum.
ARCHETYPES = {
    "CELL_OVERLOAD":          {"type": "Cell Overload",          "severity": "HIGH",   "health": "CRITICAL", "metrics": {"userLoad": 95, "latency": 180}},
    "NEIGHBOUR_CONFIG_CHANGE":{"type": "Config Drift",           "severity": "MEDIUM", "health": "WARNING",  "metrics": {"packetLoss": 14, "latency": 70}},
    "TRANSPORT_LINK_FAULT":   {"type": "Transport Link Fault",   "severity": "HIGH",   "health": "CRITICAL", "metrics": {"packetLoss": 22, "latency": 140}},
    "ALARM_STORM":            {"type": "Alarm Storm",            "severity": "HIGH",   "health": "WARNING",  "metrics": {"alarmCount": 12}},
    "NEIGHBOUR_INTERFERENCE": {"type": "Neighbour Interference", "severity": "MEDIUM", "health": "WARNING",  "metrics": {"signalQuality": 55}},
    "SOFTWARE_UPGRADE_FAULT": {"type": "Software Upgrade Fault", "severity": "MEDIUM", "health": "WARNING",  "metrics": {"packetLoss": 9}},
    "ROGUE_AUTOMATION":       {"type": "Rogue Automation",       "severity": "MEDIUM", "health": "WARNING",  "metrics": {"userLoad": 80, "latency": 120}},
    "FALSE_ALARM":            {"type": "Suspected False Alarm",  "severity": "LOW",    "health": "GOOD",     "metrics": {"alarmCount": 1}},
}
DESCRIPTIONS = {
    "CELL_OVERLOAD": "User load and latency are climbing past safe thresholds on this cell.",
    "NEIGHBOUR_CONFIG_CHANGE": "Packet loss is rising after a neighbouring cell's configuration changed.",
    "TRANSPORT_LINK_FAULT": "Intermittent transport link is dropping packets and adding latency.",
    "ALARM_STORM": "A burst of alarms is masking the underlying fault on this cell.",
    "NEIGHBOUR_INTERFERENCE": "Signal quality has dropped — likely interference from an adjacent cell.",
    "SOFTWARE_UPGRADE_FAULT": "Packet loss appeared right after a software upgrade window.",
    "ROGUE_AUTOMATION": "An automation loop is making changes that keep degrading this cell.",
    "FALSE_ALARM": "An alert fired but this cell's metrics look healthy.",
}

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
    return {"signalQuality": 95.0, "userLoad": 30.0, "latency": 25.0,
            "packetLoss": 1.0, "alarmCount": 0, "energyUsage": 45.0, "healthStatus": "GOOD"}


def apply_symptom(metrics, root_cause):
    arche = ARCHETYPES[root_cause]
    metrics.update(arche["metrics"])
    metrics["healthStatus"] = arche["health"]


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def derive_health(m):
    if m["userLoad"] > 85 or m["latency"] > 150 or m["packetLoss"] > 15:
        return "CRITICAL"
    if m["userLoad"] > 65 or m["latency"] > 90 or m["signalQuality"] < 70:
        return "WARNING"
    return "GOOD"


def drift(metrics, rng):
    metrics["userLoad"] = clamp(metrics["userLoad"] + rng.uniform(-5, 5), 0, 100)
    metrics["latency"] = clamp(metrics["latency"] + rng.uniform(-6, 6), 5, 300)
    metrics["signalQuality"] = clamp(metrics["signalQuality"] + rng.uniform(-3, 3), 0, 100)
    metrics["energyUsage"] = clamp(metrics["energyUsage"] + rng.uniform(-2, 2), 0, 100)
    metrics["healthStatus"] = derive_health(metrics)


def cell_spec(name, m):
    return {"cellName": name, "signalQuality": m["signalQuality"], "userLoad": m["userLoad"],
            "latency": m["latency"], "packetLoss": m["packetLoss"], "alarmCount": int(m["alarmCount"]),
            "energyUsage": m["energyUsage"], "healthStatus": m["healthStatus"]}


def build_plan(session_id):
    """Deterministic per session: which cells start with which incident."""
    rng = random.Random(session_id)
    names = ["Cell-%02d" % (i + 1) for i in range(CELL_COUNT)]
    keys = list(ARCHETYPES)
    incident_idx = rng.sample(range(CELL_COUNT), k=min(4, CELL_COUNT))
    incidents = [(i, rng.choice(keys)) for i in incident_idx]
    return names, incidents


def seed_session(session):
    sid = session["id"]
    players = get("/api/sessions/%d/players" % sid)
    if len(players) < 2:
        return
    names, incidents = build_plan(sid)
    canonical = {n: healthy_metrics() for n in names}
    for idx, rc in incidents:
        apply_symptom(canonical[names[idx]], rc)
    state = {"cells": canonical, "players": {}, "incident_cells": {names[i] for i, _ in incidents},
             "rng": random.Random(sid * 7919), "tick": 0}
    for p in players:
        pid = p["id"]
        created = post("/api/internal/sessions/%d/cells" % sid,
                       {"playerId": pid, "cells": [cell_spec(n, canonical[n]) for n in names]})
        name_to_id = {c["cellName"]: c["id"] for c in created}
        state["players"][pid] = name_to_id
        for idx, rc in incidents:
            name = names[idx]
            post("/api/internal/sessions/%d/incidents" % sid,
                 {"playerId": pid, "cellId": name_to_id[name], "incidentType": ARCHETYPES[rc]["type"],
                  "severity": ARCHETYPES[rc]["severity"], "rootCause": rc, "description": DESCRIPTIONS[rc]})
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
    incidents = get("/api/sessions/%d/incidents" % sid)
    id_to_name = {c["id"]: c["cellName"] for c in cells}
    incident_cells = {id_to_name.get(i["cellId"]) for i in incidents if i["cellId"] in id_to_name}
    SESSIONS[sid] = {"cells": canonical, "players": players, "incident_cells": incident_cells,
                     "rng": random.Random(sid * 7919), "tick": 0}
    print("[sim] re-registered existing session %d" % sid, flush=True)
    return True


def push_metrics(sid, state, name):
    m = state["cells"][name]
    for name_to_id in state["players"].values():
        cell_id = name_to_id.get(name)
        if cell_id is not None:
            post("/api/internal/cells/%d/metrics" % cell_id, cell_spec(name, m))


def tick_session(sid):
    state = SESSIONS[sid]
    rng = state["rng"]
    state["tick"] += 1
    tick = state["tick"]

    if tick % DRIFT_EVERY == 0:
        for name in state["cells"]:
            if name in state["incident_cells"]:
                continue  # keep incident evidence stable until it's dealt with
            drift(state["cells"][name], rng)
            push_metrics(sid, state, name)

    if tick % NEW_INCIDENT_EVERY == 0 and len(state["incident_cells"]) < MAX_INCIDENT_CELLS:
        healthy = [n for n in state["cells"] if n not in state["incident_cells"]]
        if healthy:
            name = rng.choice(healthy)
            rc = rng.choice(list(ARCHETYPES))
            apply_symptom(state["cells"][name], rc)
            state["incident_cells"].add(name)
            push_metrics(sid, state, name)
            for pid, name_to_id in state["players"].items():
                post("/api/internal/sessions/%d/incidents" % sid,
                     {"playerId": pid, "cellId": name_to_id[name], "incidentType": ARCHETYPES[rc]["type"],
                      "severity": ARCHETYPES[rc]["severity"], "rootCause": rc, "description": DESCRIPTIONS[rc]})
            print("[sim] new incident on %s (session %d)" % (name, sid), flush=True)


def loop_once():
    sessions = get("/api/sessions") or []
    for s in sessions:
        if s["status"] != "ACTIVE":
            continue
        sid = s["id"]
        if sid in SESSIONS:
            tick_session(sid)
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

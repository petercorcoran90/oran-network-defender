"""
Tests for the network simulator's pure generation logic — no network.

Covers the brief's Python Testing requirements:
  * tests for the simulator / data-generation logic,
  * repeatable simulation output where appropriate (deterministic per session seed),
  * validation that generated incidents are valid (root cause / severity / health in range).

Run from this directory:  pytest  (CI: pip install pytest pytest-cov; pytest --cov=. --cov-report=xml)
"""

import random
from datetime import datetime, timedelta, timezone

import pytest

import simulator as sim

# The root causes the simulator emits MUST match the backend RootCause enum exactly.
BACKEND_ROOT_CAUSES = {
    "CELL_OVERLOAD", "NEIGHBOUR_CONFIG_CHANGE", "TRANSPORT_LINK_FAULT", "ALARM_STORM",
    "NEIGHBOUR_INTERFERENCE", "SOFTWARE_UPGRADE_FAULT", "ROGUE_AUTOMATION", "FALSE_ALARM",
}
SEVERITIES = {"LOW", "MEDIUM", "HIGH"}
HEALTHS = {"GOOD", "WARNING", "CRITICAL"}
CONFIGS = {"STABLE", "CHANGED", "DRIFT"}


@pytest.fixture(autouse=True)
def reset_simulator_state(monkeypatch):
    sim.SESSIONS.clear()
    monkeypatch.setattr(sim, "PER_CELL_MAX", 2)
    monkeypatch.setattr(sim, "REFILL_PER_TICK", 3)
    monkeypatch.setattr(sim, "DRIFT_EVERY", 2)
    yield
    sim.SESSIONS.clear()


# --- validity of the archetype catalogue (what every generated incident is built from) ---

def test_archetypes_match_backend_enums_and_are_well_formed():
    assert set(sim.ARCHETYPES) == BACKEND_ROOT_CAUSES
    for rc, arche in sim.ARCHETYPES.items():
        assert arche["severity"] in SEVERITIES
        assert arche["health"] in HEALTHS
        assert arche.get("config", "STABLE") in CONFIGS
        assert rc in sim.DESCRIPTIONS and sim.DESCRIPTIONS[rc]  # every root cause has a description


def test_early_pool_excludes_high_severity():
    assert all(sim.ARCHETYPES[k]["severity"] in ("LOW", "MEDIUM") for k in sim.EARLY_KEYS)
    assert set(sim.ALL_KEYS) == BACKEND_ROOT_CAUSES


# --- severity_pool: difficulty + time ramp ---

def test_severity_pool_by_difficulty():
    assert sim.severity_pool("EASY", 0.0) == sim.EARLY_KEYS
    assert sim.severity_pool("EASY", 0.9) == sim.EARLY_KEYS
    assert sim.severity_pool("HARD", 0.0) == sim.ALL_KEYS
    # MEDIUM brings in the full (incl. HIGH) set past the 0.4 midpoint.
    assert sim.severity_pool("MEDIUM", 0.39) == sim.EARLY_KEYS
    assert sim.severity_pool("MEDIUM", 0.40) == sim.ALL_KEYS


# --- build_plan: deterministic (repeatable) + valid ---

def test_build_plan_is_repeatable_for_the_same_session():
    # Same session id + cell count -> identical towers and incidents, every time.
    assert sim.build_plan(42, 6) == sim.build_plan(42, 6)


def test_build_plan_structure_is_valid():
    names, incidents = sim.build_plan(42, 6)
    assert names == ["Cell-%02d" % (i + 1) for i in range(6)]
    # INITIAL_INCIDENTS (2) capped at cell_count-1.
    assert len(incidents) == 2
    idxs = [i for i, _ in incidents]
    assert len(set(idxs)) == len(idxs)                 # distinct cells
    for idx, rc in incidents:
        assert 0 <= idx < 6                            # valid cell index
        assert rc in BACKEND_ROOT_CAUSES               # valid, known root cause
        assert rc in sim.EARLY_KEYS                    # the match opens with easy incidents


def test_build_plan_handles_a_single_cell():
    names, incidents = sim.build_plan(7, 1)
    assert names == ["Cell-01"]
    assert len(incidents) == 1
    assert incidents[0][0] == 0


# --- apply_symptom: every root cause yields a valid degraded cell ---

def test_apply_symptom_yields_valid_state_for_every_root_cause():
    for rc, arche in sim.ARCHETYPES.items():
        m = sim.healthy_metrics()
        sim.apply_symptom(m, rc)
        assert m["healthStatus"] in HEALTHS
        assert m["configStatus"] in CONFIGS
        # the archetype's signature metrics were applied
        for key, value in arche["metrics"].items():
            assert m[key] == value


# --- drift: repeatable, and an incident-free cell never drifts into amber/red ---

def test_drift_is_repeatable_for_a_fixed_seed():
    m1, m2 = sim.healthy_metrics(), sim.healthy_metrics()
    sim.drift(m1, random.Random(123))
    sim.drift(m2, random.Random(123))
    assert m1 == m2


def test_drift_keeps_a_healthy_cell_healthy():
    m = sim.healthy_metrics()
    rng = random.Random(7)
    for _ in range(200):
        sim.drift(m, rng)
        assert 10 <= m["userLoad"] <= 55
        assert 10 <= m["latency"] <= 60
        assert 82 <= m["signalQuality"] <= 100
        assert 0 <= m["packetLoss"] <= 5
        # independent of the flag drift sets, the metrics themselves stay in the GOOD band
        assert sim.derive_health(m) == "GOOD"


# --- derive_health thresholds ---

def test_derive_health_thresholds():
    healthy = sim.healthy_metrics()
    assert sim.derive_health(healthy) == "GOOD"

    overloaded = sim.healthy_metrics(); overloaded["userLoad"] = 95
    assert sim.derive_health(overloaded) == "CRITICAL"

    laggy = sim.healthy_metrics(); laggy["latency"] = 100
    assert sim.derive_health(laggy) == "WARNING"


# --- elapsed_fraction: bounded 0..1, robust to bad/edge input ---

def _iso(dt):
    return dt.astimezone(timezone.utc).isoformat()


def test_elapsed_fraction_midway_and_clamped():
    now = datetime.now(timezone.utc)
    midway = {"startedAt": _iso(now - timedelta(seconds=50)), "endedAt": _iso(now + timedelta(seconds=50))}
    assert 0.0 < sim.elapsed_fraction(midway) < 1.0

    finished = {"startedAt": _iso(now - timedelta(seconds=100)), "endedAt": _iso(now - timedelta(seconds=50))}
    assert sim.elapsed_fraction(finished) == 1.0


def test_elapsed_fraction_is_safe_on_bad_input():
    now = datetime.now(timezone.utc)
    zero_span = {"startedAt": _iso(now), "endedAt": _iso(now)}
    assert sim.elapsed_fraction(zero_span) == 0.0
    assert sim.elapsed_fraction({}) == 0.0  # missing keys -> 0.0, never raises


# --- HTTP request helper: JSON body + optional internal ingest token ---

def test_request_posts_json_and_internal_token(monkeypatch):
    captured = {}

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *_):
            return False

        def read(self):
            return b'{"ok": true}'

    def fake_urlopen(req, timeout):
        captured["url"] = req.full_url
        captured["method"] = req.get_method()
        captured["body"] = req.data
        captured["timeout"] = timeout
        captured["content_type"] = req.get_header("Content-type")
        captured["token"] = req.get_header("X-internal-token")
        return FakeResponse()

    monkeypatch.setattr(sim, "BACKEND", "http://backend")
    monkeypatch.setattr(sim, "TOKEN", "secret")
    monkeypatch.setattr(sim.urllib.request, "urlopen", fake_urlopen)

    assert sim.post("/api/internal/example", {"answer": 42}) == {"ok": True}

    assert captured == {
        "url": "http://backend/api/internal/example",
        "method": "POST",
        "body": b'{"answer": 42}',
        "timeout": 10,
        "content_type": "application/json",
        "token": "secret",
    }


# --- seed_session: mirrored cells/incidents, no network ---

def test_seed_session_skips_until_two_players(monkeypatch):
    posts = []
    monkeypatch.setattr(sim, "get", lambda path: [{"id": 10}])
    monkeypatch.setattr(sim, "post", lambda path, body: posts.append((path, body)))

    sim.seed_session({"id": 99, "difficulty": "MEDIUM"})

    assert posts == []
    assert 99 not in sim.SESSIONS


def test_seed_session_creates_mirrored_cells_and_incidents(monkeypatch):
    build_plan_args = []
    posts = []

    def fake_get(path):
        assert path == "/api/sessions/7/players"
        return [{"id": 10}, {"id": 20}]

    def fake_build_plan(session_id, cell_count):
        build_plan_args.append((session_id, cell_count))
        return ["Cell-01", "Cell-02", "Cell-03"], [
            (0, "FALSE_ALARM"),
            (2, "NEIGHBOUR_CONFIG_CHANGE"),
        ]

    def fake_post(path, body):
        posts.append((path, body))
        if path.endswith("/cells"):
            player_id = body["playerId"]
            return [
                {"cellName": cell["cellName"], "id": player_id * 100 + i}
                for i, cell in enumerate(body["cells"], start=1)
            ]
        return {"id": len(posts)}

    monkeypatch.setattr(sim, "get", fake_get)
    monkeypatch.setattr(sim, "build_plan", fake_build_plan)
    monkeypatch.setattr(sim, "post", fake_post)

    sim.seed_session({"id": 7, "difficulty": "EASY"})

    assert build_plan_args == [(7, 3)]
    cell_posts = [(p, b) for p, b in posts if p.endswith("/cells")]
    incident_posts = [(p, b) for p, b in posts if p.endswith("/incidents")]
    assert len(cell_posts) == 2
    assert len(incident_posts) == 4
    assert {body["playerId"] for _, body in cell_posts} == {10, 20}
    assert all(len(body["cells"]) == 3 for _, body in cell_posts)
    assert [body["rootCause"] for _, body in incident_posts] == [
        "FALSE_ALARM", "NEIGHBOUR_CONFIG_CHANGE",
        "FALSE_ALARM", "NEIGHBOUR_CONFIG_CHANGE",
    ]
    assert all(body["description"] == sim.DESCRIPTIONS[body["rootCause"]] for _, body in incident_posts)
    assert sim.SESSIONS[7]["players"] == {
        10: {"Cell-01": 1001, "Cell-02": 1002, "Cell-03": 1003},
        20: {"Cell-01": 2001, "Cell-02": 2002, "Cell-03": 2003},
    }


# --- register_existing/open_state: restart-safe live backend accounting ---

def test_register_existing_rebuilds_state_from_backend_cells(monkeypatch):
    backend_cells = [
        {"id": 11, "playerId": 1, "cellName": "Cell-A", "signalQuality": 88.0,
         "userLoad": 44.0, "latency": 33.0, "packetLoss": 2.0, "alarmCount": 1,
         "energyUsage": 50.0, "healthStatus": "WARNING", "configStatus": "CHANGED"},
        {"id": 21, "playerId": 2, "cellName": "Cell-A", "signalQuality": 88.0,
         "userLoad": 44.0, "latency": 33.0, "packetLoss": 2.0, "alarmCount": 1,
         "energyUsage": 50.0, "healthStatus": "WARNING", "configStatus": "CHANGED"},
    ]
    monkeypatch.setattr(sim, "get", lambda path: backend_cells)

    assert sim.register_existing(12) is True

    assert sim.SESSIONS[12]["players"] == {1: {"Cell-A": 11}, 2: {"Cell-A": 21}}
    assert sim.SESSIONS[12]["cells"]["Cell-A"]["healthStatus"] == "WARNING"
    assert sim.SESSIONS[12]["cells"]["Cell-A"]["configStatus"] == "CHANGED"


def test_register_existing_returns_false_when_backend_has_no_cells(monkeypatch):
    monkeypatch.setattr(sim, "get", lambda path: [])

    assert sim.register_existing(12) is False
    assert 12 not in sim.SESSIONS


def test_open_state_counts_max_per_cell_and_player_load(monkeypatch):
    state = {
        "cells": {"Cell-A": sim.healthy_metrics(), "Cell-B": sim.healthy_metrics()},
        "players": {1: {"Cell-A": 11, "Cell-B": 12}, 2: {"Cell-A": 21, "Cell-B": 22}},
    }

    def fake_get(path):
        if "playerId=1" in path:
            return [{"cellId": 11}, {"cellId": 11}, {"cellId": 999}]
        if "playerId=2" in path:
            return [{"cellId": 22}]
        raise AssertionError(path)

    monkeypatch.setattr(sim, "get", fake_get)

    merged, load = sim.open_state(5, state)

    assert merged == {"Cell-A": 2, "Cell-B": 1}
    assert load == 2


# --- tick_session: drift/reset/refill contract ---

def test_tick_session_resets_cleared_degraded_cells_and_skips_open_cells(monkeypatch):
    pushed = []
    state = {
        "cells": {"Cell-A": sim.healthy_metrics(), "Cell-B": sim.healthy_metrics()},
        "players": {1: {"Cell-A": 11, "Cell-B": 12}, 2: {"Cell-A": 21, "Cell-B": 22}},
        "rng": random.Random(1),
        "tick": 1,
    }
    state["cells"]["Cell-A"]["healthStatus"] = "WARNING"
    state["cells"]["Cell-A"]["configStatus"] = "DRIFT"
    sim.SESSIONS[30] = state

    monkeypatch.setattr(sim, "elapsed_fraction", lambda session: 0.0)
    monkeypatch.setattr(sim, "open_state", lambda sid, s: ({"Cell-A": 0, "Cell-B": 1}, 2))
    monkeypatch.setattr(sim, "post", lambda path, body: pushed.append((path, body)))

    sim.tick_session({"id": 30, "difficulty": "MEDIUM"})

    assert state["cells"]["Cell-A"]["healthStatus"] == "GOOD"
    assert state["cells"]["Cell-A"]["configStatus"] == "STABLE"
    assert [path for path, _ in pushed] == [
        "/api/internal/cells/11/metrics",
        "/api/internal/cells/21/metrics",
    ]
    assert all(body["healthStatus"] == "GOOD" for _, body in pushed)


def test_tick_session_refills_below_target_respecting_cap_and_order(monkeypatch):
    calls = []
    state = {
        "cells": {"Cell-A": sim.healthy_metrics(), "Cell-B": sim.healthy_metrics()},
        "players": {1: {"Cell-A": 11, "Cell-B": 12}, 2: {"Cell-A": 21, "Cell-B": 22}},
        "rng": random.Random(1),
        "tick": 0,
    }
    sim.SESSIONS[31] = state

    monkeypatch.setattr(sim, "elapsed_fraction", lambda session: 0.0)
    monkeypatch.setattr(sim, "open_state", lambda sid, s: ({"Cell-A": 0, "Cell-B": 2}, 0))
    monkeypatch.setattr(sim, "severity_pool", lambda difficulty, frac: ["FALSE_ALARM"])
    monkeypatch.setattr(sim, "REFILL_PER_TICK", 1)
    monkeypatch.setattr(sim, "post", lambda path, body: calls.append((path, body)))

    sim.tick_session({"id": 31, "difficulty": "EASY"})

    assert [path for path, _ in calls] == [
        "/api/internal/sessions/31/incidents",
        "/api/internal/sessions/31/incidents",
        "/api/internal/cells/11/metrics",
        "/api/internal/cells/21/metrics",
    ]
    assert all(body["rootCause"] == "FALSE_ALARM" for path, body in calls if path.endswith("/incidents"))
    assert all(body["cellId"] in (11, 21) for path, body in calls if path.endswith("/incidents"))


# --- loop_once: dispatches only active, unexpired sessions ---

def test_loop_once_routes_active_sessions(monkeypatch):
    ticked = []
    registered = []
    seeded = []
    sim.SESSIONS[3] = {"already": True}

    monkeypatch.setattr(sim, "get", lambda path: [
        {"id": 1, "status": "WAITING"},
        {"id": 2, "status": "ACTIVE"},
        {"id": 3, "status": "ACTIVE"},
        {"id": 4, "status": "ACTIVE"},
    ])
    monkeypatch.setattr(sim, "elapsed_fraction", lambda session: 1.0 if session["id"] == 2 else 0.0)
    monkeypatch.setattr(sim, "tick_session", lambda session: ticked.append(session["id"]))
    monkeypatch.setattr(sim, "register_existing", lambda sid: registered.append(sid) or False)
    monkeypatch.setattr(sim, "seed_session", lambda session: seeded.append(session["id"]))

    sim.loop_once()

    assert ticked == [3]
    assert registered == [4]
    assert seeded == [4]

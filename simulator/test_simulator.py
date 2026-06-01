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

import simulator as sim

# The root causes the simulator emits MUST match the backend RootCause enum exactly.
BACKEND_ROOT_CAUSES = {
    "CELL_OVERLOAD", "NEIGHBOUR_CONFIG_CHANGE", "TRANSPORT_LINK_FAULT", "ALARM_STORM",
    "NEIGHBOUR_INTERFERENCE", "SOFTWARE_UPGRADE_FAULT", "ROGUE_AUTOMATION", "FALSE_ALARM",
}
SEVERITIES = {"LOW", "MEDIUM", "HIGH"}
HEALTHS = {"GOOD", "WARNING", "CRITICAL"}
CONFIGS = {"STABLE", "CHANGED", "DRIFT"}


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

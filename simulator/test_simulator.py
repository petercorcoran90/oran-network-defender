"""
Tests for the network simulator's pure generation logic — no network.

Covers the brief's Python Testing requirements (generation logic, repeatable output, valid
incidents) and the investigation feature's key property: incidents in the same symptom group
present identically, so the card never reveals the hidden root cause.

Run from this directory:  pytest  (CI: pip install pytest pytest-cov; pytest --cov=. --cov-report=xml)
"""

import random
from datetime import datetime, timedelta, timezone

import simulator as sim

# Must match the backend RootCause enum and the backend SymptomGroup partition.
BACKEND_ROOT_CAUSES = {
    "CELL_OVERLOAD", "NEIGHBOUR_CONFIG_CHANGE", "TRANSPORT_LINK_FAULT", "ALARM_STORM",
    "NEIGHBOUR_INTERFERENCE", "SOFTWARE_UPGRADE_FAULT", "ROGUE_AUTOMATION", "FALSE_ALARM",
}
EXPECTED_GROUPS = {
    "CONGESTION": {"CELL_OVERLOAD", "ROGUE_AUTOMATION"},
    "SERVICE_DEGRADATION": {"TRANSPORT_LINK_FAULT", "NEIGHBOUR_CONFIG_CHANGE",
                            "SOFTWARE_UPGRADE_FAULT", "NEIGHBOUR_INTERFERENCE"},
    "ALARMS": {"ALARM_STORM", "FALSE_ALARM"},
}
SEVERITIES = {"LOW", "MEDIUM", "HIGH"}
HEALTHS = {"GOOD", "WARNING", "CRITICAL"}
CONFIGS = {"STABLE", "CHANGED", "DRIFT"}


# --- symptom groups partition the root causes, matching the backend ---

def test_groups_match_backend_partition():
    assert set(sim.ROOT_TO_GROUP) == BACKEND_ROOT_CAUSES
    actual = {g: set(spec["causes"]) for g, spec in sim.SYMPTOM_GROUPS.items()}
    assert actual == EXPECTED_GROUPS


def test_group_specs_are_well_formed():
    for spec in sim.SYMPTOM_GROUPS.values():
        assert spec["severity"] in SEVERITIES
        assert spec["health"] in HEALTHS
        assert spec.get("config", "STABLE") in CONFIGS
        assert spec["description"]
        assert spec["metrics"]
        assert len(spec["causes"]) >= 2          # genuinely ambiguous


def test_presentation_is_ambiguous():
    """Every cause in a group presents the SAME card — so the symptom can't reveal the fix."""
    for group, causes in EXPECTED_GROUPS.items():
        cards = {(sim.present(c)["label"], sim.present(c)["severity"], sim.present(c)["description"])
                 for c in causes}
        assert len(cards) == 1                   # all causes in the group look identical
        assert sim.present(next(iter(causes)))["label"] == sim.SYMPTOM_GROUPS[group]["label"]
    # …but the groups themselves are distinguishable from each other.
    labels = {spec["label"] for spec in sim.SYMPTOM_GROUPS.values()}
    assert len(labels) == len(sim.SYMPTOM_GROUPS)


# --- apply_symptom: every root cause yields a valid degraded cell, by group ---

def test_apply_symptom_yields_valid_state_for_every_root_cause():
    for rc in BACKEND_ROOT_CAUSES:
        m = sim.healthy_metrics()
        sim.apply_symptom(m, rc)
        assert m["healthStatus"] in HEALTHS
        assert m["configStatus"] in CONFIGS
        for key, value in sim.present(rc)["metrics"].items():
            assert m[key] == value


# --- severity_pool: difficulty + time ramp (now by group ambiguity) ---

def test_severity_pool_by_difficulty():
    assert sim.severity_pool("EASY", 0.0) == sim.EARLY_KEYS
    assert sim.severity_pool("EASY", 0.9) == sim.EARLY_KEYS
    assert sim.severity_pool("HARD", 0.0) == sim.ALL_KEYS
    assert sim.severity_pool("MEDIUM", 0.39) == sim.EARLY_KEYS
    assert sim.severity_pool("MEDIUM", 0.40) == sim.ALL_KEYS


def test_early_pool_is_the_two_candidate_groups_only():
    assert set(sim.EARLY_KEYS) == EXPECTED_GROUPS["CONGESTION"] | EXPECTED_GROUPS["ALARMS"]
    assert set(sim.ALL_KEYS) == BACKEND_ROOT_CAUSES


# --- build_plan: deterministic (repeatable) + valid ---

def test_build_plan_is_repeatable_for_the_same_session():
    assert sim.build_plan(42, 6) == sim.build_plan(42, 6)


def test_build_plan_structure_is_valid():
    names, incidents = sim.build_plan(42, 6)
    assert names == ["Cell-%02d" % (i + 1) for i in range(6)]
    assert len(incidents) == 2
    idxs = [i for i, _ in incidents]
    assert len(set(idxs)) == len(idxs)
    for idx, rc in incidents:
        assert 0 <= idx < 6
        assert rc in BACKEND_ROOT_CAUSES
        assert rc in sim.EARLY_KEYS


def test_build_plan_handles_a_single_cell():
    names, incidents = sim.build_plan(7, 1)
    assert names == ["Cell-01"]
    assert len(incidents) == 1
    assert incidents[0][0] == 0


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
    assert sim.elapsed_fraction({}) == 0.0

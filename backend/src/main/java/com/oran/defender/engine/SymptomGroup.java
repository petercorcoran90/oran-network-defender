package com.oran.defender.engine;

import java.util.List;
import java.util.Set;

/**
 * How an incident is <b>presented</b> to players: a group of root causes that share an ambiguous
 * symptom profile. The incident shows the group (e.g. "Service degradation"), not the exact cause,
 * so several different fixes are plausible. Each group lists the {@link DiagnosticType}s relevant
 * to telling its candidates apart.
 *
 * <p>The groups partition every {@link RootCause} exactly once, and each group's diagnostics are
 * sufficient to identify any of its candidates (proved in the engine tests) — so investigation
 * always resolves the ambiguity, while a blind guess does not.
 */
public enum SymptomGroup {
    CONGESTION("Congestion",
            Set.of(RootCause.CELL_OVERLOAD, RootCause.ROGUE_AUTOMATION),
            List.of(DiagnosticType.INSPECT_AUTOMATION)),

    SERVICE_DEGRADATION("Service degradation",
            Set.of(RootCause.TRANSPORT_LINK_FAULT, RootCause.NEIGHBOUR_CONFIG_CHANGE,
                    RootCause.SOFTWARE_UPGRADE_FAULT, RootCause.NEIGHBOUR_INTERFERENCE),
            List.of(DiagnosticType.TRACE_TRANSPORT, DiagnosticType.CHECK_NEIGHBOUR_CONFIG,
                    DiagnosticType.CHECK_UPGRADE_HISTORY, DiagnosticType.RADIO_SCAN)),

    ALARMS("Alarms",
            Set.of(RootCause.ALARM_STORM, RootCause.FALSE_ALARM),
            List.of(DiagnosticType.CORRELATE_ALARMS));

    private final String label;
    private final Set<RootCause> candidates;
    private final List<DiagnosticType> diagnostics;

    SymptomGroup(String label, Set<RootCause> candidates, List<DiagnosticType> diagnostics) {
        this.label = label;
        this.candidates = candidates;
        this.diagnostics = diagnostics;
    }

    public String label() {
        return label;
    }

    /** The root causes that can present as this symptom group (what the player must choose between). */
    public Set<RootCause> candidates() {
        return candidates;
    }

    /** The diagnostics worth running for this group. */
    public List<DiagnosticType> diagnostics() {
        return diagnostics;
    }

    /**
     * How many diagnostics a player may run on one incident of this group. Deliberately fewer than
     * the diagnostics available for the larger group, so you can't fully eliminate by brute force —
     * you must choose what to test and commit under some residual uncertainty.
     */
    public int diagnosticBudget() {
        return candidates.size() > 2 ? 2 : 1;
    }

    /** The group a given root cause presents as. */
    public static SymptomGroup of(RootCause rootCause) {
        for (SymptomGroup group : values()) {
            if (group.candidates.contains(rootCause)) {
                return group;
            }
        }
        throw new IllegalStateException("No symptom group for root cause " + rootCause);
    }
}

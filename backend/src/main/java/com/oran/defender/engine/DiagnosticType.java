package com.oran.defender.engine;

/**
 * A cheap, non-committal investigation a player can run on an incident to gather evidence about
 * its hidden {@link RootCause}. Each diagnostic is a <i>test for one hypothesis</i>: it
 * {@code implicates} exactly one root cause. Running it either CONFIRMS that cause (if it is the
 * real one) or RULES_OUT that cause — so a player narrows an ambiguous incident down by
 * confirmation/elimination instead of memorising a symptom→fix table.
 *
 * <p>Diagnostics cost time, not points (they erode the response-time bonus), so investigating
 * trades a little speed for certainty against the heavy penalty of a wrong remediation.
 */
public enum DiagnosticType {
    TRACE_TRANSPORT("Trace transport link", "Transport link fault", RootCause.TRANSPORT_LINK_FAULT),
    CHECK_NEIGHBOUR_CONFIG("Check neighbour configuration", "Recent neighbour config change", RootCause.NEIGHBOUR_CONFIG_CHANGE),
    CHECK_UPGRADE_HISTORY("Check software-upgrade history", "Software-upgrade fault", RootCause.SOFTWARE_UPGRADE_FAULT),
    RADIO_SCAN("Scan the radio environment", "Neighbour interference", RootCause.NEIGHBOUR_INTERFERENCE),
    INSPECT_AUTOMATION("Inspect automation logs", "Rogue automation", RootCause.ROGUE_AUTOMATION),
    CORRELATE_ALARMS("Correlate the alarm burst", "Alarm storm masking a real fault", RootCause.ALARM_STORM);

    private final String label;
    private final String hypothesis;
    private final RootCause implicates;

    DiagnosticType(String label, String hypothesis, RootCause implicates) {
        this.label = label;
        this.hypothesis = hypothesis;
        this.implicates = implicates;
    }

    /** Human-readable name of the action (for the UI button). */
    public String label() {
        return label;
    }

    /** The hypothesis being tested, as a noun phrase (for the evidence message). */
    public String hypothesis() {
        return hypothesis;
    }

    /** The single root cause this diagnostic tests for. */
    public RootCause implicates() {
        return implicates;
    }
}

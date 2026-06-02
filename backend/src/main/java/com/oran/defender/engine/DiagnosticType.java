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
    TRACE_TRANSPORT("Trace transport link", "Transport link fault",
            "traceroute o-ru", "traceroute", "o-ru", RootCause.TRANSPORT_LINK_FAULT),
    CHECK_NEIGHBOUR_CONFIG("Check neighbour configuration", "Recent neighbour config change",
            "netconf get-config o-du", "netconf get-config", "o-du", RootCause.NEIGHBOUR_CONFIG_CHANGE),
    CHECK_UPGRADE_HISTORY("Check software-upgrade history", "Software-upgrade fault",
            "kubectl rollout history deploy/o-du", "kubectl rollout history", "deploy/o-du", RootCause.SOFTWARE_UPGRADE_FAULT),
    RADIO_SCAN("Scan the radio environment", "Neighbour interference",
            "pm-query cell --counters sinr,rsrp,pci", "pm-query", "--counters", RootCause.NEIGHBOUR_INTERFERENCE),
    INSPECT_AUTOMATION("Inspect automation logs", "Rogue automation",
            "kubectl logs deploy/traffic-steering", "kubectl logs", "deploy/traffic-steering", RootCause.ROGUE_AUTOMATION),
    CORRELATE_ALARMS("Correlate the alarm burst", "Alarm storm masking a real fault",
            "fmcli list-alarms", "fmcli list-alarms", "", RootCause.ALARM_STORM);

    private final String label;
    private final String hypothesis;
    private final String command;
    private final String match;
    private final String args;   // comma-separated required argument tokens ("" = none)
    private final RootCause implicates;

    DiagnosticType(String label, String hypothesis, String command, String match, String args, RootCause implicates) {
        this.label = label;
        this.hypothesis = hypothesis;
        this.command = command;
        this.match = match;
        this.args = args;
        this.implicates = implicates;
    }

    /** Required argument tokens that must appear in the typed command (taught by {@code man}). */
    public String[] requiredArgs() {
        return args.isEmpty() ? new String[0] : args.split(",");
    }

    /** Human-readable name of the action (for the UI button). */
    public String label() {
        return label;
    }

    /** The hypothesis being tested, as a noun phrase (for the evidence message). */
    public String hypothesis() {
        return hypothesis;
    }

    /** The authentic console command that runs this diagnostic (shown in {@code help}). */
    public String command() {
        return command;
    }

    /** Normalised command prefix the console parser matches input against. */
    public String match() {
        return match;
    }

    /** The single root cause this diagnostic tests for. */
    public RootCause implicates() {
        return implicates;
    }
}

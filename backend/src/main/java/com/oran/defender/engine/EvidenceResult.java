package com.oran.defender.engine;

/** The outcome of running one {@link DiagnosticType} against an incident's hidden root cause. */
public enum EvidenceResult {
    /** The diagnostic's hypothesis matches the real root cause. */
    CONFIRMS,
    /** The diagnostic's hypothesis is not the real root cause — it's eliminated. */
    RULES_OUT
}

package com.oran.defender.engine;

/**
 * The result of one diagnostic: which diagnostic was run, whether it CONFIRMS or RULES_OUT, and
 * the root cause it was testing for. Deliberately carries no other state — the engine stays pure.
 */
public record Evidence(DiagnosticType diagnostic, EvidenceResult result, RootCause implicated) {

    public boolean confirms() {
        return result == EvidenceResult.CONFIRMS;
    }
}

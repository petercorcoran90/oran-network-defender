package com.oran.defender.engine;

import org.springframework.stereotype.Component;

/**
 * Runs a {@link DiagnosticType} against an incident's hidden {@link RootCause} and returns the
 * {@link Evidence}. Pure and stateless like the other engine pieces — {@code new DiagnosticEvaluator()}
 * in a test. A diagnostic CONFIRMS the cause it tests for when that is the real cause, otherwise it
 * RULES_OUT that cause. The real root cause is never returned directly — only "this hypothesis is /
 * isn't it" — so the client still can't read the answer off a single call.
 */
@Component
public class DiagnosticEvaluator {

    public Evidence diagnose(RootCause actual, DiagnosticType diagnostic) {
        EvidenceResult result = diagnostic.implicates() == actual
                ? EvidenceResult.CONFIRMS
                : EvidenceResult.RULES_OUT;
        return new Evidence(diagnostic, result, diagnostic.implicates());
    }
}

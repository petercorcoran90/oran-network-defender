package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link DiagnosticEvaluator} — pure and stateless, so {@code new
 * DiagnosticEvaluator()} per test. A diagnostic CONFIRMS the cause it tests for when that is the
 * real cause, and RULES_OUT otherwise. Crucially the evaluator never reports the actual cause —
 * only whether the tested hypothesis holds — so a single call can't reveal the answer.
 */
@DisplayName("DiagnosticEvaluator")
class DiagnosticEvaluatorTest {

    private final DiagnosticEvaluator evaluator = new DiagnosticEvaluator();

    @ParameterizedTest
    @EnumSource(DiagnosticType.class)
    @DisplayName("a diagnostic CONFIRMS when run against the cause it tests for")
    void confirmsTheImplicatedCause(DiagnosticType diagnostic) {
        Evidence evidence = evaluator.diagnose(diagnostic.implicates(), diagnostic);

        assertThat(evidence.result()).isEqualTo(EvidenceResult.CONFIRMS);
        assertThat(evidence.confirms()).isTrue();
        assertThat(evidence.diagnostic()).isEqualTo(diagnostic);
        assertThat(evidence.implicated()).isEqualTo(diagnostic.implicates());
    }

    @Test
    @DisplayName("a diagnostic RULES_OUT when the real cause is something else")
    void rulesOutWhenCauseDiffers() {
        // TRACE_TRANSPORT tests for a transport fault; run it on a cell overload instead.
        Evidence evidence = evaluator.diagnose(RootCause.CELL_OVERLOAD, DiagnosticType.TRACE_TRANSPORT);

        assertThat(evidence.result()).isEqualTo(EvidenceResult.RULES_OUT);
        assertThat(evidence.confirms()).isFalse();
        // it still reports the hypothesis it tested, not the real cause
        assertThat(evidence.implicated()).isEqualTo(RootCause.TRANSPORT_LINK_FAULT);
    }

    @Test
    @DisplayName("the evidence never carries the actual root cause when it rules out")
    void evidenceDoesNotLeakTheActualCause() {
        RootCause actual = RootCause.ROGUE_AUTOMATION;
        Evidence evidence = evaluator.diagnose(actual, DiagnosticType.TRACE_TRANSPORT);

        // the implicated cause is the tested hypothesis, never the hidden actual one
        assertThat(evidence.implicated()).isNotEqualTo(actual);
    }
}

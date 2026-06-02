package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests the diagnostic engine. The headline guarantee: running every diagnostic in an incident's
 * symptom group always pins the hidden root cause down to exactly one answer — so investigation
 * defeats the ambiguity, whereas guessing from the symptom alone cannot.
 */
class DiagnosticEvaluatorTest {

    private final DiagnosticEvaluator evaluator = new DiagnosticEvaluator();
    private final IncidentEvaluator incidentEvaluator = new IncidentEvaluator();

    // ---- diagnose() contract: CONFIRMS iff it tests for the real cause, else RULES_OUT ----
    static Stream<Arguments> allCombos() {
        return Arrays.stream(RootCause.values())
                .flatMap(rc -> Arrays.stream(DiagnosticType.values()).map(d -> arguments(rc, d)));
    }

    @ParameterizedTest(name = "{1} on {0}")
    @MethodSource("allCombos")
    @DisplayName("a diagnostic CONFIRMS only the cause it tests for, otherwise RULES_OUT")
    void diagnoseContract(RootCause actual, DiagnosticType diagnostic) {
        Evidence e = evaluator.diagnose(actual, diagnostic);

        assertThat(e.diagnostic()).isEqualTo(diagnostic);
        assertThat(e.implicated()).isEqualTo(diagnostic.implicates());
        if (diagnostic.implicates() == actual) {
            assertThat(e.result()).isEqualTo(EvidenceResult.CONFIRMS);
            assertThat(e.confirms()).isTrue();
        } else {
            assertThat(e.result()).isEqualTo(EvidenceResult.RULES_OUT);
            assertThat(e.confirms()).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(RootCause.class)
    @DisplayName("running the whole group's diagnostics uniquely identifies the hidden cause")
    void fullInvestigationIdentifiesTheCause(RootCause actual) {
        assertThat(deduce(actual)).isEqualTo(actual);
    }

    @ParameterizedTest
    @EnumSource(RootCause.class)
    @DisplayName("investigate → deduce → apply that cause's fix → CORRECT (end-to-end engine loop)")
    void investigateThenFixResolves(RootCause actual) {
        RootCause deduced = deduce(actual);
        ActionType chosenFix = deduced.correctAction();
        assertThat(incidentEvaluator.evaluate(actual, chosenFix)).isEqualTo(EvaluationResult.CORRECT);
    }

    /**
     * Plays the investigation: run every diagnostic in the incident's group, then deduce the cause
     * — the one a diagnostic CONFIRMS, or (if all rule their target out) the single remaining
     * candidate. Returns null if the evidence is ambiguous (which would fail the assertions above).
     */
    private RootCause deduce(RootCause actual) {
        SymptomGroup group = SymptomGroup.of(actual);
        RootCause confirmed = null;
        Set<RootCause> ruledOut = new HashSet<>();
        for (DiagnosticType d : group.diagnostics()) {
            Evidence e = evaluator.diagnose(actual, d);
            if (e.confirms()) {
                confirmed = e.implicated();
            } else {
                ruledOut.add(e.implicated());
            }
        }
        if (confirmed != null) {
            return confirmed;
        }
        var remaining = group.candidates().stream().filter(c -> !ruledOut.contains(c)).toList();
        return remaining.size() == 1 ? remaining.get(0) : null;
    }
}

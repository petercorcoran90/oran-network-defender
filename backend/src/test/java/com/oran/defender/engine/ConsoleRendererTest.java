package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for the emulated console: command recognition and realistic, leak-free output rendering. */
class ConsoleRendererTest {

    private final ConsoleRenderer console = new ConsoleRenderer();

    @ParameterizedTest
    @EnumSource(DiagnosticType.class)
    @DisplayName("each diagnostic's own command (with arguments) is recognised as that diagnostic")
    void recognisesEachCommand(DiagnosticType type) {
        assertThat(console.match(type.command())).contains(type);
        // still matches with extra args / different case / messy spacing
        assertThat(console.match("  " + type.command().toUpperCase() + "  --extra arg ")).contains(type);
    }

    @Test
    @DisplayName("the two kubectl commands are disambiguated by their full prefix")
    void kubectlCommandsAreDistinct() {
        assertThat(console.match("kubectl rollout history deploy/o-du")).contains(DiagnosticType.CHECK_UPGRADE_HISTORY);
        assertThat(console.match("kubectl logs deploy/traffic-steering")).contains(DiagnosticType.INSPECT_AUTOMATION);
    }

    @Test
    @DisplayName("unknown / partial input is not recognised")
    void unknownInputNotRecognised() {
        assertThat(console.match("rm -rf /")).isEmpty();
        assertThat(console.match("kubectl")).isEmpty();      // ambiguous prefix alone
        assertThat(console.match("")).isEmpty();
        assertThat(console.match(null)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(DiagnosticType.class)
    @DisplayName("confirm vs rule-out outputs differ, are non-empty, and never echo the root cause")
    void renderDiffersAndDoesNotLeak(DiagnosticType type) {
        String confirm = console.render(type, EvidenceResult.CONFIRMS);
        String ruleOut = console.render(type, EvidenceResult.RULES_OUT);

        assertThat(confirm).isNotBlank();
        assertThat(ruleOut).isNotBlank();
        assertThat(confirm).isNotEqualTo(ruleOut);
        // The output must never contain a RootCause enum name (that would hand over the answer).
        for (RootCause rc : RootCause.values()) {
            assertThat(confirm).doesNotContain(rc.name());
            assertThat(ruleOut).doesNotContain(rc.name());
        }
    }

    @Test
    @DisplayName("output reads like the real tool — the tell-tale anomaly only shows on CONFIRMS")
    void outputCarriesTheTellTaleSignal() {
        assertThat(console.render(DiagnosticType.TRACE_TRANSPORT, EvidenceResult.CONFIRMS)).contains("57% loss");
        assertThat(console.render(DiagnosticType.TRACE_TRANSPORT, EvidenceResult.RULES_OUT)).contains("0% loss");
        assertThat(console.render(DiagnosticType.CHECK_NEIGHBOUR_CONFIG, EvidenceResult.RULES_OUT)).contains("No differences");
        assertThat(console.render(DiagnosticType.CORRELATE_ALARMS, EvidenceResult.CONFIRMS)).contains("correlation score 0.92");
    }

    @Test
    @DisplayName("every diagnostic has a distinct, recognisable command")
    void everyDiagnosticHasADistinctCommand() {
        long distinct = Arrays.stream(DiagnosticType.values()).map(DiagnosticType::match).distinct().count();
        assertThat(distinct).isEqualTo(DiagnosticType.values().length);
    }
}

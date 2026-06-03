package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link ConsoleRenderer} — the emulated diagnostic console. It recognises typed
 * commands and renders realistic terminal output, but it is an <b>emulator</b>: it only maps a
 * recognised command to generated text, and the output depends solely on the already-computed
 * {@link EvidenceResult}, so the hidden root cause is never echoed back and nothing is executed.
 */
@DisplayName("ConsoleRenderer")
class ConsoleRendererTest {

    private final ConsoleRenderer console = new ConsoleRenderer();

    @Test
    @DisplayName("recognises a typed diagnostic command, ignoring its arguments and casing")
    void matchesDiagnosticIgnoringArgs() {
        assertThat(console.match("traceroute o-ru-07")).contains(DiagnosticType.TRACE_TRANSPORT);
        assertThat(console.match("  TRACEROUTE   o-ru  ")).contains(DiagnosticType.TRACE_TRANSPORT);
        assertThat(console.match("fmcli list-alarms")).contains(DiagnosticType.CORRELATE_ALARMS);
    }

    @Test
    @DisplayName("an unknown command matches nothing")
    void unknownCommandMatchesNothing() {
        assertThat(console.match("sudo rm -rf /")).isEmpty();
        assertThat(console.match("")).isEmpty();
    }

    @Test
    @DisplayName("recognises a typed remediation command")
    void matchesAction() {
        assertThat(console.matchAction("rrmctl rebalance --cell o-ru-07"))
                .contains(ActionType.REBALANCE_TRAFFIC);
        assertThat(console.matchAction("kubectl rollout restart deploy/o-du"))
                .contains(ActionType.RESTART_CELL);
        assertThat(console.matchAction("not a command")).isEmpty();
    }

    @Test
    @DisplayName("normalise trims, lower-cases and collapses whitespace; null becomes empty")
    void normaliseRules() {
        assertThat(ConsoleRenderer.normalise("  Foo   BAR ")).isEqualTo("foo bar");
        assertThat(ConsoleRenderer.normalise(null)).isEmpty();
    }

    @Test
    @DisplayName("hasRequiredArgs is true only when every token is present")
    void hasRequiredArgsChecksEveryToken() {
        String[] required = {"--cell", "--delta"};
        assertThat(console.hasRequiredArgs("rrmctl set-power --cell o-ru-07 --delta +3", required)).isTrue();
        assertThat(console.hasRequiredArgs("rrmctl set-power --cell o-ru-07", required)).isFalse();
        // no required args -> always satisfied
        assertThat(console.hasRequiredArgs("fmcli list-alarms", new String[0])).isTrue();
    }

    @ParameterizedTest
    @EnumSource(DiagnosticType.class)
    @DisplayName("output differs between a confirming and a ruling-out run, and never prints the verdict")
    void renderDiffersByResultWithoutLeaking(DiagnosticType type) {
        String confirms = console.render(type, EvidenceResult.CONFIRMS);
        String rulesOut = console.render(type, EvidenceResult.RULES_OUT);

        assertThat(confirms).isNotBlank();
        assertThat(rulesOut).isNotBlank();
        assertThat(confirms).isNotEqualTo(rulesOut);
        // the player must deduce the result from the output — it never spells it out
        assertThat(confirms).doesNotContain("CONFIRMS", "RULES_OUT");
        assertThat(rulesOut).doesNotContain("CONFIRMS", "RULES_OUT");
    }

    @Test
    @DisplayName("rendering is deterministic — same command + result give identical output (it's an emulator, not a shell)")
    void renderIsDeterministic() {
        Optional<DiagnosticType> matched = console.match("traceroute o-ru-07");
        assertThat(matched).isPresent();
        String first = console.render(matched.get(), EvidenceResult.CONFIRMS);
        String second = console.render(matched.get(), EvidenceResult.CONFIRMS);
        assertThat(first).isEqualTo(second);
    }
}

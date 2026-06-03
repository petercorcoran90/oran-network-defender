package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link DiagnosticType} — the catalogue of investigations. Each diagnostic tests
 * exactly one hypothesis (implicates one root cause) and carries the authentic command + the
 * argument tokens the player must supply. These are the contracts the console parser and the
 * teaching text rely on.
 */
@DisplayName("DiagnosticType")
class DiagnosticTypeTest {

    @ParameterizedTest
    @EnumSource(DiagnosticType.class)
    @DisplayName("the match prefix is the start of the full command")
    void matchIsAPrefixOfCommand(DiagnosticType diagnostic) {
        assertThat(diagnostic.command()).startsWith(diagnostic.match());
    }

    @ParameterizedTest
    @EnumSource(DiagnosticType.class)
    @DisplayName("every diagnostic implicates a real root cause and exposes its display metadata")
    void exposesMetadata(DiagnosticType diagnostic) {
        assertThat(diagnostic.implicates()).isNotNull();
        assertThat(diagnostic.label()).isNotBlank();
        assertThat(diagnostic.hypothesis()).isNotBlank();
        assertThat(diagnostic.command()).isNotBlank();
    }

    @Test
    @DisplayName("each diagnostic implicates a distinct root cause (one test per hypothesis)")
    void eachDiagnosticTestsADistinctCause() {
        long distinct = java.util.Arrays.stream(DiagnosticType.values())
                .map(DiagnosticType::implicates)
                .distinct()
                .count();
        assertThat(distinct).isEqualTo(DiagnosticType.values().length);
    }

    @Test
    @DisplayName("requiredArgs splits the comma-separated tokens, empty when there are none")
    void requiredArgsParsing() {
        // CORRELATE_ALARMS needs no arguments
        assertThat(DiagnosticType.CORRELATE_ALARMS.requiredArgs()).isEmpty();
        // RADIO_SCAN needs the --counters flag
        assertThat(DiagnosticType.RADIO_SCAN.requiredArgs()).containsExactly("--counters");
        // TRACE_TRANSPORT needs the o-ru target
        assertThat(DiagnosticType.TRACE_TRANSPORT.requiredArgs()).containsExactly("o-ru");
    }

    @ParameterizedTest
    @EnumSource(DiagnosticType.class)
    @DisplayName("each required-arg token actually appears in the full command (so help/man are consistent)")
    void requiredArgsAppearInTheCommand(DiagnosticType diagnostic) {
        for (String token : diagnostic.requiredArgs()) {
            assertThat(diagnostic.command()).contains(token);
        }
    }
}

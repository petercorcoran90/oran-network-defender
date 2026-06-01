package com.oran.defender.engine;

import static com.oran.defender.engine.ActionType.*;
import static com.oran.defender.engine.EvaluationResult.*;
import static com.oran.defender.engine.RootCause.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for the pure game-logic evaluator. No Spring, no DB — {@code new IncidentEvaluator()}.
 *
 * <p>The expectations below ({@link #correctActions()} / {@link #trapActions()}) are written by
 * hand from the game design, independent of {@link RootCause}'s own data, so they catch a wrong
 * edit to the enum. The {@link #allCombos()} sweep then proves all 8×9 = 72 combinations are
 * handled and that the evaluator faithfully implements the three-way contract.
 */
class IncidentEvaluatorTest {

    private final IncidentEvaluator evaluator = new IncidentEvaluator();

    // ---- The one action that fixes each root cause (hand-written from the spec) ----
    static Stream<Arguments> correctActions() {
        return Stream.of(
                arguments(CELL_OVERLOAD, REBALANCE_TRAFFIC),
                arguments(NEIGHBOUR_CONFIG_CHANGE, ROLLBACK_CONFIG),
                arguments(TRANSPORT_LINK_FAULT, ESCALATE),
                arguments(ALARM_STORM, FILTER_ALARMS),
                arguments(NEIGHBOUR_INTERFERENCE, INCREASE_TRANSMIT_POWER),
                arguments(SOFTWARE_UPGRADE_FAULT, ROLLBACK_SOFTWARE),
                arguments(ROGUE_AUTOMATION, DISABLE_AUTOMATION),
                arguments(FALSE_ALARM, IGNORE));
    }

    @ParameterizedTest(name = "{1} fixes {0}")
    @MethodSource("correctActions")
    @DisplayName("the correct action resolves each incident")
    void correctActionResolvesIncident(RootCause cause, ActionType action) {
        assertThat(evaluator.evaluate(cause, action)).isEqualTo(CORRECT);
    }

    // ---- Plausible-but-harmful traps (hand-written from the spec) ----
    static Stream<Arguments> trapActions() {
        return Stream.of(
                arguments(CELL_OVERLOAD, RESTART_CELL),
                arguments(CELL_OVERLOAD, IGNORE),
                arguments(NEIGHBOUR_CONFIG_CHANGE, RESTART_CELL),
                arguments(NEIGHBOUR_CONFIG_CHANGE, IGNORE),
                arguments(TRANSPORT_LINK_FAULT, RESTART_CELL),
                arguments(TRANSPORT_LINK_FAULT, IGNORE),
                arguments(ALARM_STORM, RESTART_CELL),
                arguments(ALARM_STORM, IGNORE),
                arguments(NEIGHBOUR_INTERFERENCE, RESTART_CELL),
                arguments(NEIGHBOUR_INTERFERENCE, IGNORE),
                arguments(SOFTWARE_UPGRADE_FAULT, INCREASE_TRANSMIT_POWER),
                arguments(SOFTWARE_UPGRADE_FAULT, IGNORE),
                arguments(ROGUE_AUTOMATION, REBALANCE_TRAFFIC),
                arguments(ROGUE_AUTOMATION, IGNORE),
                arguments(FALSE_ALARM, RESTART_CELL));
    }

    @ParameterizedTest(name = "{1} is harmful for {0}")
    @MethodSource("trapActions")
    @DisplayName("trap actions are flagged HARMFUL")
    void trapActionsAreHarmful(RootCause cause, ActionType action) {
        assertThat(evaluator.evaluate(cause, action)).isEqualTo(HARMFUL);
    }

    @Test
    @DisplayName("headline rule: IGNORE is correct for a false alarm but a trap for real incidents")
    void ignoreDependsOnContext() {
        assertThat(evaluator.evaluate(FALSE_ALARM, IGNORE)).isEqualTo(CORRECT);
        // For every genuine incident, ignoring it is the worst move.
        Arrays.stream(RootCause.values())
                .filter(cause -> cause != FALSE_ALARM)
                .forEach(cause -> assertThat(evaluator.evaluate(cause, IGNORE))
                        .as("IGNORE on %s", cause)
                        .isEqualTo(HARMFUL));
    }

    // ---- Full 8×9 = 72 sweep: every combo is handled and obeys the contract ----
    static Stream<Arguments> allCombos() {
        return Arrays.stream(RootCause.values())
                .flatMap(cause -> Arrays.stream(ActionType.values())
                        .map(action -> arguments(cause, action)));
    }

    @ParameterizedTest(name = "{0} + {1}")
    @MethodSource("allCombos")
    @DisplayName("all 72 combinations return a defined result matching the contract")
    void everyComboHonoursTheContract(RootCause cause, ActionType action) {
        EvaluationResult result = evaluator.evaluate(cause, action);

        assertThat(result).isNotNull();
        if (action == cause.correctAction()) {
            assertThat(result).isEqualTo(CORRECT);
        } else if (cause.isTrap(action)) {
            assertThat(result).isEqualTo(HARMFUL);
        } else {
            assertThat(result).isEqualTo(INEFFECTIVE);
        }
    }

    @Test
    @DisplayName("the 72 sweep actually covers every combination")
    void sweepCoversAllCombos() {
        assertThat(allCombos()).hasSize(RootCause.values().length * ActionType.values().length)
                .hasSize(72);
    }

    @ParameterizedTest
    @EnumSource(RootCause.class)
    @DisplayName("a root cause's correct action is never also one of its traps")
    void correctActionIsNeverATrap(RootCause cause) {
        assertThat(cause.isTrap(cause.correctAction()))
                .as("correct action for %s must not be a trap", cause)
                .isFalse();
    }
}

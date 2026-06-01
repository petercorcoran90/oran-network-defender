package com.oran.defender.engine;

import static com.oran.defender.engine.ActionType.*;
import static com.oran.defender.engine.EvaluationResult.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for the pure scoring logic. Same package as {@link ScoreCalculator} so the tests
 * can read its package-private constants and exercise {@link ScoreCalculator#timeBonus(long)}
 * directly. Expected values are written as explicit literals (the "golden" scores) so a change
 * to the formula has to be a deliberate update here.
 */
class ScoreCalculatorTest {

    private final ScoreCalculator calculator = new ScoreCalculator();

    @Test
    @DisplayName("instant correct fix: base + full time bonus, minus the action's cost")
    void correctInstantFix() {
        // REBALANCE_TRAFFIC costs 10 -> 100 + 50 - 10 = 140
        assertThat(calculator.pointsFor(CORRECT, 0, REBALANCE_TRAFFIC)).isEqualTo(140);
    }

    @Test
    @DisplayName("a free correct action (IGNORE on a false alarm) keeps the whole reward")
    void correctFreeAction() {
        // IGNORE costs 0 -> 100 + 50 - 0 = 150
        assertThat(calculator.pointsFor(CORRECT, 0, IGNORE)).isEqualTo(150);
    }

    @Test
    @DisplayName("correct fix at the decay boundary earns base points but no time bonus")
    void correctSlowFixHasNoBonus() {
        // 60s -> bonus 0 -> 100 + 0 - 10 = 90
        assertThat(calculator.pointsFor(CORRECT, 60, REBALANCE_TRAFFIC)).isEqualTo(90);
    }

    @Test
    @DisplayName("a harmful action loses the penalty plus its cost, regardless of timing")
    void harmfulActionPenalised() {
        // RESTART_CELL costs 20 -> -75 - 20 = -95
        assertThat(calculator.pointsFor(HARMFUL, 5, RESTART_CELL)).isEqualTo(-95);
    }

    @Test
    @DisplayName("an ineffective action only loses its cost")
    void ineffectiveActionCostsOnly() {
        assertThat(calculator.pointsFor(INEFFECTIVE, 5, RESTART_CELL)).isEqualTo(-20);
        assertThat(calculator.pointsFor(INEFFECTIVE, 5, IGNORE)).isZero();
    }

    @Test
    @DisplayName("the time bonus never applies to harmful/ineffective outcomes")
    void timeBonusOnlyHelpsCorrectActions() {
        assertThat(calculator.pointsFor(HARMFUL, 0, IGNORE)).isEqualTo(-75);
        assertThat(calculator.pointsFor(INEFFECTIVE, 0, IGNORE)).isZero();
    }

    @ParameterizedTest(name = "{0}s -> bonus {1}")
    @CsvSource({
            "-10, 50",  // negative treated as instant
            "0,   50",  // full bonus
            "1,   49",  // round(50 * (1 - 1/60))  = round(49.16)
            "15,  38",  // round(50 * 0.75)         = round(37.5)
            "30,  25",  // half-way
            "59,  1",   // round(50 * (1/60))       = round(0.83)
            "60,  0",   // boundary: no bonus
            "120, 0"    // beyond decay window
    })
    @DisplayName("time bonus decays linearly from 50 to 0 over 60 seconds")
    void timeBonusDecay(long responseSeconds, int expectedBonus) {
        assertThat(calculator.timeBonus(responseSeconds)).isEqualTo(expectedBonus);
    }

    @Test
    @DisplayName("a faster correct response never scores worse than a slower one")
    void fasterIsNeverWorse() {
        int fast = calculator.pointsFor(CORRECT, 5, REBALANCE_TRAFFIC);
        int slow = calculator.pointsFor(CORRECT, 45, REBALANCE_TRAFFIC);
        assertThat(fast).isGreaterThanOrEqualTo(slow);
    }
}

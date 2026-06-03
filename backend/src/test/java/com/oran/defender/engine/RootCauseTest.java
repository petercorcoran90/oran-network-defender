package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RootCause}. The catalogue has 9 actions but only 8 faults: each fault has
 * exactly one correct fix (8 distinct actions), and RESTART_CELL is a deliberate trap that is never
 * the right answer — so it is not "learnable" and progress counters are sized to the 8 real fixes.
 */
@DisplayName("RootCause")
class RootCauseTest {

    @Test
    @DisplayName("there are 8 faults, each with a distinct correct fix")
    void eightFaultsEachWithADistinctFix() {
        assertThat(RootCause.values()).hasSize(8);
        assertThat(RootCause.learnableActions()).hasSize(8); // 8 distinct correct actions
    }

    @Test
    @DisplayName("RESTART_CELL is a pure trap — never the correct fix, so never learnable")
    void restartCellIsATrapNotLearnable() {
        assertThat(RootCause.learnableActions()).doesNotContain(ActionType.RESTART_CELL);
        for (RootCause rc : RootCause.values()) {
            assertThat(rc.correctAction()).isNotEqualTo(ActionType.RESTART_CELL);
        }
    }

    @Test
    @DisplayName("learnable fixes are fewer than the full action catalogue (the 9th is the trap)")
    void learnableFewerThanCatalogue() {
        assertThat(RootCause.learnableActions().size()).isLessThan(ActionType.values().length);
        assertThat(ActionType.values()).hasSize(9);
    }
}

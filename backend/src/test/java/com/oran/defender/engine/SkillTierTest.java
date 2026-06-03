package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests for {@link SkillTier#of(int)} — the onboarding tier derived purely from how many
 * actions + diagnostics a player has learned. Boundaries are written as explicit literals so a
 * change to the thresholds is a deliberate edit here.
 */
@DisplayName("SkillTier")
class SkillTierTest {

    @ParameterizedTest(name = "learned {0} -> {1}")
    @CsvSource({
            "0, TRAINEE",
            "4, TRAINEE",     // last TRAINEE
            "5, OPERATOR",    // first OPERATOR
            "10, OPERATOR",   // last OPERATOR
            "11, ENGINEER",   // first ENGINEER
            "15, ENGINEER",   // everything learned
    })
    @DisplayName("maps the learned count onto the right tier at each boundary")
    void mapsLearnedCountToTier(int learned, SkillTier expected) {
        assertThat(SkillTier.of(learned)).isEqualTo(expected);
    }

    @Test
    @DisplayName("the tier never decreases as more is learned")
    void tierIsMonotonic() {
        SkillTier previous = SkillTier.of(0);
        for (int learned = 1; learned <= 15; learned++) {
            SkillTier current = SkillTier.of(learned);
            assertThat(current.ordinal()).isGreaterThanOrEqualTo(previous.ordinal());
            previous = current;
        }
    }
}

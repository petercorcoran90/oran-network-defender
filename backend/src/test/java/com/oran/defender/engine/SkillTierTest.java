package com.oran.defender.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SkillTierTest {

    @Test
    @DisplayName("tier thresholds: <5 trainee, <11 operator, else engineer")
    void thresholds() {
        assertThat(SkillTier.of(0)).isEqualTo(SkillTier.TRAINEE);
        assertThat(SkillTier.of(4)).isEqualTo(SkillTier.TRAINEE);
        assertThat(SkillTier.of(5)).isEqualTo(SkillTier.OPERATOR);
        assertThat(SkillTier.of(10)).isEqualTo(SkillTier.OPERATOR);
        assertThat(SkillTier.of(11)).isEqualTo(SkillTier.ENGINEER);
        assertThat(SkillTier.of(15)).isEqualTo(SkillTier.ENGINEER);
    }

    @Test
    @DisplayName("tier never decreases as more is learned")
    void monotonic() {
        SkillTier prev = SkillTier.of(0);
        for (int n = 1; n <= 15; n++) {
            SkillTier next = SkillTier.of(n);
            assertThat(next.ordinal()).isGreaterThanOrEqualTo(prev.ordinal());
            prev = next;
        }
    }
}

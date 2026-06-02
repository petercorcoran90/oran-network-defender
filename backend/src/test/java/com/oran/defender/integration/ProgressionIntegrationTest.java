package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.oran.defender.engine.ActionType;
import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.engine.SkillTier;
import com.oran.defender.model.UserSkill;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.service.ProgressionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Persistence of per-user learned skills against real MySQL: create-on-read, idempotent learning, tier. */
@SpringBootTest
@Transactional
@DisplayName("Progression / learned skills (MySQL Testcontainer)")
class ProgressionIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private ProgressionService progression;
    @Autowired private AppUserRepository users;

    private Long user(String name) {
        return users.save(Fixtures.user(name)).getId();
    }

    @Test
    @DisplayName("a new player starts with nothing learned (Trainee)")
    void startsEmpty() {
        UserSkill s = progression.getOrCreate(user("prog-a"));
        assertThat(s.learnedCount()).isZero();
        assertThat(SkillTier.of(s.learnedCount())).isEqualTo(SkillTier.TRAINEE);
    }

    @Test
    @DisplayName("learning persists and is idempotent")
    void learnsAndIsIdempotent() {
        Long u = user("prog-b");
        progression.learnAction(u, ActionType.REBALANCE_TRAFFIC);
        progression.learnAction(u, ActionType.REBALANCE_TRAFFIC);   // again — no duplicate
        progression.learnDiagnostic(u, DiagnosticType.TRACE_TRANSPORT);

        UserSkill s = progression.getOrCreate(u);
        assertThat(s.getLearnedActions()).containsExactly("REBALANCE_TRAFFIC");
        assertThat(s.getLearnedDiagnostics()).containsExactly("TRACE_TRANSPORT");
        assertThat(s.learnedCount()).isEqualTo(2);
        assertThat(progression.hasLearnedAction(u, ActionType.REBALANCE_TRAFFIC)).isTrue();
        assertThat(progression.hasLearnedDiagnostic(u, DiagnosticType.RADIO_SCAN)).isFalse();
    }

    @Test
    @DisplayName("tier advances as more is learned")
    void tierAdvances() {
        Long u = user("prog-c");
        for (ActionType a : new ActionType[]{ActionType.REBALANCE_TRAFFIC, ActionType.RESTART_CELL,
                ActionType.ROLLBACK_CONFIG, ActionType.ROLLBACK_SOFTWARE, ActionType.INCREASE_TRANSMIT_POWER}) {
            progression.learnAction(u, a);
        }
        assertThat(SkillTier.of(progression.getOrCreate(u).learnedCount())).isEqualTo(SkillTier.OPERATOR);
    }
}

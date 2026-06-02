package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.oran.defender.engine.ActionType;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.Difficulty;
import com.oran.defender.model.GameSession.Mode;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Player;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.PlayerRepository;
import com.oran.defender.service.ProgressionService;
import com.oran.defender.service.SessionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Solo Training mode against real MySQL: one player, immediate activation, difficulty by tier. */
@SpringBootTest
@Transactional
@DisplayName("Training mode (MySQL Testcontainer)")
class TrainingIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private SessionService sessions;
    @Autowired private ProgressionService progression;
    @Autowired private AppUserRepository users;
    @Autowired private PlayerRepository players;

    @Test
    @DisplayName("training starts immediately with one player; a new player gets EASY (Trainee)")
    void trainingStartsImmediatelyAtTraineeEasy() {
        Long u = users.save(Fixtures.user("trn-trainee")).getId();

        Player p = sessions.createTrainingSession(u, 300);
        GameSession s = p.getGameSession();

        assertThat(s.getMode()).isEqualTo(Mode.TRAINING);
        assertThat(s.getStatus()).isEqualTo(SessionStatus.ACTIVE);   // no ready-check / opponent
        assertThat(s.getStartedAt()).isNotNull();
        assertThat(s.getDifficulty()).isEqualTo(Difficulty.EASY);    // Trainee
        assertThat(players.countByGameSessionId(s.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("difficulty tracks the player's tier at creation")
    void difficultyTracksTier() {
        Long u = users.save(Fixtures.user("trn-operator")).getId();
        for (ActionType a : new ActionType[]{ActionType.REBALANCE_TRAFFIC, ActionType.RESTART_CELL,
                ActionType.ROLLBACK_CONFIG, ActionType.ROLLBACK_SOFTWARE, ActionType.INCREASE_TRANSMIT_POWER}) {
            progression.learnAction(u, a); // 5 learned -> Operator
        }

        Player p = sessions.createTrainingSession(u, 300);
        assertThat(p.getGameSession().getDifficulty()).isEqualTo(Difficulty.MEDIUM);
    }
}

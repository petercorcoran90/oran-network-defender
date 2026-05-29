package com.oran.defender;

import static org.assertj.core.api.Assertions.assertThat;

import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.repository.ActionRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Harness smoke test: confirms the test environment boots on H2 and that data.sql loads
 * against the Hibernate-generated schema. It is NOT a game-logic test — feel free to delete
 * it once your own test suite covers the context loading.
 */
@SpringBootTest
@ActiveProfiles("test")
class TestEnvironmentSmokeTest {

    @Autowired
    private ActionRepository actionRepository;

    @Autowired
    private GameSessionRepository sessionRepository;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Test
    void seedDataLoadsOnH2() {
        assertThat(actionRepository.count()).isEqualTo(9);

        var session = sessionRepository.findBySessionCode("TEST01");
        assertThat(session).isPresent();
        assertThat(session.get().getStatus()).isEqualTo(SessionStatus.ACTIVE);

        // Two players, each with their own mirrored copy of the two incidents.
        assertThat(playerRepository.countByGameSessionId(1L)).isEqualTo(2);
        assertThat(incidentRepository.findByPlayerId(1L)).hasSize(2);
        assertThat(incidentRepository.findByPlayerId(2L)).hasSize(2);
    }
}

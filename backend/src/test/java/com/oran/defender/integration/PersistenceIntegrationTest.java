package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.oran.defender.engine.RootCause;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.Player;
import com.oran.defender.repository.ActionRepository;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import com.oran.defender.repository.PlayerRepository;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence-layer integration: proves the JPA mappings, enum (STRING) round-trips and custom
 * repository queries work against real MySQL — the kind of thing an in-memory DB can mask.
 * {@code @Transactional} rolls each test back so they stay independent.
 */
@Transactional
@DisplayName("Persistence layer (MySQL Testcontainer)")
class PersistenceIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private AppUserRepository users;
    @Autowired private GameSessionRepository sessions;
    @Autowired private PlayerRepository players;
    @Autowired private NetworkCellRepository cells;
    @Autowired private IncidentRepository incidents;
    @Autowired private ActionRepository actions;

    @Test
    @DisplayName("DatabaseSeeder populates the 9-action catalog, queryable by name")
    void seederPopulatesActionCatalog() {
        assertThat(actions.count()).isEqualTo(9);
        assertThat(actions.findByActionName("REBALANCE_TRAFFIC")).isPresent();
        assertThat(actions.findByActionName("NOPE")).isEmpty();
    }

    @Test
    @DisplayName("the full object graph persists and is found via the custom finders")
    void persistsAndQueriesObjectGraph() {
        AppUser user = users.save(Fixtures.user("int-user"));
        GameSession session = sessions.save(Fixtures.activeSession("INT001", user));
        Player player = players.save(Fixtures.player(user, session, "Blue"));
        NetworkCell cell = cells.save(Fixtures.cell(session, player, "Cell-A"));
        incidents.save(Fixtures.openIncident(
                session, player, cell, "Cell overload", RootCause.CELL_OVERLOAD, Severity.HIGH));

        assertThat(sessions.findBySessionCode("INT001")).isPresent();
        assertThat(players.countByGameSessionId(session.getId())).isEqualTo(1);
        assertThat(cells.findByPlayerId(player.getId())).hasSize(1);

        List<Incident> open = incidents.findByPlayerIdAndStatus(player.getId(), IncidentStatus.OPEN);
        assertThat(open).hasSize(1);
        Incident found = open.get(0);
        // Enums persisted as STRING round-trip correctly.
        assertThat(found.getSeverity()).isEqualTo(Severity.HIGH);
        assertThat(found.getStatus()).isEqualTo(IncidentStatus.OPEN);
        // The hidden root cause is stored as the enum name.
        assertThat(found.getRootCause()).isEqualTo("CELL_OVERLOAD");
        // Relationships are wired up.
        assertThat(found.getCell().getCellName()).isEqualTo("Cell-A");
        assertThat(found.getGameSession().getSessionCode()).isEqualTo("INT001");
    }
}

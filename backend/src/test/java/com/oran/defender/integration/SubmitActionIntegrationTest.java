package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oran.defender.engine.RootCause;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.NetworkCell.HealthStatus;
import com.oran.defender.model.Player;
import com.oran.defender.model.PlayerAction;
import com.oran.defender.model.PlayerAction.ActionResult;
import com.oran.defender.repository.ActionRepository;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import com.oran.defender.repository.PlayerRepository;
import com.oran.defender.repository.ScoreEventRepository;
import com.oran.defender.service.IncidentService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end of the service write path against real MySQL: {@code submitAction} runs the engine,
 * persists the attempt + score event, updates the incident, heals/worsens the cell, and cascades.
 * This is the integration tier — the engine itself is unit-tested separately. {@code @Transactional}
 * rolls each test back for isolation.
 */
@SpringBootTest
@Transactional
@DisplayName("submitAction (MySQL Testcontainer)")
class SubmitActionIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private IncidentService incidentService;
    @Autowired private com.oran.defender.service.ProgressionService progression;
    @Autowired private AppUserRepository users;
    @Autowired private GameSessionRepository sessions;
    @Autowired private PlayerRepository players;
    @Autowired private NetworkCellRepository cells;
    @Autowired private IncidentRepository incidents;
    @Autowired private ScoreEventRepository scoreEvents;
    @Autowired private ActionRepository actions;

    private Long actionId(String name) {
        return actions.findByActionName(name).orElseThrow().getId();
    }

    @Test
    @DisplayName("correct action -> SUCCESS, incident RESOLVED, cell healed, score up, event recorded")
    void correctActionResolvesHealsAndScores() {
        AppUser user = users.save(Fixtures.user("solver"));
        GameSession session = sessions.save(Fixtures.activeSession("SUB001", user));
        Player player = players.save(Fixtures.player(user, session, "Blue"));
        NetworkCell cell = cells.save(Fixtures.cell(session, player, "Cell-A"));
        Incident incident = incidents.save(Fixtures.openIncident(
                session, player, cell, "Cell overload", RootCause.CELL_OVERLOAD, Severity.HIGH));

        PlayerAction result = incidentService.submitAction(
                session.getId(), incident.getId(), player.getId(), actionId("REBALANCE_TRAFFIC"));

        assertThat(result.getResult()).isEqualTo(ActionResult.SUCCESS);
        assertThat(result.getPointsAwarded()).isPositive();

        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.RESOLVED);

        NetworkCell healed = cells.findById(cell.getId()).orElseThrow();
        assertThat(healed.getHealthStatus()).isEqualTo(HealthStatus.GOOD);
        assertThat(healed.getSignalQuality()).isEqualTo(95.0);

        Player reloaded = players.findById(player.getId()).orElseThrow();
        assertThat(reloaded.getScore()).isEqualTo(result.getPointsAwarded());
        assertThat(scoreEvents.findByPlayerIdOrderByCreatedAtDesc(player.getId())).hasSize(1);

        // Getting it right earns the command.
        assertThat(progression.hasLearnedAction(user.getId(), com.oran.defender.engine.ActionType.REBALANCE_TRAFFIC))
                .isTrue();
    }

    @Test
    @DisplayName("trap action -> FAILED, negative score, and a cascade onto both neighbours")
    void trapActionFailsCascadesAndPenalises() {
        AppUser user = users.save(Fixtures.user("trapper"));
        GameSession session = sessions.save(Fixtures.activeSession("SUB002", user));
        Player player = players.save(Fixtures.player(user, session, "Red"));
        // Three cells A,B,C; the open incident sits on the middle one so both neighbours cascade.
        cells.save(Fixtures.cell(session, player, "Cell-A"));
        NetworkCell middle = cells.save(Fixtures.cell(session, player, "Cell-B"));
        cells.save(Fixtures.cell(session, player, "Cell-C"));
        Incident incident = incidents.save(Fixtures.openIncident(
                session, player, middle, "Cell overload", RootCause.CELL_OVERLOAD, Severity.HIGH));

        // RESTART_CELL is a trap for CELL_OVERLOAD.
        PlayerAction result = incidentService.submitAction(
                session.getId(), incident.getId(), player.getId(), actionId("RESTART_CELL"));

        assertThat(result.getResult()).isEqualTo(ActionResult.FAILED);
        assertThat(result.getPointsAwarded()).isNegative();

        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.FAILED);
        assertThat(cells.findById(middle.getId()).orElseThrow().getHealthStatus())
                .isEqualTo(HealthStatus.CRITICAL);

        // The two neighbouring cells now have fresh, solvable cascade incidents.
        List<Incident> open = incidents.findByPlayerIdAndStatus(player.getId(), IncidentStatus.OPEN);
        assertThat(open).hasSize(2)
                .allSatisfy(i -> assertThat(i.getRootCause()).isEqualTo("CELL_OVERLOAD"));

        assertThat(players.findById(player.getId()).orElseThrow().getScore()).isNegative();

        // A wrong (trap) action teaches nothing — you don't earn the command by getting it wrong.
        assertThat(progression.hasLearnedAction(user.getId(), com.oran.defender.engine.ActionType.RESTART_CELL))
                .isFalse();
    }

    @Test
    @DisplayName("ineffective action -> PARTIAL and the incident stays OPEN for a retry")
    void ineffectiveActionKeepsIncidentOpen() {
        AppUser user = users.save(Fixtures.user("dithers"));
        GameSession session = sessions.save(Fixtures.activeSession("SUB003", user));
        Player player = players.save(Fixtures.player(user, session, "Blue"));
        NetworkCell cell = cells.save(Fixtures.cell(session, player, "Cell-A"));
        Incident incident = incidents.save(Fixtures.openIncident(
                session, player, cell, "Cell overload", RootCause.CELL_OVERLOAD, Severity.HIGH));

        // ESCALATE is neither the fix nor a trap for CELL_OVERLOAD -> ineffective.
        PlayerAction result = incidentService.submitAction(
                session.getId(), incident.getId(), player.getId(), actionId("ESCALATE"));

        assertThat(result.getResult()).isEqualTo(ActionResult.PARTIAL);
        assertThat(incidents.findById(incident.getId()).orElseThrow().getStatus())
                .isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    @DisplayName("a player cannot act on another player's incident")
    void cannotActOnAnotherPlayersIncident() {
        AppUser owner = users.save(Fixtures.user("owner"));
        AppUser intruder = users.save(Fixtures.user("intruder"));
        GameSession session = sessions.save(Fixtures.activeSession("SUB004", owner));
        Player p1 = players.save(Fixtures.player(owner, session, "Blue"));
        Player p2 = players.save(Fixtures.player(intruder, session, "Red"));
        NetworkCell cell = cells.save(Fixtures.cell(session, p1, "Cell-A"));
        Incident p1Incident = incidents.save(Fixtures.openIncident(
                session, p1, cell, "Cell overload", RootCause.CELL_OVERLOAD, Severity.HIGH));

        Long submitSessionId = session.getId();
        Long p1IncidentId = p1Incident.getId();
        Long p2Id = p2.getId();
        Long rebalanceActionId = actionId("REBALANCE_TRAFFIC");
        assertThatThrownBy(() -> incidentService.submitAction(
                submitSessionId, p1IncidentId, p2Id, rebalanceActionId))
                .isInstanceOf(InvalidActionException.class);
    }
}

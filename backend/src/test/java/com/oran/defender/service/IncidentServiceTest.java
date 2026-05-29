package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.engine.ActionType;
import com.oran.defender.engine.EvaluationResult;
import com.oran.defender.engine.IncidentEvaluator;
import com.oran.defender.engine.RootCause;
import com.oran.defender.engine.ScoreCalculator;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.Action;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.Player;
import com.oran.defender.model.PlayerAction;
import com.oran.defender.model.PlayerAction.ActionResult;
import com.oran.defender.repository.ActionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.PlayerActionRepository;
import com.oran.defender.repository.PlayerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ActionRepository actionRepository;

    @Mock
    private PlayerActionRepository playerActionRepository;

    @Mock
    private IncidentEvaluator incidentEvaluator;

    @Mock
    private ScoreCalculator scoreCalculator;

    @Mock
    private ScoreService scoreService;

    @InjectMocks
    private IncidentService incidentService;

    // -- getIncidents ---------------------------------------------------------

    @Test
    void getIncidents_returnsAllIncidents_whenStatusIsBlank() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        List<Incident> incidents = List.of(incidentWith(1L, session, player, IncidentStatus.OPEN),
                incidentWith(2L, session, player, IncidentStatus.RESOLVED));
        when(incidentRepository.findByGameSessionId(10L)).thenReturn(incidents);

        List<IncidentResponse> result = incidentService.getIncidents(10L, " ");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(IncidentResponse::id).containsExactly(1L, 2L);
        verify(incidentRepository, never()).findByGameSessionIdAndStatus(anyLong(), any());
    }

    @Test
    void getIncidents_filtersByStatus_whenStatusIsProvided() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        List<Incident> incidents = List.of(incidentWith(1L, session, player, IncidentStatus.OPEN));
        when(incidentRepository.findByGameSessionIdAndStatus(10L, IncidentStatus.OPEN)).thenReturn(incidents);

        List<IncidentResponse> result = incidentService.getIncidents(10L, "open");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).status()).isEqualTo("OPEN");
    }

    @Test
    void getIncidents_throwsInvalidAction_whenStatusIsUnknown() {
        assertThatThrownBy(() -> incidentService.getIncidents(10L, "unknown"))
                .isInstanceOf(InvalidActionException.class)
                .hasMessage("Unknown incident status: unknown");
    }

    // -- getIncident ----------------------------------------------------------

    @Test
    void getIncident_returnsIncident_whenFoundInSession() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        IncidentResponse result = incidentService.getIncident(10L, 1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.gameSessionId()).isEqualTo(10L);
        assertThat(result.cellId()).isEqualTo(100L);
        assertThat(result.incidentType()).isEqualTo("High load");
        assertThat(result.status()).isEqualTo("OPEN");
    }

    @Test
    void getIncident_throwsNotFound_whenIncidentDoesNotExist() {
        when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.getIncident(10L, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Incident not found");
    }

    @Test
    void getIncident_throwsNotFound_whenIncidentBelongsToDifferentSession() {
        GameSession session = sessionWith(20L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.getIncident(10L, 1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Incident not found");
    }

    // -- submitAction ---------------------------------------------------------

    @Test
    void submitAction_savesSuccessfulActionRecordsScoreAndResolvesIncident() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        Action action = actionWith(7L, ActionType.REBALANCE_TRAFFIC);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(actionRepository.findById(7L)).thenReturn(Optional.of(action));
        when(incidentEvaluator.evaluate(RootCause.CELL_OVERLOAD, ActionType.REBALANCE_TRAFFIC))
                .thenReturn(EvaluationResult.CORRECT);
        when(scoreCalculator.pointsFor(any(EvaluationResult.class), anyLong(), any(ActionType.class))).thenReturn(85);
        when(playerActionRepository.save(any(PlayerAction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerAction result = incidentService.submitAction(10L, 1L, 5L, 7L);

        assertThat(result.getPlayer()).isSameAs(player);
        assertThat(result.getIncident()).isSameAs(incident);
        assertThat(result.getAction()).isSameAs(action);
        assertThat(result.getResult()).isEqualTo(ActionResult.SUCCESS);
        assertThat(result.getPointsAwarded()).isEqualTo(85);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
        verify(scoreService).recordScoreEvent(5L, 10L, "High load / CORRECT", 85);
        verify(incidentRepository).save(incident);
    }

    @Test
    void submitAction_keepsIncidentOpen_whenActionIsIneffective() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        Action action = actionWith(7L, ActionType.ESCALATE);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(actionRepository.findById(7L)).thenReturn(Optional.of(action));
        when(incidentEvaluator.evaluate(RootCause.CELL_OVERLOAD, ActionType.ESCALATE))
                .thenReturn(EvaluationResult.INEFFECTIVE);
        when(scoreCalculator.pointsFor(any(EvaluationResult.class), anyLong(), any(ActionType.class))).thenReturn(-5);
        when(playerActionRepository.save(any(PlayerAction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerAction result = incidentService.submitAction(10L, 1L, 5L, 7L);

        assertThat(result.getResult()).isEqualTo(ActionResult.PARTIAL);
        assertThat(result.getPointsAwarded()).isEqualTo(-5);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(incident.getResolvedAt()).isNull();
        verify(scoreService).recordScoreEvent(5L, 10L, "High load / INEFFECTIVE", -5);
        verify(incidentRepository).save(incident);
    }

    @Test
    void submitAction_marksIncidentFailed_whenActionIsHarmful() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        Action action = actionWith(7L, ActionType.RESTART_CELL);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(actionRepository.findById(7L)).thenReturn(Optional.of(action));
        when(incidentEvaluator.evaluate(RootCause.CELL_OVERLOAD, ActionType.RESTART_CELL))
                .thenReturn(EvaluationResult.HARMFUL);
        when(scoreCalculator.pointsFor(any(EvaluationResult.class), anyLong(), any(ActionType.class))).thenReturn(-40);
        when(playerActionRepository.save(any(PlayerAction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PlayerAction result = incidentService.submitAction(10L, 1L, 5L, 7L);

        assertThat(result.getResult()).isEqualTo(ActionResult.FAILED);
        assertThat(result.getPointsAwarded()).isEqualTo(-40);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.FAILED);
        assertThat(incident.getResolvedAt()).isNotNull();
        verify(scoreService).recordScoreEvent(5L, 10L, "High load / HARMFUL", -40);
        verify(incidentRepository).save(incident);
    }

    @Test
    void submitAction_throwsNotFound_whenPlayerDoesNotExist() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.submitAction(10L, 1L, 99L, 7L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Player not found");

        verify(playerActionRepository, never()).save(any());
    }

    @Test
    void submitAction_throwsInvalidAction_whenPlayerIsInDifferentSession() {
        GameSession incidentSession = sessionWith(10L, SessionStatus.ACTIVE);
        GameSession playerSession = sessionWith(20L, SessionStatus.ACTIVE);
        Player incidentPlayer = playerInSession(5L, incidentSession);
        Player actingPlayer = playerInSession(5L, playerSession);
        Incident incident = incidentWith(1L, incidentSession, incidentPlayer, IncidentStatus.OPEN);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(actingPlayer));

        assertThatThrownBy(() -> incidentService.submitAction(10L, 1L, 5L, 7L))
                .isInstanceOf(InvalidActionException.class)
                .hasMessage("Player is not part of this session");

        verify(playerActionRepository, never()).save(any());
    }

    @Test
    void submitAction_throwsInvalidAction_whenIncidentBelongsToDifferentPlayer() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player owner = playerInSession(5L, session);
        Player actingPlayer = playerInSession(6L, session);
        Incident incident = incidentWith(1L, session, owner, IncidentStatus.OPEN);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(6L)).thenReturn(Optional.of(actingPlayer));

        assertThatThrownBy(() -> incidentService.submitAction(10L, 1L, 6L, 7L))
                .isInstanceOf(InvalidActionException.class)
                .hasMessage("Incident does not belong to this player");

        verify(playerActionRepository, never()).save(any());
    }

    @Test
    void submitAction_throwsInvalidAction_whenSessionIsNotActive() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> incidentService.submitAction(10L, 1L, 5L, 7L))
                .isInstanceOf(InvalidActionException.class)
                .hasMessage("Session is not active");

        verify(playerActionRepository, never()).save(any());
    }

    @Test
    void submitAction_throwsInvalidAction_whenIncidentIsAlreadyResolved() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.RESOLVED);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> incidentService.submitAction(10L, 1L, 5L, 7L))
                .isInstanceOf(InvalidActionException.class)
                .hasMessage("Incident is already resolved");

        verify(playerActionRepository, never()).save(any());
    }

    @Test
    void submitAction_throwsNotFound_whenActionDoesNotExist() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));
        when(actionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.submitAction(10L, 1L, 5L, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Action not found");

        verify(playerActionRepository, never()).save(any());
    }

    // -- getActionsForIncident ------------------------------------------------

    @Test
    void getActionsForIncident_returnsActions_whenIncidentBelongsToSession() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        Player player = playerInSession(5L, session);
        Incident incident = incidentWith(1L, session, player, IncidentStatus.OPEN);
        List<PlayerAction> actions = List.of(playerActionWith(20L, player, incident));
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(playerActionRepository.findByIncidentId(1L)).thenReturn(actions);

        List<PlayerAction> result = incidentService.getActionsForIncident(10L, 1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(20L);
    }

    // -- helpers --------------------------------------------------------------

    private GameSession sessionWith(Long id, SessionStatus status) {
        GameSession session = new GameSession();
        session.setId(id);
        session.setName("Match " + id);
        session.setSessionCode("ABC123");
        session.setStatus(status);
        return session;
    }

    private AppUser userWith(Long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole("PLAYER");
        return user;
    }

    private Player playerInSession(Long id, GameSession session) {
        AppUser user = userWith(id, "player-" + id);
        Player player = new Player();
        player.setId(id);
        player.setUser(user);
        player.setGameSession(session);
        player.setTeamName(user.getUsername());
        player.setScore(0);
        return player;
    }

    private Incident incidentWith(Long id, GameSession session, Player player, IncidentStatus status) {
        Incident incident = new Incident();
        incident.setId(id);
        incident.setGameSession(session);
        incident.setPlayer(player);
        incident.setCell(cellWith(100L, session, player));
        incident.setIncidentType("High load");
        incident.setSeverity(Severity.HIGH);
        incident.setStatus(status);
        incident.setDescription("Cell load is above threshold");
        incident.setRootCause(RootCause.CELL_OVERLOAD.name());
        incident.setCreatedAt(Instant.now().minusSeconds(10));
        return incident;
    }

    private NetworkCell cellWith(Long id, GameSession session, Player player) {
        NetworkCell cell = new NetworkCell();
        cell.setId(id);
        cell.setGameSession(session);
        cell.setPlayer(player);
        cell.setCellName("Cell-" + id);
        return cell;
    }

    private Action actionWith(Long id, ActionType actionType) {
        Action action = new Action();
        action.setId(id);
        action.setActionName(actionType.name());
        action.setDescription(actionType.name());
        return action;
    }

    private PlayerAction playerActionWith(Long id, Player player, Incident incident) {
        PlayerAction playerAction = new PlayerAction();
        playerAction.setId(id);
        playerAction.setPlayer(player);
        playerAction.setIncident(incident);
        playerAction.setAction(actionWith(7L, ActionType.REBALANCE_TRAFFIC));
        playerAction.setResult(ActionResult.SUCCESS);
        playerAction.setPointsAwarded(50);
        return playerAction;
    }
}

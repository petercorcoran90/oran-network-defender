package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.dto.ConsoleResponse;
import com.oran.defender.engine.ConsoleRenderer;
import com.oran.defender.engine.DiagnosticEvaluator;
import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.engine.EvaluationResult;
import com.oran.defender.engine.Evidence;
import com.oran.defender.engine.EvidenceResult;
import com.oran.defender.engine.IncidentEvaluator;
import com.oran.defender.engine.RootCause;
import com.oran.defender.engine.ScoreCalculator;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.Action;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.DiagnosticRun;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.Player;
import com.oran.defender.model.PlayerAction;
import com.oran.defender.repository.ActionRepository;
import com.oran.defender.repository.DiagnosticRunRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import com.oran.defender.repository.PlayerActionRepository;
import com.oran.defender.repository.PlayerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentService unit tests")
class IncidentServiceTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private ActionRepository actionRepository;
    @Mock private PlayerActionRepository playerActionRepository;
    @Mock private NetworkCellRepository cellRepository;
    @Mock private IncidentEvaluator incidentEvaluator;
    @Mock private ScoreCalculator scoreCalculator;
    @Mock private ScoreService scoreService;
    @Mock private DiagnosticEvaluator diagnosticEvaluator;
    @Mock private DiagnosticRunRepository diagnosticRunRepository;
    @Mock private ConsoleRenderer consoleRenderer;
    @Mock private ProgressionService progressionService;

    private IncidentService incidentService;

    // shared stubs
    private GameSession session;
    private Player player;
    private NetworkCell cell;
    private Incident incident;
    private Action action;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(
                incidentRepository, playerRepository, actionRepository,
                playerActionRepository, cellRepository,
                incidentEvaluator, scoreCalculator, scoreService,
                diagnosticEvaluator, diagnosticRunRepository, consoleRenderer, progressionService);

        session = new GameSession();
        session.setId(1L);
        session.setStatus(SessionStatus.ACTIVE);
        session.setDurationSeconds(300);

        player = new Player();
        player.setId(10L);
        player.setGameSession(session);
        player.setScore(0);
        AppUser user = new AppUser();
        user.setId(7L);
        player.setUser(user);

        cell = new NetworkCell();
        cell.setId(100L);
        cell.setGameSession(session);
        cell.setPlayer(player);
        cell.setCellName("Cell-A");
        cell.setUserLoad(50.0);
        cell.setLatency(50.0);
        cell.setPacketLoss(2.0);
        cell.setAlarmCount(1);
        cell.setHealthStatus(NetworkCell.HealthStatus.WARNING);

        incident = new Incident();
        incident.setId(200L);
        incident.setGameSession(session);
        incident.setPlayer(player);
        incident.setCell(cell);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setRootCause("CELL_OVERLOAD");
        incident.setIncidentType("Cell overload");
        incident.setCreatedAt(Instant.now());

        action = new Action();
        action.setId(300L);
        action.setActionName("REBALANCE_TRAFFIC");
    }

    @Test
    @DisplayName("submitAction throws when player does not belong to the session")
    void submitAction_playerNotInSession() {
        GameSession otherSession = new GameSession();
        otherSession.setId(99L);
        player.setGameSession(otherSession);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> incidentService.submitAction(1L, 200L, 10L, 300L))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("not part of this session");
    }

    @Test
    @DisplayName("submitAction throws when session is not ACTIVE")
    void submitAction_sessionNotActive() {
        session.setStatus(SessionStatus.WAITING);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> incidentService.submitAction(1L, 200L, 10L, 300L))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("submitAction throws when incident is already resolved")
    void submitAction_incidentAlreadyResolved() {
        incident.setStatus(IncidentStatus.RESOLVED);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> incidentService.submitAction(1L, 200L, 10L, 300L))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("already resolved");
    }

    @Test
    @DisplayName("submitAction throws when incident does not belong to player")
    void submitAction_incidentNotOwnedByPlayer() {
        Player otherPlayer = new Player();
        otherPlayer.setId(99L);
        otherPlayer.setGameSession(session);
        incident.setPlayer(otherPlayer);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> incidentService.submitAction(1L, 200L, 10L, 300L))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("does not belong to this player");
    }

    @Test
    @DisplayName("submitAction throws NotFoundException when incident is not in the session")
    void submitAction_incidentNotInSession() {
        when(incidentRepository.findById(200L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> incidentService.submitAction(1L, 200L, 10L, 300L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("submitAction saves and returns PlayerAction on success")
    void submitAction_success() {
        PlayerAction saved = new PlayerAction();
        saved.setId(500L);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));
        when(actionRepository.findById(300L)).thenReturn(Optional.of(action));
        when(incidentEvaluator.evaluate(any(), any())).thenReturn(EvaluationResult.CORRECT);
        when(scoreCalculator.pointsFor(any(), any(long.class), any())).thenReturn(50);
        when(playerActionRepository.save(any())).thenReturn(saved);
        when(incidentRepository.save(any())).thenReturn(incident);
        when(cellRepository.save(any())).thenReturn(cell);

        PlayerAction result = incidentService.submitAction(1L, 200L, 10L, 300L);

        assertThat(result.getId()).isEqualTo(500L);
        verify(scoreService).recordScoreEvent(eq(10L), eq(1L), any(), eq(50));
        verify(progressionService).learnAction(any(), any()); // a correct fix earns the command
    }

    @Test
    @DisplayName("a wrong/ineffective action is NOT learned — you only earn the command by getting it right")
    void submitAction_wrongActionNotLearned() {
        PlayerAction saved = new PlayerAction();
        saved.setId(501L);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));
        when(actionRepository.findById(300L)).thenReturn(Optional.of(action));
        when(incidentEvaluator.evaluate(any(), any())).thenReturn(EvaluationResult.INEFFECTIVE);
        when(scoreCalculator.pointsFor(any(), any(long.class), any())).thenReturn(-15);
        when(playerActionRepository.save(any())).thenReturn(saved);
        when(incidentRepository.save(any())).thenReturn(incident);

        PlayerAction result = incidentService.submitAction(1L, 200L, 10L, 300L);

        assertThat(result.isNewlyLearnedAction()).isFalse();
        verify(progressionService, never()).learnAction(any(), any());
        verify(progressionService, never()).hasLearnedAction(any(), any());
    }

    @Test
    @DisplayName("getActionsForIncident returns actions from repository")
    void getActionsForIncident_returnsActions() {
        PlayerAction pa = new PlayerAction();
        pa.setId(501L);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerActionRepository.findByIncidentId(200L)).thenReturn(List.of(pa));

        List<PlayerAction> result = incidentService.getActionsForIncident(1L, 200L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(501L);
    }

    @Test
    @DisplayName("getActionsForIncident throws when incident not in session")
    void getActionsForIncident_incidentNotInSession() {
        GameSession other = new GameSession();
        other.setId(99L);
        incident.setGameSession(other);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> incidentService.getActionsForIncident(1L, 200L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("getIncidents with unknown status throws InvalidActionException")
    void getIncidents_unknownStatusThrows() {
        assertThatThrownBy(() -> incidentService.getIncidents(1L, null, "stale"))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("Unknown incident status");
    }

    // ---- investigation: diagnostics ----
    // The shared incident's hidden cause is a cell overload, which the player sees as the
    // "Congestion" symptom group. The only diagnostic that applies there is inspect-automation,
    // and that group allows a single diagnostic (budget of one).

    @Test
    @DisplayName("runDiagnostic records the evidence and teaches the diagnostic")
    void runDiagnostic_recordsEvidenceAndLearns() {
        Evidence evidence = new Evidence(
                DiagnosticType.INSPECT_AUTOMATION, EvidenceResult.RULES_OUT, RootCause.ROGUE_AUTOMATION);
        DiagnosticRun saved = new DiagnosticRun();
        saved.setId(900L);

        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));
        when(diagnosticRunRepository.findByIncidentIdAndPlayerIdAndDiagnosticType(200L, 10L, "INSPECT_AUTOMATION"))
                .thenReturn(Optional.empty());
        when(diagnosticRunRepository.countByIncidentIdAndPlayerId(200L, 10L)).thenReturn(0L);
        when(diagnosticEvaluator.diagnose(RootCause.CELL_OVERLOAD, DiagnosticType.INSPECT_AUTOMATION))
                .thenReturn(evidence);
        when(diagnosticRunRepository.save(any(DiagnosticRun.class))).thenReturn(saved);

        DiagnosticRun result = incidentService.runDiagnostic(1L, 200L, 10L, "INSPECT_AUTOMATION");

        assertThat(result.getId()).isEqualTo(900L);
        verify(diagnosticRunRepository).save(any(DiagnosticRun.class));
        verify(progressionService).learnDiagnostic(7L, DiagnosticType.INSPECT_AUTOMATION);
    }

    @Test
    @DisplayName("runDiagnostic rejects a diagnostic that does not apply to the incident's group")
    void runDiagnostic_notApplicable() {
        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        // TRACE_TRANSPORT belongs to the SERVICE_DEGRADATION group, not CONGESTION.
        assertThatThrownBy(() -> incidentService.runDiagnostic(1L, 200L, 10L, "TRACE_TRANSPORT"))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("does not apply");
    }

    @Test
    @DisplayName("runDiagnostic is idempotent — re-running returns the recorded evidence for free")
    void runDiagnostic_idempotent() {
        DiagnosticRun existing = new DiagnosticRun();
        existing.setId(901L);
        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));
        when(diagnosticRunRepository.findByIncidentIdAndPlayerIdAndDiagnosticType(200L, 10L, "INSPECT_AUTOMATION"))
                .thenReturn(Optional.of(existing));

        DiagnosticRun result = incidentService.runDiagnostic(1L, 200L, 10L, "INSPECT_AUTOMATION");

        assertThat(result.getId()).isEqualTo(901L);
        verify(diagnosticRunRepository, never()).save(any());
        verify(progressionService, never()).learnDiagnostic(any(), any());
    }

    @Test
    @DisplayName("runDiagnostic enforces the per-incident investigation budget")
    void runDiagnostic_budgetExhausted() {
        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));
        when(diagnosticRunRepository.findByIncidentIdAndPlayerIdAndDiagnosticType(200L, 10L, "INSPECT_AUTOMATION"))
                .thenReturn(Optional.empty());
        when(diagnosticRunRepository.countByIncidentIdAndPlayerId(200L, 10L)).thenReturn(1L); // budget is 1

        assertThatThrownBy(() -> incidentService.runDiagnostic(1L, 200L, 10L, "INSPECT_AUTOMATION"))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("budget");
    }

    @Test
    @DisplayName("getDiagnostics returns the runs recorded for the player's incident")
    void getDiagnostics_returnsRuns() {
        DiagnosticRun run = new DiagnosticRun();
        run.setId(902L);
        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(diagnosticRunRepository.findByIncidentIdAndPlayerId(200L, 10L)).thenReturn(List.of(run));

        assertThat(incidentService.getDiagnostics(1L, 200L, 10L)).containsExactly(run);
    }

    // ---- investigation: console (utility branches that don't depend on the renderer) ----

    @Test
    @DisplayName("console 'clear' is recognised and prints nothing")
    void runConsole_clear() {
        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        ConsoleResponse res = incidentService.runConsoleCommand(1L, 200L, 10L, "clear");

        assertThat(res.recognised()).isTrue();
        assertThat(res.output()).isEmpty();
    }

    @Test
    @DisplayName("console 'help' lists the commands relevant to this incident")
    void runConsole_helpListsCommands() {
        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        ConsoleResponse res = incidentService.runConsoleCommand(1L, 200L, 10L, "help");

        assertThat(res.recognised()).isTrue();
        // INSPECT_AUTOMATION (the CONGESTION diagnostic) is offered, by its command prefix
        assertThat(res.output()).contains("kubectl logs");
    }

    @Test
    @DisplayName("an unrecognised console command is reported, not executed")
    void runConsole_unknownCommand() {
        when(incidentRepository.findById(200L)).thenReturn(Optional.of(incident));
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        ConsoleResponse res = incidentService.runConsoleCommand(1L, 200L, 10L, "frobnicate now");

        assertThat(res.recognised()).isFalse();
        assertThat(res.output()).contains("command not found: frobnicate");
    }
}

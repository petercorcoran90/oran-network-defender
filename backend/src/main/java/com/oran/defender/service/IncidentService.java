package com.oran.defender.service;

import com.oran.defender.dto.ConsoleResponse;
import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.engine.ActionType;
import com.oran.defender.engine.ConsoleRenderer;
import com.oran.defender.engine.DiagnosticEvaluator;
import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.engine.EvaluationResult;
import com.oran.defender.engine.Evidence;
import com.oran.defender.engine.EvidenceResult;
import com.oran.defender.engine.IncidentEvaluator;
import com.oran.defender.engine.RootCause;
import com.oran.defender.engine.ScoreCalculator;
import com.oran.defender.engine.SymptomGroup;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.Action;
import com.oran.defender.model.DiagnosticRun;
import com.oran.defender.model.GameSession.SessionStatus;
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
import com.oran.defender.repository.DiagnosticRunRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.NetworkCellRepository;
import com.oran.defender.repository.PlayerActionRepository;
import com.oran.defender.repository.PlayerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final PlayerRepository playerRepository;
    private final ActionRepository actionRepository;
    private final PlayerActionRepository playerActionRepository;
    private final NetworkCellRepository cellRepository;
    private final IncidentEvaluator incidentEvaluator;
    private final ScoreCalculator scoreCalculator;
    private final ScoreService scoreService;
    private final DiagnosticEvaluator diagnosticEvaluator;
    private final DiagnosticRunRepository diagnosticRunRepository;
    private final ConsoleRenderer consoleRenderer;

    // Each diagnostic a player runs costs this many points off the eventual remediation score, so
    // investigating is a real expense — the accurate, economical player beats the fast guesser,
    // and "diagnose everything" is a bad strategy. Combined with the per-incident budget below.
    static final int DIAGNOSTIC_COST = 15;

    public IncidentService(IncidentRepository incidentRepository,
                           PlayerRepository playerRepository,
                           ActionRepository actionRepository,
                           PlayerActionRepository playerActionRepository,
                           NetworkCellRepository cellRepository,
                           IncidentEvaluator incidentEvaluator,
                           ScoreCalculator scoreCalculator,
                           ScoreService scoreService,
                           DiagnosticEvaluator diagnosticEvaluator,
                           DiagnosticRunRepository diagnosticRunRepository,
                           ConsoleRenderer consoleRenderer) {
        this.incidentRepository = incidentRepository;
        this.playerRepository = playerRepository;
        this.actionRepository = actionRepository;
        this.playerActionRepository = playerActionRepository;
        this.cellRepository = cellRepository;
        this.incidentEvaluator = incidentEvaluator;
        this.scoreCalculator = scoreCalculator;
        this.scoreService = scoreService;
        this.diagnosticEvaluator = diagnosticEvaluator;
        this.diagnosticRunRepository = diagnosticRunRepository;
        this.consoleRenderer = consoleRenderer;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidents(Long sessionId, Long playerId, String status) {
        boolean hasStatus = status != null && !status.isBlank();
        IncidentStatus parsed = hasStatus ? parseStatus(status) : null;
        List<Incident> incidents;
        if (playerId != null) {
            // A player only ever sees their own mirrored incidents.
            incidents = hasStatus
                    ? incidentRepository.findByPlayerIdAndStatus(playerId, parsed)
                    : incidentRepository.findByPlayerId(playerId);
        } else {
            incidents = hasStatus
                    ? incidentRepository.findByGameSessionIdAndStatus(sessionId, parsed)
                    : incidentRepository.findByGameSessionId(sessionId);
        }
        return incidents.stream().map(IncidentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(Long sessionId, Long incidentId) {
        return IncidentResponse.from(loadIncidentInSession(sessionId, incidentId));
    }

    /**
     * Evaluates a player's action against the incident's hidden root cause, scores it,
     * persists the attempt and the resulting score event, and updates the incident.
     */
    @Transactional
    public PlayerAction submitAction(Long sessionId, Long incidentId, Long playerId, Long actionId) {
        Incident incident = loadIncidentInSession(sessionId, incidentId);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found"));

        // The player must be in this session...
        if (!player.getGameSession().getId().equals(sessionId)) {
            throw new InvalidActionException("Player is not part of this session");
        }
        // ...and may only act on their own mirrored incident, never the opponent's.
        if (!incident.getPlayer().getId().equals(playerId)) {
            throw new InvalidActionException("Incident does not belong to this player");
        }
        // The match must be live and the incident still open.
        if (incident.getGameSession().getStatus() != SessionStatus.ACTIVE) {
            throw new InvalidActionException("Session is not active");
        }
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new InvalidActionException("Incident is already resolved");
        }

        Action action = actionRepository.findById(actionId)
                .orElseThrow(() -> new NotFoundException("Action not found"));

        // Hand off to the pure engine: hidden root cause + chosen action -> verdict + points.
        RootCause rootCause = RootCause.valueOf(incident.getRootCause());
        ActionType actionType = ActionType.valueOf(action.getActionName());
        long responseSeconds = Duration.between(incident.getCreatedAt(), Instant.now()).getSeconds();
        EvaluationResult verdict = incidentEvaluator.evaluate(rootCause, actionType);
        // Subtract the cost of investigating: every diagnostic this player ran on this incident
        // costs real points, so over-investigating is penalised regardless of the outcome.
        long diagnostics = diagnosticRunRepository.countByIncidentIdAndPlayerId(incidentId, playerId);
        int points = scoreCalculator.pointsFor(verdict, responseSeconds, actionType)
                - (int) diagnostics * DIAGNOSTIC_COST;

        PlayerAction playerAction = new PlayerAction();
        playerAction.setPlayer(player);
        playerAction.setIncident(incident);
        playerAction.setAction(action);
        playerAction.setResult(toActionResult(verdict));
        playerAction.setPointsAwarded(points);
        PlayerAction saved = playerActionRepository.save(playerAction);

        scoreService.recordScoreEvent(playerId, sessionId,
                incident.getIncidentType() + " / " + verdict.name(), points);
        applyOutcome(incident, verdict);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<PlayerAction> getActionsForIncident(Long sessionId, Long incidentId) {
        loadIncidentInSession(sessionId, incidentId); // 404 if it isn't in this session
        return playerActionRepository.findByIncidentId(incidentId);
    }

    /**
     * Runs a diagnostic against the incident's hidden root cause and records the evidence. Same
     * ownership/state guards as {@link #submitAction}. Idempotent: re-running a diagnostic returns
     * the already-recorded evidence (so the player isn't charged time twice). The diagnostic must
     * be one of those relevant to the incident's symptom group.
     */
    @Transactional
    public DiagnosticRun runDiagnostic(Long sessionId, Long incidentId, Long playerId, String diagnosticName) {
        Incident incident = loadIncidentInSession(sessionId, incidentId);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        if (!player.getGameSession().getId().equals(sessionId)) {
            throw new InvalidActionException("Player is not part of this session");
        }
        if (!incident.getPlayer().getId().equals(playerId)) {
            throw new InvalidActionException("Incident does not belong to this player");
        }
        if (incident.getGameSession().getStatus() != SessionStatus.ACTIVE) {
            throw new InvalidActionException("Session is not active");
        }
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new InvalidActionException("Incident is already resolved");
        }

        DiagnosticType diagnostic = parseDiagnostic(diagnosticName);
        RootCause rootCause = RootCause.valueOf(incident.getRootCause());
        SymptomGroup group = SymptomGroup.of(rootCause);
        if (!group.diagnostics().contains(diagnostic)) {
            throw new InvalidActionException("Diagnostic does not apply to this incident");
        }

        // Idempotent: if this diagnostic was already run, return the existing evidence (free).
        Optional<DiagnosticRun> existing = diagnosticRunRepository
                .findByIncidentIdAndPlayerIdAndDiagnosticType(incidentId, playerId, diagnostic.name());
        if (existing.isPresent()) {
            return existing.get();
        }
        // Otherwise it's a new test — enforce the per-incident investigation budget.
        long used = diagnosticRunRepository.countByIncidentIdAndPlayerId(incidentId, playerId);
        if (used >= group.diagnosticBudget()) {
            throw new InvalidActionException("Investigation budget used up — commit a remediation");
        }
        Evidence evidence = diagnosticEvaluator.diagnose(rootCause, diagnostic);
        DiagnosticRun run = new DiagnosticRun();
        run.setIncident(incident);
        run.setPlayer(player);
        run.setDiagnosticType(diagnostic.name());
        run.setResult(evidence.result().name());
        run.setImplicated(evidence.implicated().name());
        return diagnosticRunRepository.save(run);
    }

    @Transactional(readOnly = true)
    public List<DiagnosticRun> getDiagnostics(Long sessionId, Long incidentId, Long playerId) {
        loadIncidentInSession(sessionId, incidentId); // 404 if it isn't in this session
        return diagnosticRunRepository.findByIncidentIdAndPlayerId(incidentId, playerId);
    }

    /**
     * The diagnostic console. Recognised diagnostic commands relevant to this incident run the real
     * diagnostic (budget + cost via {@link #runDiagnostic}); utility commands (help/man/clear) and
     * unrecognised input are free. Returns emulated terminal output for the player to interpret —
     * never the hidden root cause.
     */
    @Transactional
    public ConsoleResponse runConsoleCommand(Long sessionId, Long incidentId, Long playerId, String commandLine) {
        Incident incident = requireOwnOpenIncident(sessionId, incidentId, playerId);
        String norm = ConsoleRenderer.normalise(commandLine);

        if (norm.isEmpty() || norm.equals("clear")) {
            return new ConsoleResponse(commandLine, true, "");
        }
        if (norm.equals("help") || norm.equals("?")) {
            return new ConsoleResponse(commandLine, true, helpText(incident));
        }
        if (norm.startsWith("man ")) {
            return new ConsoleResponse(commandLine, true, manText(norm.substring(4).trim()));
        }

        DiagnosticType type = consoleRenderer.match(commandLine).orElse(null);
        if (type == null) {
            String cmd = norm.split(" ")[0];
            return new ConsoleResponse(commandLine, false, "command not found: " + cmd + " — type 'help'");
        }

        SymptomGroup group = SymptomGroup.of(RootCause.valueOf(incident.getRootCause()));
        if (!group.diagnostics().contains(type)) {
            // A real command, but it probes a subsystem unrelated to this incident — nominal, free.
            return new ConsoleResponse(commandLine, true,
                    consoleRenderer.render(type, EvidenceResult.RULES_OUT) + "\n(no bearing on this incident)");
        }

        // A relevant diagnostic — runs the real thing (enforces budget + point cost, records it).
        DiagnosticRun run = runDiagnostic(sessionId, incidentId, playerId, type.name());
        return new ConsoleResponse(commandLine, true,
                consoleRenderer.render(type, EvidenceResult.valueOf(run.getResult())));
    }

    private String helpText(Incident incident) {
        SymptomGroup group = SymptomGroup.of(RootCause.valueOf(incident.getRootCause()));
        StringBuilder sb = new StringBuilder("Diagnostics for this incident (each costs points; budget ")
                .append(group.diagnosticBudget()).append("):\n");
        group.diagnostics().forEach(d -> sb.append("  ").append(d.command()).append('\n'));
        return sb.append("Utility: help · man <command> · clear").toString();
    }

    private String manText(String cmd) {
        return consoleRenderer.match(cmd)
                .map(d -> d.command() + "\n  " + d.label() + ". Investigates: " + d.hypothesis() + ".")
                .orElse("No manual entry for " + cmd);
    }

    private Incident requireOwnOpenIncident(Long sessionId, Long incidentId, Long playerId) {
        Incident incident = loadIncidentInSession(sessionId, incidentId);
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found"));
        if (!player.getGameSession().getId().equals(sessionId)) {
            throw new InvalidActionException("Player is not part of this session");
        }
        if (!incident.getPlayer().getId().equals(playerId)) {
            throw new InvalidActionException("Incident does not belong to this player");
        }
        if (incident.getGameSession().getStatus() != SessionStatus.ACTIVE) {
            throw new InvalidActionException("Session is not active");
        }
        if (incident.getStatus() != IncidentStatus.OPEN) {
            throw new InvalidActionException("Incident is already resolved");
        }
        return incident;
    }

    private DiagnosticType parseDiagnostic(String name) {
        try {
            return DiagnosticType.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidActionException("Unknown diagnostic: " + name);
        }
    }

    private Incident loadIncidentInSession(Long sessionId, Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new NotFoundException("Incident not found"));
        if (!incident.getGameSession().getId().equals(sessionId)) {
            throw new NotFoundException("Incident not found");
        }
        return incident;
    }

    private IncidentStatus parseStatus(String status) {
        try {
            return IncidentStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidActionException("Unknown incident status: " + status);
        }
    }

    private void applyOutcome(Incident incident, EvaluationResult verdict) {
        switch (verdict) {
            case CORRECT -> {
                incident.setStatus(IncidentStatus.RESOLVED);
                incident.setResolvedAt(Instant.now());
                healCell(incident.getCell());
            }
            case HARMFUL -> {
                incident.setStatus(IncidentStatus.FAILED);
                incident.setResolvedAt(Instant.now());
                worsenCell(incident.getCell());
                cascade(incident); // a bad call ripples into neighbouring cells
            }
            case INEFFECTIVE -> {
                // No change: the incident stays OPEN so the player can try a different action.
            }
        }
        incidentRepository.save(incident);
    }

    /** A correct fix restores the cell so the map/health reflects the win. */
    private void healCell(NetworkCell cell) {
        cell.setSignalQuality(95.0);
        cell.setUserLoad(30.0);
        cell.setLatency(25.0);
        cell.setPacketLoss(1.0);
        cell.setAlarmCount(0);
        cell.setHealthStatus(HealthStatus.GOOD);
        cell.setConfigStatus(NetworkCell.ConfigStatus.STABLE);
        cellRepository.save(cell);
    }

    /** A trap makes things worse — the cell degrades and the player sees the cost. */
    private void worsenCell(NetworkCell cell) {
        cell.setUserLoad(Math.min(100.0, cell.getUserLoad() + 10.0));
        cell.setLatency(cell.getLatency() + 30.0);
        cell.setPacketLoss(Math.min(100.0, cell.getPacketLoss() + 5.0));
        cell.setAlarmCount(cell.getAlarmCount() + 2);
        cell.setHealthStatus(HealthStatus.CRITICAL);
        cellRepository.save(cell);
    }

    private static final String CASCADE_TYPE = "Cascade Overload";
    private static final String CASCADE_DESC =
            "Traffic from a downed neighbouring cell has overloaded this one.";

    /**
     * Knock-on effect: a crashed cell drags its two adjacent cells (by name order) toward
     * trouble. A neighbour with no open incident gets a new, solvable "Cascade Overload"
     * (root cause CELL_OVERLOAD → fix with Rebalance Traffic); a neighbour already in trouble
     * just degrades further. Only affects the acting player's own network.
     */
    private void cascade(Incident source) {
        Player player = source.getPlayer();
        NetworkCell crashed = source.getCell();
        List<NetworkCell> cells = cellRepository.findByPlayerId(player.getId());
        if (cells.size() < 2) {
            return;
        }
        cells.sort(Comparator.comparing(NetworkCell::getCellName));
        int idx = -1;
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i).getId().equals(crashed.getId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return;
        }
        Set<Long> cellsWithOpenIncident = incidentRepository
                .findByPlayerIdAndStatus(player.getId(), IncidentStatus.OPEN).stream()
                .map(i -> i.getCell().getId()).collect(Collectors.toSet());

        int n = cells.size();
        for (int delta : new int[]{-1, 1}) {
            NetworkCell neighbour = cells.get((idx + delta + n) % n);
            if (neighbour.getId().equals(crashed.getId())) {
                continue;
            }
            if (cellsWithOpenIncident.contains(neighbour.getId())) {
                neighbour.setUserLoad(Math.min(100.0, neighbour.getUserLoad() + 10.0));
                neighbour.setLatency(neighbour.getLatency() + 20.0);
                if (neighbour.getHealthStatus() == HealthStatus.GOOD) {
                    neighbour.setHealthStatus(HealthStatus.WARNING);
                }
                cellRepository.save(neighbour);
            } else {
                spawnCascadeIncident(source.getGameSession(), player, neighbour);
                cellsWithOpenIncident.add(neighbour.getId());
            }
        }
    }

    private void spawnCascadeIncident(GameSession session, Player player, NetworkCell cell) {
        cell.setUserLoad(90.0);
        cell.setLatency(150.0);
        cell.setHealthStatus(HealthStatus.WARNING);
        cellRepository.save(cell);

        Incident incident = new Incident();
        incident.setGameSession(session);
        incident.setPlayer(player);
        incident.setCell(cell);
        incident.setIncidentType(CASCADE_TYPE);
        incident.setSeverity(Severity.HIGH);
        incident.setStatus(IncidentStatus.OPEN);
        incident.setDescription(CASCADE_DESC);
        incident.setRootCause(RootCause.CELL_OVERLOAD.name());
        incident.setCreatedAt(Instant.now());
        incidentRepository.save(incident);
    }

    private ActionResult toActionResult(EvaluationResult verdict) {
        return switch (verdict) {
            case CORRECT -> ActionResult.SUCCESS;
            case INEFFECTIVE -> ActionResult.PARTIAL;
            case HARMFUL -> ActionResult.FAILED;
        };
    }
}

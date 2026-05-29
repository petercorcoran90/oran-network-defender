package com.oran.defender.service;

import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.engine.ActionType;
import com.oran.defender.engine.EvaluationResult;
import com.oran.defender.engine.IncidentEvaluator;
import com.oran.defender.engine.RootCause;
import com.oran.defender.engine.ScoreCalculator;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.Action;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Player;
import com.oran.defender.model.PlayerAction;
import com.oran.defender.model.PlayerAction.ActionResult;
import com.oran.defender.repository.ActionRepository;
import com.oran.defender.repository.IncidentRepository;
import com.oran.defender.repository.PlayerActionRepository;
import com.oran.defender.repository.PlayerRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final PlayerRepository playerRepository;
    private final ActionRepository actionRepository;
    private final PlayerActionRepository playerActionRepository;
    private final IncidentEvaluator incidentEvaluator;
    private final ScoreCalculator scoreCalculator;
    private final ScoreService scoreService;

    public IncidentService(IncidentRepository incidentRepository,
                           PlayerRepository playerRepository,
                           ActionRepository actionRepository,
                           PlayerActionRepository playerActionRepository,
                           IncidentEvaluator incidentEvaluator,
                           ScoreCalculator scoreCalculator,
                           ScoreService scoreService) {
        this.incidentRepository = incidentRepository;
        this.playerRepository = playerRepository;
        this.actionRepository = actionRepository;
        this.playerActionRepository = playerActionRepository;
        this.incidentEvaluator = incidentEvaluator;
        this.scoreCalculator = scoreCalculator;
        this.scoreService = scoreService;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> getIncidents(Long sessionId, String status) {
        List<Incident> incidents = (status == null || status.isBlank())
                ? incidentRepository.findByGameSessionId(sessionId)
                : incidentRepository.findByGameSessionIdAndStatus(sessionId, parseStatus(status));
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
        int points = scoreCalculator.pointsFor(verdict, responseSeconds, actionType);

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
            }
            case HARMFUL -> {
                incident.setStatus(IncidentStatus.FAILED);
                incident.setResolvedAt(Instant.now());
            }
            case INEFFECTIVE -> {
                // No change: the incident stays OPEN so the player can try a different action.
            }
        }
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

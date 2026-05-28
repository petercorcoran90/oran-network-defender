package com.oran.defender.service;

import com.oran.defender.model.Incident;
import com.oran.defender.model.PlayerAction;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IncidentService {

    // TODO: inject IncidentRepository, PlayerRepository, ActionRepository, ScoreService

    public List<Incident> getIncidents(Long sessionId, String status) {
        // TODO: if status is null return all; otherwise filter by IncidentStatus enum
        throw new UnsupportedOperationException("Not implemented");
    }

    public Incident getIncident(Long sessionId, Long incidentId) {
        // TODO: find by ID, verify it belongs to sessionId, or throw 404
        // NOTE: do NOT include rootCause in the response — that is server-side only
        throw new UnsupportedOperationException("Not implemented");
    }

    public PlayerAction submitAction(Long sessionId, Long incidentId, Long playerId, Long actionId) {
        // TODO:
        // 1. Verify player belongs to this session
        // 2. Verify incident is OPEN
        // 3. Evaluate action against incident rootCause → determine result
        // 4. Calculate points (correct/partial/failed)
        // 5. Persist PlayerAction
        // 6. Call ScoreService to record score event
        // 7. Update incident status if resolved
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<PlayerAction> getActionsForIncident(Long sessionId, Long incidentId) {
        // TODO: return all player_actions for this incident
        throw new UnsupportedOperationException("Not implemented");
    }
}

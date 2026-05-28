package com.oran.defender.service;

import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.model.PlayerAction;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IncidentService {

    // TODO: inject IncidentRepository, PlayerRepository, ActionRepository, ScoreService

    public List<IncidentResponse> getIncidents(Long sessionId, String status) {
        // TODO: fetch Incident entities (filter by status if provided)
        // TODO: map each to IncidentResponse.from(incident) before returning
        throw new UnsupportedOperationException("Not implemented");
    }

    public IncidentResponse getIncident(Long sessionId, Long incidentId) {
        // TODO: find Incident by ID, verify it belongs to sessionId, or throw 404
        // TODO: return IncidentResponse.from(incident) — rootCause stays server-side
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

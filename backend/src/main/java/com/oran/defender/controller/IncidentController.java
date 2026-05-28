package com.oran.defender.controller;

import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.model.PlayerAction;
import com.oran.defender.service.IncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions/{sessionId}/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    record SubmitActionRequest(@NotNull Long playerId, @NotNull Long actionId) {}

    // Get all incidents for a session — optionally filter by status (OPEN, RESOLVED, FAILED)
    @GetMapping
    public List<IncidentResponse> getIncidents(@PathVariable Long sessionId,
                                               @RequestParam(required = false) String status) {
        return incidentService.getIncidents(sessionId, status);
    }

    // Get a single incident's details including evidence
    @GetMapping("/{incidentId}")
    public IncidentResponse getIncident(@PathVariable Long sessionId,
                                        @PathVariable Long incidentId) {
        return incidentService.getIncident(sessionId, incidentId);
    }

    // Player submits an action against an incident
    @PostMapping("/{incidentId}/actions")
    public PlayerAction submitAction(@PathVariable Long sessionId,
                                     @PathVariable Long incidentId,
                                     @Valid @RequestBody SubmitActionRequest req) {
        return incidentService.submitAction(sessionId, incidentId, req.playerId(), req.actionId());
    }

    // Get all actions submitted for a specific incident
    @GetMapping("/{incidentId}/actions")
    public List<PlayerAction> getIncidentActions(@PathVariable Long sessionId,
                                                  @PathVariable Long incidentId) {
        return incidentService.getActionsForIncident(sessionId, incidentId);
    }
}

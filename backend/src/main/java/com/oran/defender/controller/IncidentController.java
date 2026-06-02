package com.oran.defender.controller;

import com.oran.defender.dto.ConsoleResponse;
import com.oran.defender.dto.DiagnosticResponse;
import com.oran.defender.dto.IncidentResponse;
import com.oran.defender.dto.PlayerActionResponse;
import com.oran.defender.service.IncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    record RunDiagnosticRequest(@NotNull Long playerId, @NotBlank String diagnostic) {}
    record ConsoleRequest(@NotNull Long playerId, @NotBlank @Size(max = 200) String command) {}

    // Get incidents for a session — pass ?playerId= for one player's incidents,
    // and/or ?status= to filter by OPEN, RESOLVED, FAILED
    @GetMapping
    public List<IncidentResponse> getIncidents(@PathVariable Long sessionId,
                                               @RequestParam(required = false) Long playerId,
                                               @RequestParam(required = false) String status) {
        return incidentService.getIncidents(sessionId, playerId, status);
    }

    // Get a single incident's details including evidence
    @GetMapping("/{incidentId}")
    public IncidentResponse getIncident(@PathVariable Long sessionId,
                                        @PathVariable Long incidentId) {
        return incidentService.getIncident(sessionId, incidentId);
    }

    // Player submits an action against an incident
    @PostMapping("/{incidentId}/actions")
    public PlayerActionResponse submitAction(@PathVariable Long sessionId,
                                             @PathVariable Long incidentId,
                                             @Valid @RequestBody SubmitActionRequest req) {
        return PlayerActionResponse.from(
                incidentService.submitAction(sessionId, incidentId, req.playerId(), req.actionId()));
    }

    // Get all actions submitted for a specific incident
    @GetMapping("/{incidentId}/actions")
    public List<PlayerActionResponse> getIncidentActions(@PathVariable Long sessionId,
                                                          @PathVariable Long incidentId) {
        return incidentService.getActionsForIncident(sessionId, incidentId).stream()
                .map(PlayerActionResponse::from).toList();
    }

    // Player runs a diagnostic on an incident to gather evidence (investigation)
    @PostMapping("/{incidentId}/diagnostics")
    public DiagnosticResponse runDiagnostic(@PathVariable Long sessionId,
                                            @PathVariable Long incidentId,
                                            @Valid @RequestBody RunDiagnosticRequest req) {
        return DiagnosticResponse.from(
                incidentService.runDiagnostic(sessionId, incidentId, req.playerId(), req.diagnostic()));
    }

    // Get the evidence a player has gathered on an incident so far
    @GetMapping("/{incidentId}/diagnostics")
    public List<DiagnosticResponse> getDiagnostics(@PathVariable Long sessionId,
                                                   @PathVariable Long incidentId,
                                                   @RequestParam Long playerId) {
        return incidentService.getDiagnostics(sessionId, incidentId, playerId).stream()
                .map(DiagnosticResponse::from).toList();
    }

    // Diagnostic console: run a typed command against an incident, get emulated terminal output
    @PostMapping("/{incidentId}/console")
    public ConsoleResponse console(@PathVariable Long sessionId,
                                   @PathVariable Long incidentId,
                                   @Valid @RequestBody ConsoleRequest req) {
        return incidentService.runConsoleCommand(sessionId, incidentId, req.playerId(), req.command());
    }
}

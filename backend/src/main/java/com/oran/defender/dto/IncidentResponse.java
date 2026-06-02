package com.oran.defender.dto;

import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.engine.RootCause;
import com.oran.defender.engine.SymptomGroup;
import com.oran.defender.model.Incident;

import java.time.Instant;
import java.util.List;

/**
 * What the client sees. The actual rootCause is intentionally excluded — exposing it would let
 * players cheat. We DO expose the ambiguous {@code symptomGroup} the incident presents as, plus the
 * {@code availableDiagnostics} for that group, so the player can investigate. Both are derived from
 * the (server-side) root cause but reveal only the group, never the cause itself.
 */
public record IncidentResponse(
        Long id,
        Long gameSessionId,
        Long playerId,
        Long cellId,
        String incidentType,
        String severity,
        String status,
        String description,
        String symptomGroup,
        List<DiagnosticInfo> availableDiagnostics,
        Instant createdAt,
        Instant resolvedAt
) {
    /** A diagnostic the player can run, for the UI (name to submit + human label). */
    public record DiagnosticInfo(String name, String label) {}

    public static IncidentResponse from(Incident incident) {
        String groupLabel = null;
        List<DiagnosticInfo> diagnostics = List.of();
        SymptomGroup group = symptomGroupOf(incident.getRootCause());
        if (group != null) {
            groupLabel = group.label();
            diagnostics = group.diagnostics().stream()
                    .map(d -> new DiagnosticInfo(d.name(), d.label()))
                    .toList();
        }
        return new IncidentResponse(
                incident.getId(),
                incident.getGameSession().getId(),
                incident.getPlayer().getId(),
                incident.getCell().getId(),
                incident.getIncidentType(),
                incident.getSeverity().name(),
                incident.getStatus().name(),
                incident.getDescription(),
                groupLabel,
                diagnostics,
                incident.getCreatedAt(),
                incident.getResolvedAt()
        );
    }

    private static SymptomGroup symptomGroupOf(String rootCause) {
        if (rootCause == null) {
            return null;
        }
        try {
            return SymptomGroup.of(RootCause.valueOf(rootCause));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return null; // unknown/legacy root cause — just omit the group
        }
    }
}

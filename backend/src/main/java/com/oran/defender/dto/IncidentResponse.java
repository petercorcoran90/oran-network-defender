package com.oran.defender.dto;

import com.oran.defender.model.Incident;

import java.time.Instant;

/**
 * What the client sees. rootCause is intentionally excluded — exposing it would let players cheat.
 */
public record IncidentResponse(
        Long id,
        Long gameSessionId,
        Long cellId,
        String incidentType,
        String severity,
        String status,
        String description,
        String evidenceJson,
        Instant createdAt,
        Instant resolvedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getGameSessionId(),
                incident.getCellId(),
                incident.getIncidentType(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getDescription(),
                incident.getEvidenceJson(),
                incident.getCreatedAt(),
                incident.getResolvedAt()
        );
    }
}

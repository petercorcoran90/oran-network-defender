package com.oran.defender.dto;

import com.oran.defender.model.PlayerAction;
import java.time.Instant;

/** The outcome of a submitted action. Mirrors what the engine decided. */
public record PlayerActionResponse(
        Long id,
        Long playerId,
        Long incidentId,
        Long actionId,
        String result,
        Integer pointsAwarded,
        Instant submittedAt
) {
    public static PlayerActionResponse from(PlayerAction pa) {
        return new PlayerActionResponse(
                pa.getId(),
                pa.getPlayer().getId(),
                pa.getIncident().getId(),
                pa.getAction().getId(),
                pa.getResult().name(),
                pa.getPointsAwarded(),
                pa.getSubmittedAt());
    }
}

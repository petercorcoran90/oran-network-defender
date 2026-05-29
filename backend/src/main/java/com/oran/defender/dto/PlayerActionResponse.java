package com.oran.defender.dto;

import com.oran.defender.model.PlayerAction;
import java.time.Instant;

public record PlayerActionResponse(
        Long id,
        Long playerId,
        Long incidentId,
        Long actionId,
        String actionName,
        String result,
        Integer pointsAwarded,
        Instant submittedAt) {

    public static PlayerActionResponse from(PlayerAction playerAction) {
        return new PlayerActionResponse(
                playerAction.getId(),
                playerAction.getPlayer().getId(),
                playerAction.getIncident().getId(),
                playerAction.getAction().getId(),
                playerAction.getAction().getActionName(),
                playerAction.getResult().name(),
                playerAction.getPointsAwarded(),
                playerAction.getSubmittedAt());
    }
}

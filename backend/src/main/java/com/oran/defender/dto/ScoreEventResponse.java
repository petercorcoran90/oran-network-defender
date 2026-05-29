package com.oran.defender.dto;

import com.oran.defender.model.ScoreEvent;
import java.time.Instant;

public record ScoreEventResponse(
        Long id,
        Long playerId,
        String username,
        Long sessionId,
        String reason,
        Integer points,
        Instant createdAt) {

    public static ScoreEventResponse from(ScoreEvent event) {
        return new ScoreEventResponse(
                event.getId(),
                event.getPlayer().getId(),
                event.getPlayer().getUser().getUsername(),
                event.getGameSession().getId(),
                event.getReason(),
                event.getPoints(),
                event.getCreatedAt());
    }
}

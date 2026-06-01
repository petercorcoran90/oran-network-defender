package com.oran.defender.dto;

import com.oran.defender.model.ScoreEvent;
import java.time.Instant;

/** One entry in a session's score history. */
public record ScoreEventResponse(
        Long id,
        Long playerId,
        String reason,
        Integer points,
        Instant createdAt
) {
    public static ScoreEventResponse from(ScoreEvent e) {
        return new ScoreEventResponse(
                e.getId(),
                e.getPlayer().getId(),
                e.getReason(),
                e.getPoints(),
                e.getCreatedAt());
    }
}

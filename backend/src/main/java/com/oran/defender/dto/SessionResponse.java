package com.oran.defender.dto;

import com.oran.defender.model.GameSession;
import java.time.Instant;

/** Session state for clients — no entity graph, no lazy proxies. */
public record SessionResponse(
        Long id,
        String sessionCode,
        String name,
        String status,
        Integer durationSeconds,
        String difficulty,
        Instant startedAt,
        Instant endedAt,
        Long createdByUserId
) {
    public static SessionResponse from(GameSession s) {
        return new SessionResponse(
                s.getId(),
                s.getSessionCode(),
                s.getName(),
                s.getStatus().name(),
                s.getDurationSeconds(),
                s.getDifficulty().name(),
                s.getStartedAt(),
                s.getEndedAt(),
                s.getCreatedByUser() != null ? s.getCreatedByUser().getId() : null);
    }
}

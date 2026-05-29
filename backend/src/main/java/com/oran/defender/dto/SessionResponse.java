package com.oran.defender.dto;

import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import java.time.Instant;

public record SessionResponse(
        Long id,
        String sessionCode,
        String name,
        SessionStatus status,
        Integer durationSeconds,
        Instant startedAt,
        Instant endedAt,
        Long createdByUserId,
        String createdByUsername) {

    public static SessionResponse from(GameSession session) {
        var creator = session.getCreatedByUser();
        return new SessionResponse(
                session.getId(),
                session.getSessionCode(),
                session.getName(),
                session.getStatus(),
                session.getDurationSeconds(),
                session.getStartedAt(),
                session.getEndedAt(),
                creator == null ? null : creator.getId(),
                creator == null ? null : creator.getUsername());
    }
}

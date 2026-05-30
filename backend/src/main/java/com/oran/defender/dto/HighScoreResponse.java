package com.oran.defender.dto;

import com.oran.defender.model.MatchResult;
import java.time.Instant;

/** One row of the high-score table. */
public record HighScoreResponse(
        String winnerName,
        Integer winnerScore,
        String loserName,
        String difficulty,
        Integer durationSeconds,
        boolean forfeit,
        Instant createdAt
) {
    public static HighScoreResponse from(MatchResult m) {
        return new HighScoreResponse(
                m.getWinnerName(),
                m.getWinnerScore(),
                m.getLoserName(),
                m.getDifficulty(),
                m.getDurationSeconds(),
                m.isForfeit(),
                m.getCreatedAt());
    }
}

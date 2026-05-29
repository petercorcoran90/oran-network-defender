package com.oran.defender.dto;

import com.oran.defender.model.Player;
import java.time.Instant;

public record PlayerResponse(
        Long id,
        Long userId,
        String username,
        Long sessionId,
        String teamName,
        Integer score,
        Instant joinedAt) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(
                player.getId(),
                player.getUser().getId(),
                player.getUser().getUsername(),
                player.getGameSession().getId(),
                player.getTeamName(),
                player.getScore(),
                player.getJoinedAt());
    }
}

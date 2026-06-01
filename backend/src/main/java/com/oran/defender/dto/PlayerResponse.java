package com.oran.defender.dto;

import com.oran.defender.model.Player;

/** A player on the scoreboard / in a session. */
public record PlayerResponse(
        Long id,
        String teamName,
        Integer score,
        boolean ready
) {
    public static PlayerResponse from(Player p) {
        return new PlayerResponse(p.getId(), p.getTeamName(), p.getScore(), p.isReady());
    }
}

package com.oran.defender.service;

import com.oran.defender.model.Player;
import com.oran.defender.model.ScoreEvent;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScoreService {

    // TODO: inject PlayerRepository, ScoreEventRepository

    public List<Player> getScoreboard(Long sessionId) {
        // TODO: return players for session ordered by score desc
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<ScoreEvent> getScoreEvents(Long sessionId) {
        // TODO: return all score events for the session ordered by createdAt
        throw new UnsupportedOperationException("Not implemented");
    }

    public void recordScoreEvent(Long playerId, Long sessionId, String reason, int points) {
        // TODO: persist ScoreEvent, update Player.score
        throw new UnsupportedOperationException("Not implemented");
    }
}

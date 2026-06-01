package com.oran.defender.service;

import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.Player;
import com.oran.defender.model.ScoreEvent;
import com.oran.defender.repository.PlayerRepository;
import com.oran.defender.repository.ScoreEventRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScoreService {

    private final PlayerRepository playerRepository;
    private final ScoreEventRepository scoreEventRepository;

    public ScoreService(PlayerRepository playerRepository,
                        ScoreEventRepository scoreEventRepository) {
        this.playerRepository = playerRepository;
        this.scoreEventRepository = scoreEventRepository;
    }

    @Transactional(readOnly = true)
    public List<Player> getScoreboard(Long sessionId) {
        return playerRepository.findByGameSessionIdOrderByScoreDesc(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ScoreEvent> getScoreEvents(Long sessionId) {
        return scoreEventRepository.findByGameSessionIdOrderByCreatedAtDesc(sessionId);
    }

    /**
     * Records an immutable score delta and updates the player's running total. This is the
     * only place a player's score changes — scores are never set directly from a request,
     * which is what stops a player editing their own or another player's score.
     */
    @Transactional
    public void recordScoreEvent(Long playerId, Long sessionId, String reason, int points) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException("Player not found"));

        ScoreEvent event = new ScoreEvent();
        event.setPlayer(player);
        event.setGameSession(player.getGameSession());
        event.setReason(reason);
        event.setPoints(points);
        scoreEventRepository.save(event);

        player.setScore(player.getScore() + points);
        playerRepository.save(player);
    }
}

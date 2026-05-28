package com.oran.defender.service;

import com.oran.defender.model.GameSession;
import com.oran.defender.model.Player;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SessionService {

    // TODO: inject GameSessionRepository, PlayerRepository, UserRepository

    public GameSession createSession(String name, Long createdByUserId) {
        // TODO: create GameSession with status WAITING, persist, return
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<GameSession> listActiveSessions() {
        // TODO: return sessions with status WAITING or ACTIVE
        throw new UnsupportedOperationException("Not implemented");
    }

    public GameSession getSession(Long sessionId) {
        // TODO: find by ID or throw 404
        throw new UnsupportedOperationException("Not implemented");
    }

    public Player joinSession(Long sessionId, Long userId, String teamName) {
        // TODO: validate session is WAITING, create Player, persist, return
        throw new UnsupportedOperationException("Not implemented");
    }

    public GameSession startSession(Long sessionId) {
        // TODO: validate session is WAITING, set status ACTIVE, set startedAt, persist
        throw new UnsupportedOperationException("Not implemented");
    }

    public List<Player> getPlayers(Long sessionId) {
        // TODO: return players ordered by score desc
        throw new UnsupportedOperationException("Not implemented");
    }
}

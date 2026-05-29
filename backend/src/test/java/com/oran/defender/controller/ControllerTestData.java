package com.oran.defender.controller;

import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.NetworkCell.HealthStatus;
import com.oran.defender.model.Player;
import com.oran.defender.model.PlayerAction;
import com.oran.defender.model.PlayerAction.ActionResult;
import com.oran.defender.model.ScoreEvent;
import java.time.Instant;

final class ControllerTestData {
    static final Instant NOW = Instant.parse("2026-01-01T10:15:30Z");

    private ControllerTestData() {
    }

    static AppUser user(Long id, String username, String role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole(role);
        user.setCreatedAt(NOW);
        return user;
    }

    static GameSession session(Long id, String code, String name, SessionStatus status, AppUser creator) {
        GameSession session = new GameSession();
        session.setId(id);
        session.setSessionCode(code);
        session.setName(name);
        session.setStatus(status);
        session.setDurationSeconds(300);
        session.setCreatedByUser(creator);
        return session;
    }

    static Player player(Long id, AppUser user, GameSession session, String teamName, int score) {
        Player player = new Player();
        player.setId(id);
        player.setUser(user);
        player.setGameSession(session);
        player.setTeamName(teamName);
        player.setScore(score);
        player.setJoinedAt(NOW);
        return player;
    }

    static NetworkCell cell(Long id, String name, HealthStatus healthStatus) {
        AppUser user = user(3L, "operator", "PLAYER");
        GameSession session = session(11L, "ABC123", "Training", SessionStatus.ACTIVE, user);
        Player player = player(21L, user, session, "Blue", 25);

        NetworkCell cell = new NetworkCell();
        cell.setId(id);
        cell.setGameSession(session);
        cell.setPlayer(player);
        cell.setCellName(name);
        cell.setSignalQuality(87.5);
        cell.setUserLoad(44.0);
        cell.setLatency(12.5);
        cell.setPacketLoss(0.1);
        cell.setAlarmCount(2);
        cell.setEnergyUsage(55.5);
        cell.setHealthStatus(healthStatus);
        return cell;
    }

    static PlayerAction playerAction(Long id, ActionResult result, int pointsAwarded) {
        PlayerAction playerAction = new PlayerAction();
        playerAction.setId(id);
        playerAction.setResult(result);
        playerAction.setPointsAwarded(pointsAwarded);
        playerAction.setSubmittedAt(NOW);
        return playerAction;
    }

    static ScoreEvent scoreEvent(Long id, String reason, int points) {
        ScoreEvent event = new ScoreEvent();
        event.setId(id);
        event.setReason(reason);
        event.setPoints(points);
        event.setCreatedAt(NOW);
        return event;
    }
}

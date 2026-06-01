package com.oran.defender.integration;

import com.oran.defender.engine.RootCause;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.Difficulty;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import com.oran.defender.model.Incident.Severity;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.model.Player;
import java.time.Instant;

/** Detached-entity builders for integration tests. The caller saves them via repositories. */
final class Fixtures {

    private Fixtures() {}

    static AppUser user(String username) {
        AppUser u = new AppUser();
        u.setUsername(username);
        u.setRole("PLAYER");
        return u;
    }

    static GameSession activeSession(String code, AppUser creator) {
        GameSession s = new GameSession();
        s.setSessionCode(code);
        s.setName("Integration match");
        s.setStatus(SessionStatus.ACTIVE);
        s.setDifficulty(Difficulty.MEDIUM);
        s.setDurationSeconds(300);
        s.setCreatedByUser(creator);
        return s;
    }

    static Player player(AppUser user, GameSession session, String team) {
        Player p = new Player();
        p.setUser(user);
        p.setGameSession(session);
        p.setTeamName(team);
        p.setScore(0);
        p.setReady(true);
        p.setJoinedAt(Instant.now());
        return p;
    }

    static NetworkCell cell(GameSession session, Player player, String name) {
        NetworkCell c = new NetworkCell();
        c.setGameSession(session);
        c.setPlayer(player);
        c.setCellName(name);
        return c; // metric/health defaults from the entity are fine
    }

    static Incident openIncident(GameSession session, Player player, NetworkCell cell,
                                 String type, RootCause rootCause, Severity severity) {
        Incident i = new Incident();
        i.setGameSession(session);
        i.setPlayer(player);
        i.setCell(cell);
        i.setIncidentType(type);
        i.setSeverity(severity);
        i.setStatus(IncidentStatus.OPEN);
        i.setDescription(type + " on " + cell.getCellName());
        i.setRootCause(rootCause.name());
        i.setCreatedAt(Instant.now());
        return i;
    }
}

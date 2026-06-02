package com.oran.defender.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.InvalidActionException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.Difficulty;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Player;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.MatchResultRepository;
import com.oran.defender.repository.PlayerRepository;
import com.oran.defender.service.SessionService;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DisplayName("SessionService lifecycle (MySQL Testcontainer)")
class SessionServiceIntegrationTest extends AbstractMySqlIntegrationTest {

    @Autowired private SessionService sessionService;
    @Autowired private AppUserRepository users;
    @Autowired private GameSessionRepository sessions;
    @Autowired private PlayerRepository players;
    @Autowired private MatchResultRepository matchResults;

    @Test
    @DisplayName("createSession defaults duration, normalizes difficulty, and rejects unknown difficulty")
    void createSessionDefaultsAndValidatesDifficulty() {
        AppUser creator = users.save(Fixtures.user("session-create"));

        GameSession created = sessionService.createSession("Ops match", creator.getId(), null, "hard");

        assertThat(created.getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(created.getDurationSeconds()).isEqualTo(300);
        assertThat(created.getDifficulty()).isEqualTo(Difficulty.HARD);
        assertThat(created.getSessionCode()).hasSize(6);
        assertThat(created.getCreatedByUser().getId()).isEqualTo(creator.getId());

        Long creatorId = creator.getId();
        assertThatThrownBy(() -> sessionService.createSession("Bad", creatorId, 60, "nightmare"))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("Unknown difficulty");
        assertThatThrownBy(() -> sessionService.createSession("Missing", 999_999L, 60, "EASY"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("joinSession applies username fallback and rejects duplicates, full sessions, and active sessions")
    void joinSessionGuardsCapacityAndState() {
        AppUser creator = users.save(Fixtures.user("session-join-creator"));
        AppUser second = users.save(Fixtures.user("session-join-second"));
        AppUser third = users.save(Fixtures.user("session-join-third"));
        GameSession waiting = sessionService.createSession("Joinable", creator.getId(), 300, "MEDIUM");

        Player p1 = sessionService.joinSession(waiting.getId(), creator.getId(), " ");
        assertThat(p1.getTeamName()).isEqualTo("session-join-creator");

        Long waitingId = waiting.getId();
        Long creatorId2 = creator.getId();
        Long thirdId = third.getId();
        assertThatThrownBy(() -> sessionService.joinSession(waitingId, creatorId2, "Blue"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already joined");

        sessionService.joinSession(waiting.getId(), second.getId(), "Red");
        assertThatThrownBy(() -> sessionService.joinSession(waitingId, thirdId, "Green"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("full");

        GameSession active = sessions.save(Fixtures.activeSession("JOIN01", creator));
        Long activeId = active.getId();
        assertThatThrownBy(() -> sessionService.joinSession(activeId, thirdId, "Late"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not accepting");
    }

    @Test
    @DisplayName("readying both players activates the match and rejects players from other sessions")
    void markReadyActivatesAndValidatesMembership() {
        AppUser creator = users.save(Fixtures.user("session-ready-a"));
        AppUser opponent = users.save(Fixtures.user("session-ready-b"));
        AppUser outsider = users.save(Fixtures.user("session-ready-outsider"));
        GameSession session = sessionService.createSession("Ready", creator.getId(), 120, "EASY");
        Player p1 = sessionService.joinSession(session.getId(), creator.getId(), "Blue");
        Player p2 = sessionService.joinSession(session.getId(), opponent.getId(), "Red");
        GameSession other = sessionService.createSession("Other", outsider.getId(), 120, "EASY");
        Player otherPlayer = sessionService.joinSession(other.getId(), outsider.getId(), "Green");

        assertThat(sessionService.markReady(session.getId(), p1.getId()).getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(players.findById(p1.getId()).orElseThrow().isReady()).isTrue();

        GameSession active = sessionService.markReady(session.getId(), p2.getId());
        assertThat(active.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(active.getStartedAt()).isNotNull();
        assertThat(active.getEndedAt()).isAfter(active.getStartedAt());

        Long sessionId = session.getId();
        Long otherPlayerId = otherPlayer.getId();
        Long otherId = other.getId();
        Long p1Id = p1.getId();
        assertThatThrownBy(() -> sessionService.markReady(sessionId, otherPlayerId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already started");
        assertThatThrownBy(() -> sessionService.markReady(otherId, p1Id))
                .isInstanceOf(InvalidActionException.class)
                .hasMessageContaining("not part");
    }

    @Test
    @DisplayName("startSession requires two players and cannot restart an active match")
    void startSessionGuardsPreconditions() {
        AppUser creator = users.save(Fixtures.user("session-start-a"));
        AppUser opponent = users.save(Fixtures.user("session-start-b"));
        GameSession session = sessionService.createSession("Manual", creator.getId(), 90, "MEDIUM");
        sessionService.joinSession(session.getId(), creator.getId(), "Blue");

        Long startSessionId = session.getId();
        assertThatThrownBy(() -> sessionService.startSession(startSessionId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("needs 2 players");

        sessionService.joinSession(session.getId(), opponent.getId(), "Red");
        assertThat(sessionService.startSession(session.getId()).getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThatThrownBy(() -> sessionService.startSession(startSessionId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot be started");
    }

    @Test
    @DisplayName("lookup helpers normalize codes, reject missing sessions, and order players by score")
    void lookupHelpers() {
        AppUser creator = users.save(Fixtures.user("session-lookup-a"));
        AppUser opponent = users.save(Fixtures.user("session-lookup-b"));
        GameSession session = sessionService.createSession("Lookup", creator.getId(), 300, null);
        Player p1 = sessionService.joinSession(session.getId(), creator.getId(), "Blue");
        Player p2 = sessionService.joinSession(session.getId(), opponent.getId(), "Red");
        p1.setScore(10);
        p2.setScore(50);
        players.save(p1);
        players.save(p2);

        assertThat(sessionService.getByCode("  " + session.getSessionCode().toLowerCase() + "  ").getId())
                .isEqualTo(session.getId());
        assertThat(sessionService.getPlayers(session.getId())).extracting(Player::getId)
                .containsExactly(p2.getId(), p1.getId());
        assertThatThrownBy(() -> sessionService.getSession(999_999L))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> sessionService.getPlayers(999_999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("listActiveSessions lazily ends expired active sessions and records the winner")
    void listActiveSessionsEndsExpiredMatches() {
        AppUser creator = users.save(Fixtures.user("session-expired-a"));
        AppUser opponent = users.save(Fixtures.user("session-expired-b"));
        GameSession expired = sessions.save(Fixtures.activeSession("EXP001", creator));
        expired.setStartedAt(Instant.now().minusSeconds(300));
        expired.setEndedAt(Instant.now().minusSeconds(1));
        expired = sessions.save(expired);
        GameSession waiting = sessionService.createSession("Still waiting", creator.getId(), 300, "MEDIUM");

        Player p1 = players.save(Fixtures.player(creator, expired, "Blue"));
        Player p2 = players.save(Fixtures.player(opponent, expired, "Red"));
        p1.setScore(80);
        p2.setScore(40);
        players.save(p1);
        players.save(p2);

        Long expiredId = expired.getId();
        assertThat(sessionService.listActiveSessions()).extracting(GameSession::getId)
                .contains(waiting.getId())
                .doesNotContain(expiredId);
        assertThat(sessions.findById(expiredId).orElseThrow().getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(matchResults.findTop20ByOrderByWinnerScoreDesc())
                .anySatisfy(r -> {
                    assertThat(r.getGameSessionId()).isEqualTo(expiredId);
                    assertThat(r.getWinnerName()).isEqualTo("Blue");
                    assertThat(r.isForfeit()).isFalse();
                });
    }

    @Test
    @DisplayName("leaveSession records a scored forfeit once and leaves ended sessions unchanged")
    void leaveSessionRecordsForfeitOnce() {
        AppUser creator = users.save(Fixtures.user("session-forfeit-a"));
        AppUser opponent = users.save(Fixtures.user("session-forfeit-b"));
        GameSession session = sessionService.createSession("Forfeit", creator.getId(), 300, "HARD");
        Player p1 = sessionService.joinSession(session.getId(), creator.getId(), "Blue");
        Player p2 = sessionService.joinSession(session.getId(), opponent.getId(), "Red");
        sessionService.markReady(session.getId(), p1.getId());
        sessionService.markReady(session.getId(), p2.getId());
        p1.setScore(120);
        p2.setScore(30);
        players.save(p1);
        players.save(p2);

        GameSession ended = sessionService.leaveSession(session.getId(), p2.getId());
        assertThat(ended.getStatus()).isEqualTo(SessionStatus.ENDED);
        assertThat(ended.getForfeitedByPlayerId()).isEqualTo(p2.getId());
        sessionService.leaveSession(session.getId(), p2.getId());

        assertThat(matchResults.findTop20ByOrderByWinnerScoreDesc())
                .filteredOn(r -> r.getGameSessionId().equals(session.getId()))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getWinnerName()).isEqualTo("Blue");
                    assertThat(r.getLoserName()).isEqualTo("Red");
                    assertThat(r.getWinnerScore()).isEqualTo(120);
                    assertThat(r.getDifficulty()).isEqualTo("HARD");
                    assertThat(r.isForfeit()).isTrue();
                });
    }
}

package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.exception.ConflictException;
import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Player;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.PlayerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private GameSessionRepository sessionRepository;

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private SessionService sessionService;

    // -- createSession --------------------------------------------------------

    @Test
    void createSession_savesWaitingSessionWithCustomDuration() {
        AppUser creator = userWith(1L, "alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(sessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSession result = sessionService.createSession("Match 1", 1L, 600);

        assertThat(result.getName()).isEqualTo("Match 1");
        assertThat(result.getCreatedByUser()).isSameAs(creator);
        assertThat(result.getStatus()).isEqualTo(SessionStatus.WAITING);
        assertThat(result.getDurationSeconds()).isEqualTo(600);
        assertThat(result.getSessionCode()).hasSize(6);
        verify(sessionRepository).save(any(GameSession.class));
    }

    @Test
    void createSession_usesDefaultDuration_whenDurationIsNull() {
        AppUser creator = userWith(1L, "alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(sessionRepository.findBySessionCode(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.save(any(GameSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSession result = sessionService.createSession("Match 1", 1L, null);

        assertThat(result.getDurationSeconds()).isEqualTo(300);
    }

    @Test
    void createSession_throwsNotFound_whenCreatorDoesNotExist() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.createSession("Match 1", 99L, 300))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");

        verify(sessionRepository, never()).save(any());
    }

    // -- listActiveSessions ---------------------------------------------------

    @Test
    void listActiveSessions_returnsWaitingAndActiveSessions() {
        List<GameSession> sessions = List.of(sessionWith(1L, SessionStatus.WAITING),
                sessionWith(2L, SessionStatus.ACTIVE));
        when(sessionRepository.findByStatusIn(List.of(SessionStatus.WAITING, SessionStatus.ACTIVE)))
                .thenReturn(sessions);

        List<GameSession> result = sessionService.listActiveSessions();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(GameSession::getId).containsExactly(1L, 2L);
    }

    // -- getSession -----------------------------------------------------------

    @Test
    void getSession_returnsSession_whenFound() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        GameSession result = sessionService.getSession(10L);

        assertThat(result).isSameAs(session);
    }

    @Test
    void getSession_marksActiveSessionEnded_whenExpired() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        session.setEndedAt(Instant.now().minusSeconds(1));
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        GameSession result = sessionService.getSession(10L);

        assertThat(result.getStatus()).isEqualTo(SessionStatus.ENDED);
        verify(sessionRepository).save(session);
    }

    @Test
    void getSession_throwsNotFound_whenSessionDoesNotExist() {
        when(sessionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getSession(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Session not found");
    }

    // -- joinSession ----------------------------------------------------------

    @Test
    void joinSession_savesPlayerWithProvidedTeamName() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        AppUser user = userWith(5L, "bob");
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(playerRepository.findByUserIdAndGameSessionId(5L, 10L)).thenReturn(Optional.empty());
        when(playerRepository.countByGameSessionId(10L)).thenReturn(0L);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Player result = sessionService.joinSession(10L, 5L, "Blue Team");

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getGameSession()).isSameAs(session);
        assertThat(result.getTeamName()).isEqualTo("Blue Team");
        assertThat(result.getScore()).isZero();
    }

    @Test
    void joinSession_usesUsername_whenTeamNameIsBlank() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        AppUser user = userWith(5L, "bob");
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(playerRepository.findByUserIdAndGameSessionId(5L, 10L)).thenReturn(Optional.empty());
        when(playerRepository.countByGameSessionId(10L)).thenReturn(0L);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Player result = sessionService.joinSession(10L, 5L, " ");

        assertThat(result.getTeamName()).isEqualTo("bob");
    }

    @Test
    void joinSession_autoStartsSession_whenSecondPlayerJoins() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        session.setDurationSeconds(300);
        AppUser user = userWith(5L, "bob");
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(playerRepository.findByUserIdAndGameSessionId(5L, 10L)).thenReturn(Optional.empty());
        when(playerRepository.countByGameSessionId(10L)).thenReturn(1L);
        when(playerRepository.save(any(Player.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.joinSession(10L, 5L, "Blue Team");

        assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(session.getStartedAt()).isNotNull();
        assertThat(session.getEndedAt()).isAfter(session.getStartedAt());
        verify(sessionRepository).save(session);
    }

    @Test
    void joinSession_throwsConflict_whenSessionIsNotWaiting() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.joinSession(10L, 5L, "Blue Team"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Session is not accepting players");

        verify(playerRepository, never()).save(any());
    }

    @Test
    void joinSession_throwsConflict_whenUserAlreadyJoined() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        AppUser user = userWith(5L, "bob");
        Player existing = playerWith(7L, session, user, 20);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(playerRepository.findByUserIdAndGameSessionId(5L, 10L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> sessionService.joinSession(10L, 5L, "Blue Team"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User has already joined this session");

        verify(playerRepository, never()).save(any());
    }

    @Test
    void joinSession_throwsConflict_whenSessionIsFull() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        AppUser user = userWith(5L, "bob");
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(playerRepository.findByUserIdAndGameSessionId(5L, 10L)).thenReturn(Optional.empty());
        when(playerRepository.countByGameSessionId(10L)).thenReturn(2L);

        assertThatThrownBy(() -> sessionService.joinSession(10L, 5L, "Blue Team"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Session is full");

        verify(playerRepository, never()).save(any());
    }

    // -- startSession ---------------------------------------------------------

    @Test
    void startSession_setsSessionActive_whenEnoughPlayersExist() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        session.setDurationSeconds(300);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(playerRepository.countByGameSessionId(10L)).thenReturn(2L);
        when(sessionRepository.save(session)).thenReturn(session);

        GameSession result = sessionService.startSession(10L);

        assertThat(result.getStatus()).isEqualTo(SessionStatus.ACTIVE);
        assertThat(result.getStartedAt()).isNotNull();
        assertThat(result.getEndedAt()).isAfter(result.getStartedAt());
        verify(sessionRepository).save(session);
    }

    @Test
    void startSession_throwsConflict_whenNotEnoughPlayersExist() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(playerRepository.countByGameSessionId(10L)).thenReturn(1L);

        assertThatThrownBy(() -> sessionService.startSession(10L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Session needs 2 players to start");

        verify(sessionRepository, never()).save(session);
    }

    @Test
    void startSession_throwsConflict_whenSessionIsNotWaiting() {
        GameSession session = sessionWith(10L, SessionStatus.ACTIVE);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> sessionService.startSession(10L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Session cannot be started");

        verify(playerRepository, never()).countByGameSessionId(10L);
        verify(sessionRepository, never()).save(session);
    }

    // -- getPlayers -----------------------------------------------------------

    @Test
    void getPlayers_returnsPlayersOrderedByScore_whenSessionExists() {
        GameSession session = sessionWith(10L, SessionStatus.WAITING);
        AppUser alice = userWith(1L, "alice");
        AppUser bob = userWith(2L, "bob");
        List<Player> players = List.of(playerWith(1L, session, alice, 90), playerWith(2L, session, bob, 50));
        when(sessionRepository.existsById(10L)).thenReturn(true);
        when(playerRepository.findByGameSessionIdOrderByScoreDesc(10L)).thenReturn(players);

        List<Player> result = sessionService.getPlayers(10L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Player::getScore).containsExactly(90, 50);
    }

    @Test
    void getPlayers_throwsNotFound_whenSessionDoesNotExist() {
        when(sessionRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> sessionService.getPlayers(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Session not found");

        verify(playerRepository, never()).findByGameSessionIdOrderByScoreDesc(99L);
    }

    // -- helpers --------------------------------------------------------------

    private AppUser userWith(Long id, String username) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername(username);
        user.setRole("PLAYER");
        return user;
    }

    private GameSession sessionWith(Long id, SessionStatus status) {
        GameSession session = new GameSession();
        session.setId(id);
        session.setName("Match " + id);
        session.setSessionCode("ABC123");
        session.setStatus(status);
        session.setDurationSeconds(300);
        return session;
    }

    private Player playerWith(Long id, GameSession session, AppUser user, Integer score) {
        Player player = new Player();
        player.setId(id);
        player.setGameSession(session);
        player.setUser(user);
        player.setTeamName(user.getUsername());
        player.setScore(score);
        return player;
    }
}

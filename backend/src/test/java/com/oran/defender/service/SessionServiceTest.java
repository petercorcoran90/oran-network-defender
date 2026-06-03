package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.AppUser;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.Difficulty;
import com.oran.defender.model.GameSession.SessionStatus;
import com.oran.defender.model.Player;
import com.oran.defender.model.UserSkill;
import com.oran.defender.repository.AppUserRepository;
import com.oran.defender.repository.GameSessionRepository;
import com.oran.defender.repository.MatchResultRepository;
import com.oran.defender.repository.PlayerRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService unit tests")
class SessionServiceTest {

    @Mock private GameSessionRepository sessionRepository;
    @Mock private PlayerRepository playerRepository;
    @Mock private AppUserRepository userRepository;
    @Mock private MatchResultRepository matchResultRepository;
    @Mock private ProgressionService progressionService;

    private SessionService sessionService;

    private GameSession session;
    private Player winner;
    private Player loser;

    @BeforeEach
    void setUp() {
        sessionService = new SessionService(
                sessionRepository, playerRepository, userRepository, matchResultRepository,
                progressionService);

        AppUser creatorUser = new AppUser();
        creatorUser.setId(1L);
        creatorUser.setUsername("creator");

        session = new GameSession();
        session.setId(10L);
        session.setStatus(SessionStatus.ACTIVE);
        session.setDifficulty(Difficulty.MEDIUM);
        session.setDurationSeconds(300);
        session.setStartedAt(Instant.now().minusSeconds(60));
        session.setEndedAt(Instant.now().plusSeconds(240));
        session.setCreatedByUser(creatorUser);

        winner = new Player();
        winner.setId(100L);
        winner.setTeamName("Blue");
        winner.setScore(80);
        winner.setGameSession(session);

        loser = new Player();
        loser.setId(200L);
        loser.setTeamName("Red");
        loser.setScore(40);
        loser.setGameSession(session);
    }

    // --- getByCode ---

    @Test
    @DisplayName("getByCode returns session when code matches (trimmed and uppercased)")
    void getByCode_returnsSession() {
        when(sessionRepository.findBySessionCode("ABC123")).thenReturn(Optional.of(session));

        GameSession result = sessionService.getByCode("  abc123  ");

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getByCode throws NotFoundException when code does not match")
    void getByCode_notFound() {
        when(sessionRepository.findBySessionCode("XXXXXX")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.getByCode("xxxxxx"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No match with that code");
    }

    // --- recordResult (via leaveSession) ---

    @Test
    @DisplayName("leaveSession skips recordResult when session has no startedAt")
    void leaveSession_skipsRecordResult_whenNotStarted() {
        session.setStartedAt(null);
        session.setStatus(SessionStatus.WAITING);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);

        sessionService.leaveSession(10L, 100L);

        verify(matchResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("leaveSession skips recordResult when result already exists")
    void leaveSession_skipsRecordResult_whenAlreadyRecorded() {
        session.setStatus(SessionStatus.WAITING);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(matchResultRepository.existsByGameSessionId(10L)).thenReturn(true);

        sessionService.leaveSession(10L, 100L);

        verify(matchResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("leaveSession skips recordResult when fewer than 2 players")
    void leaveSession_skipsRecordResult_whenFewerThanTwoPlayers() {
        session.setStatus(SessionStatus.WAITING);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(matchResultRepository.existsByGameSessionId(10L)).thenReturn(false);
        when(playerRepository.findByGameSessionIdOrderByScoreDesc(10L)).thenReturn(List.of(winner));

        sessionService.leaveSession(10L, 100L);

        verify(matchResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("leaveSession skips recordResult when forfeit but all scores are zero")
    void leaveSession_skipsRecordResult_whenForfeitWithZeroScores() {
        winner.setScore(0);
        loser.setScore(0);
        session.setStatus(SessionStatus.WAITING);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(matchResultRepository.existsByGameSessionId(10L)).thenReturn(false);
        when(playerRepository.findByGameSessionIdOrderByScoreDesc(10L))
                .thenReturn(List.of(winner, loser));

        sessionService.leaveSession(10L, loser.getId());

        verify(matchResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("leaveSession saves MatchResult with correct forfeit winner")
    void leaveSession_savesMatchResult_forfeit() {
        session.setStatus(SessionStatus.WAITING);
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(session);
        when(matchResultRepository.existsByGameSessionId(10L)).thenReturn(false);
        when(playerRepository.findByGameSessionIdOrderByScoreDesc(10L))
                .thenReturn(List.of(winner, loser));

        sessionService.leaveSession(10L, loser.getId());

        verify(matchResultRepository).save(any());
    }

    // --- createTrainingSession (solo curriculum mode) ---

    @Test
    @DisplayName("createTrainingSession starts a solo ACTIVE session at the player's tier")
    void createTrainingSession_startsSoloMatch() {
        AppUser user = new AppUser();
        user.setId(7L);
        user.setUsername("ava");
        Player savedPlayer = new Player();
        savedPlayer.setId(50L);

        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(progressionService.getOrCreate(7L)).thenReturn(new UserSkill()); // nothing learned -> TRAINEE
        when(sessionRepository.findBySessionCode(any())).thenReturn(Optional.empty()); // code is unique
        when(playerRepository.save(any(Player.class))).thenReturn(savedPlayer);

        Player result = sessionService.createTrainingSession(7L, 300);

        assertThat(result.getId()).isEqualTo(50L);

        ArgumentCaptor<GameSession> captor = ArgumentCaptor.forClass(GameSession.class);
        verify(sessionRepository, atLeastOnce()).save(captor.capture());
        GameSession created = captor.getValue();
        assertThat(created.getMode()).isEqualTo(GameSession.Mode.TRAINING);
        assertThat(created.getStatus()).isEqualTo(SessionStatus.ACTIVE); // starts immediately, no opponent
        assertThat(created.getDifficulty()).isEqualTo(Difficulty.EASY);  // TRAINEE -> EASY
        assertThat(created.getEndedAt()).isNull();                       // training has no time limit
    }

    @Test
    @DisplayName("createTrainingSession throws when the user does not exist")
    void createTrainingSession_userNotFound() {
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sessionService.createTrainingSession(7L, 300))
                .isInstanceOf(NotFoundException.class);
    }
}

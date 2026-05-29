package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.Player;
import com.oran.defender.model.ScoreEvent;
import com.oran.defender.repository.PlayerRepository;
import com.oran.defender.repository.ScoreEventRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScoreServiceTest {

    @Mock
    private PlayerRepository playerRepository;

    @Mock
    private ScoreEventRepository scoreEventRepository;

    @InjectMocks
    private ScoreService scoreService;

    // -- getScoreboard --------------------------------------------------------

    @Test
    void getScoreboard_returnsPlayersOrderedByScore() {
        List<Player> players = List.of(playerInSession(1L, 10L, 120), playerInSession(2L, 10L, 80));
        when(playerRepository.findByGameSessionIdOrderByScoreDesc(10L)).thenReturn(players);

        List<Player> result = scoreService.getScoreboard(10L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Player::getId).containsExactly(1L, 2L);
        assertThat(result).extracting(Player::getScore).containsExactly(120, 80);
    }

    @Test
    void getScoreboard_returnsEmptyList_whenNoPlayersExist() {
        when(playerRepository.findByGameSessionIdOrderByScoreDesc(10L)).thenReturn(List.of());

        assertThat(scoreService.getScoreboard(10L)).isEmpty();
    }

    // -- getScoreEvents -------------------------------------------------------

    @Test
    void getScoreEvents_returnsEventsForSession() {
        List<ScoreEvent> events = List.of(scoreEvent(1L, 10L, "Resolved incident", 50),
                scoreEvent(2L, 10L, "Fast response", 25));
        when(scoreEventRepository.findByGameSessionIdOrderByCreatedAtDesc(10L)).thenReturn(events);

        List<ScoreEvent> result = scoreService.getScoreEvents(10L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ScoreEvent::getId).containsExactly(1L, 2L);
        assertThat(result).extracting(ScoreEvent::getPoints).containsExactly(50, 25);
    }

    @Test
    void getScoreEvents_returnsEmptyList_whenNoEventsExist() {
        when(scoreEventRepository.findByGameSessionIdOrderByCreatedAtDesc(10L)).thenReturn(List.of());

        assertThat(scoreService.getScoreEvents(10L)).isEmpty();
    }

    // -- recordScoreEvent -----------------------------------------------------

    @Test
    void recordScoreEvent_savesEventAndUpdatesPlayerScore() {
        Player player = playerInSession(5L, 10L, 40);
        when(playerRepository.findById(5L)).thenReturn(Optional.of(player));

        scoreService.recordScoreEvent(5L, 10L, "Correct action", 30);

        ArgumentCaptor<ScoreEvent> eventCaptor = ArgumentCaptor.forClass(ScoreEvent.class);
        verify(scoreEventRepository).save(eventCaptor.capture());
        ScoreEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getPlayer()).isSameAs(player);
        assertThat(savedEvent.getGameSession().getId()).isEqualTo(10L);
        assertThat(savedEvent.getReason()).isEqualTo("Correct action");
        assertThat(savedEvent.getPoints()).isEqualTo(30);

        assertThat(player.getScore()).isEqualTo(70);
        verify(playerRepository).save(player);
    }

    @Test
    void recordScoreEvent_throwsNotFound_whenPlayerDoesNotExist() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scoreService.recordScoreEvent(99L, 10L, "Correct action", 30))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Player not found");

        verify(scoreEventRepository, never()).save(any());
        verify(playerRepository, never()).save(any());
    }

    // -- helpers --------------------------------------------------------------

    private Player playerInSession(Long playerId, Long sessionId, Integer score) {
        GameSession session = new GameSession();
        session.setId(sessionId);

        Player player = new Player();
        player.setId(playerId);
        player.setGameSession(session);
        player.setScore(score);
        return player;
    }

    private ScoreEvent scoreEvent(Long eventId, Long sessionId, String reason, Integer points) {
        ScoreEvent event = new ScoreEvent();
        event.setId(eventId);
        event.setGameSession(sessionWith(sessionId));
        event.setReason(reason);
        event.setPoints(points);
        return event;
    }

    private GameSession sessionWith(Long sessionId) {
        GameSession session = new GameSession();
        session.setId(sessionId);
        return session;
    }
}

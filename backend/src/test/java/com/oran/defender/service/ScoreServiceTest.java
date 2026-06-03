package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreService unit tests")
class ScoreServiceTest {

    @Mock private PlayerRepository playerRepository;
    @Mock private ScoreEventRepository scoreEventRepository;

    private ScoreService scoreService;

    private Player player;

    @BeforeEach
    void setUp() {
        scoreService = new ScoreService(playerRepository, scoreEventRepository);

        GameSession session = new GameSession();
        session.setId(1L);

        player = new Player();
        player.setId(10L);
        player.setScore(50);
        player.setGameSession(session);
    }

    @Test
    @DisplayName("getScoreboard returns players ordered by score from repository")
    void getScoreboard_returnsPlayers() {
        when(playerRepository.findByGameSessionIdOrderByScoreDesc(1L))
                .thenReturn(List.of(player));

        List<Player> result = scoreService.getScoreboard(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getScoreEvents returns events from repository")
    void getScoreEvents_returnsEvents() {
        ScoreEvent event = new ScoreEvent();
        event.setId(100L);
        when(scoreEventRepository.findByGameSessionIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(event));

        List<ScoreEvent> result = scoreService.getScoreEvents(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("recordScoreEvent saves event and updates player score")
    void recordScoreEvent_savesEventAndUpdatesScore() {
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        scoreService.recordScoreEvent(10L, 1L, "correct action", 30);

        verify(scoreEventRepository).save(any(ScoreEvent.class));
        verify(playerRepository).save(player);
        assertThat(player.getScore()).isEqualTo(80);
    }

    @Test
    @DisplayName("recordScoreEvent applies negative points correctly")
    void recordScoreEvent_negativePoints() {
        when(playerRepository.findById(10L)).thenReturn(Optional.of(player));

        scoreService.recordScoreEvent(10L, 1L, "trap action", -20);

        assertThat(player.getScore()).isEqualTo(30);
    }

    @Test
    @DisplayName("recordScoreEvent throws NotFoundException when player does not exist")
    void recordScoreEvent_playerNotFound() {
        when(playerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> scoreService.recordScoreEvent(99L, 1L, "reason", 10))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Player not found");
    }
}

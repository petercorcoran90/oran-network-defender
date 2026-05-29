package com.oran.defender.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.GameSession;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.repository.NetworkCellRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkCellServiceTest {

    @Mock
    private NetworkCellRepository cellRepository;

    @InjectMocks
    private NetworkCellService networkCellService;

    // ── getCells ──────────────────────────────────────────────────────────────

    @Test
    void getCells_returnsAllCellsForSession() {
        List<NetworkCell> cells = List.of(cellInSession(1L, 10L), cellInSession(2L, 10L));
        when(cellRepository.findByGameSessionId(10L)).thenReturn(cells);

        List<NetworkCell> result = networkCellService.getCells(10L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(NetworkCell::getId).containsExactly(1L, 2L);
    }

    @Test
    void getCells_returnsEmptyList_whenNoCellsExist() {
        when(cellRepository.findByGameSessionId(10L)).thenReturn(List.of());

        assertThat(networkCellService.getCells(10L)).isEmpty();
    }

    // ── getCell ───────────────────────────────────────────────────────────────

    @Test
    void getCell_returnsCell_whenFoundInCorrectSession() {
        NetworkCell cell = cellInSession(5L, 10L);
        when(cellRepository.findById(5L)).thenReturn(Optional.of(cell));

        NetworkCell result = networkCellService.getCell(10L, 5L);

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getGameSession().getId()).isEqualTo(10L);
    }

    @Test
    void getCell_throwsNotFound_whenCellDoesNotExist() {
        when(cellRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> networkCellService.getCell(10L, 99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Cell not found");
    }

    @Test
    void getCell_throwsNotFound_whenCellBelongsToADifferentSession() {
        // Cell is in session 20, but caller is asking for session 10.
        NetworkCell cell = cellInSession(5L, 20L);
        when(cellRepository.findById(5L)).thenReturn(Optional.of(cell));

        assertThatThrownBy(() -> networkCellService.getCell(10L, 5L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Cell not found");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private NetworkCell cellInSession(Long cellId, Long sessionId) {
        GameSession session = new GameSession();
        session.setId(sessionId);

        NetworkCell cell = new NetworkCell();
        cell.setId(cellId);
        cell.setGameSession(session);
        cell.setCellName("Cell-" + cellId);
        return cell;
    }
}

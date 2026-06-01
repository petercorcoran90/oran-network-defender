package com.oran.defender.service;

import com.oran.defender.exception.NotFoundException;
import com.oran.defender.model.NetworkCell;
import com.oran.defender.repository.NetworkCellRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NetworkCellService {

    private final NetworkCellRepository cellRepository;

    public NetworkCellService(NetworkCellRepository cellRepository) {
        this.cellRepository = cellRepository;
    }

    @Transactional(readOnly = true)
    public List<NetworkCell> getCells(Long sessionId, Long playerId) {
        // In head-to-head a player should see their own network; without a playerId
        // (e.g. an admin/overview view) return every cell in the session.
        if (playerId != null) {
            return cellRepository.findByPlayerId(playerId);
        }
        return cellRepository.findByGameSessionId(sessionId);
    }

    @Transactional(readOnly = true)
    public NetworkCell getCell(Long sessionId, Long cellId) {
        NetworkCell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new NotFoundException("Cell not found"));
        // Don't reveal cells from other sessions — treat a mismatch as "not found".
        if (!cell.getGameSession().getId().equals(sessionId)) {
            throw new NotFoundException("Cell not found");
        }
        return cell;
    }
}

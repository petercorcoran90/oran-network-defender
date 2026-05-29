package com.oran.defender.repository;

import com.oran.defender.model.NetworkCell;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NetworkCellRepository extends JpaRepository<NetworkCell, Long> {
    List<NetworkCell> findByGameSessionId(Long gameSessionId);

    List<NetworkCell> findByPlayerId(Long playerId);
}

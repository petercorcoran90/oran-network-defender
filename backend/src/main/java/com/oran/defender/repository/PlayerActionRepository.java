package com.oran.defender.repository;

import com.oran.defender.model.PlayerAction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerActionRepository extends JpaRepository<PlayerAction, Long> {
    List<PlayerAction> findByPlayerId(Long playerId);

    List<PlayerAction> findByIncidentId(Long incidentId);
}

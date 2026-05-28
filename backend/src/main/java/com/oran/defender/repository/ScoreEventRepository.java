package com.oran.defender.repository;

import com.oran.defender.model.ScoreEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoreEventRepository extends JpaRepository<ScoreEvent, Long> {
    List<ScoreEvent> findByGameSessionIdOrderByCreatedAtDesc(Long gameSessionId);

    List<ScoreEvent> findByPlayerIdOrderByCreatedAtDesc(Long playerId);
}

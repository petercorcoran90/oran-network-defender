package com.oran.defender.repository;

import com.oran.defender.model.Player;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    List<Player> findByGameSessionIdOrderByScoreDesc(Long gameSessionId);

    Optional<Player> findByUserIdAndGameSessionId(Long userId, Long gameSessionId);
}

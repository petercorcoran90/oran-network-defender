package com.oran.defender.repository;

import com.oran.defender.model.GameSession;
import com.oran.defender.model.GameSession.SessionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameSessionRepository extends JpaRepository<GameSession, Long> {
    Optional<GameSession> findBySessionCode(String sessionCode);

    List<GameSession> findByStatusIn(Collection<SessionStatus> statuses);
}

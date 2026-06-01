package com.oran.defender.repository;

import com.oran.defender.model.MatchResult;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    boolean existsByGameSessionId(Long gameSessionId);

    List<MatchResult> findTop20ByOrderByWinnerScoreDesc();
}

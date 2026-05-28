package com.oran.defender.repository;

import com.oran.defender.model.Incident;
import com.oran.defender.model.Incident.IncidentStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findByGameSessionId(Long gameSessionId);

    List<Incident> findByGameSessionIdAndStatus(Long gameSessionId, IncidentStatus status);
}

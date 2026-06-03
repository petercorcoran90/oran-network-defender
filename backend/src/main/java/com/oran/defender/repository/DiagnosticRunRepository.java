package com.oran.defender.repository;

import com.oran.defender.model.DiagnosticRun;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagnosticRunRepository extends JpaRepository<DiagnosticRun, Long> {

    List<DiagnosticRun> findByIncidentIdAndPlayerId(Long incidentId, Long playerId);

    Optional<DiagnosticRun> findByIncidentIdAndPlayerIdAndDiagnosticType(
            Long incidentId, Long playerId, String diagnosticType);

    long countByIncidentIdAndPlayerId(Long incidentId, Long playerId);
}

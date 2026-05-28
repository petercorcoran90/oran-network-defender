package com.oran.defender.repository;

import com.oran.defender.model.Action;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionRepository extends JpaRepository<Action, Long> {
    Optional<Action> findByActionName(String actionName);
}

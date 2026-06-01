package com.oran.defender.config;

import com.oran.defender.model.Action;
import com.oran.defender.repository.ActionRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseSeeder {
    @Bean
    CommandLineRunner seedActions(ActionRepository actionRepository) {
        return args -> {
            if (actionRepository.count() > 0) {
                return;
            }

            List<Action> actions = List.of(
                    action("REBALANCE_TRAFFIC", "Move load to neighbouring cells to reduce congestion."),
                    action("RESTART_CELL", "Restart a cell to clear transient faults."),
                    action("ROLLBACK_CONFIG", "Restore the previous known-good network configuration."),
                    action("ROLLBACK_SOFTWARE", "Revert a faulty software upgrade."),
                    action("INCREASE_TRANSMIT_POWER", "Increase radio power to improve weak signal conditions."),
                    action("FILTER_ALARMS", "Filter alarms to identify the primary fault during an alarm storm."),
                    action("DISABLE_AUTOMATION", "Disable automation that is causing harmful network changes."),
                    action("ESCALATE", "Escalate an unresolvable fault for specialist handling."),
                    action("IGNORE", "Take no remediation action when the incident is a false alarm."));
            actionRepository.saveAll(actions);
        };
    }

    private static Action action(String name, String description) {
        Action action = new Action();
        action.setActionName(name);
        action.setDescription(description);
        return action;
    }
}

package com.oran.defender.engine;

/**
 * The remediation actions a player can take. The names match the {@code action_name}
 * values seeded into the {@code actions} table, so the service can translate a stored
 * {@code Action} into an {@code ActionType} with {@code ActionType.valueOf(action.getActionName())}.
 *
 * <p>Each action carries a {@code remediationCost} — the points it costs to use, regardless
 * of whether it works. Cheap actions (IGNORE, ESCALATE) cost little; disruptive ones
 * (RESTART_CELL causes downtime) cost more. This is one of the scoring factors: a player
 * who fixes things with a cheaper correct action scores better than one who brute-forces it.
 */
public enum ActionType {
    REBALANCE_TRAFFIC(10),
    RESTART_CELL(20),
    ROLLBACK_CONFIG(10),
    ROLLBACK_SOFTWARE(15),
    INCREASE_TRANSMIT_POWER(10),
    FILTER_ALARMS(5),
    DISABLE_AUTOMATION(10),
    ESCALATE(5),
    IGNORE(0);

    private final int remediationCost;

    ActionType(int remediationCost) {
        this.remediationCost = remediationCost;
    }

    public int remediationCost() {
        return remediationCost;
    }
}

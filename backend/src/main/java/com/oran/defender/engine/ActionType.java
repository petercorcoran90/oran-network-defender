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
    REBALANCE_TRAFFIC(10, "rrmctl rebalance --cell o-ru-07", "rrmctl rebalance"),
    RESTART_CELL(20, "kubectl rollout restart deploy/o-du", "kubectl rollout restart"),
    ROLLBACK_CONFIG(10, "netconf edit-config --rollback", "netconf edit-config"),
    ROLLBACK_SOFTWARE(15, "kubectl rollout undo deploy/o-du", "kubectl rollout undo"),
    INCREASE_TRANSMIT_POWER(10, "rrmctl set-power --cell o-ru-07 --delta +3", "rrmctl set-power"),
    FILTER_ALARMS(5, "fmcli suppress --correlated", "fmcli suppress"),
    DISABLE_AUTOMATION(10, "ricctl xapp disable traffic-steering", "ricctl xapp disable"),
    ESCALATE(5, "ticket open --team transport --priority p1", "ticket open"),
    IGNORE(0, "fmcli ack --no-action", "fmcli ack");

    private final int remediationCost;
    private final String command;
    private final String match;

    ActionType(int remediationCost, String command, String match) {
        this.remediationCost = remediationCost;
        this.command = command;
        this.match = match;
    }

    public int remediationCost() {
        return remediationCost;
    }

    /** The authentic CLI command that applies this remediation (taught + accepted in the console). */
    public String command() {
        return command;
    }

    /** Normalised command prefix the console parser matches input against. */
    public String match() {
        return match;
    }
}

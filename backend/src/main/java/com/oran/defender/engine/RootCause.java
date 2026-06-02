package com.oran.defender.engine;

import java.util.Set;

/**
 * The <b>real</b> underlying problem behind an incident — stored server-side in
 * {@code incidents.root_cause} and <b>never</b> sent to the client (that's why
 * {@code IncidentResponse} omits it; exposing it would tell players the answer).
 *
 * <p>Each root cause declares two things:
 * <ul>
 *   <li>{@code correctAction} — the one action that actually fixes it (→ SUCCESS).</li>
 *   <li>{@code trapActions} — actions that look plausible but make things worse (→ FAILED).
 *       Everything else is merely INEFFECTIVE.</li>
 * </ul>
 *
 * <p><b>Why the engine keys on root cause and not on the incident <i>type</i>:</b>
 * the project rule is "the same action must not always be correct." Because the engine
 * decides correctness from the hidden root cause, the same visible symptom can have
 * different right answers, and the same action can be right or wrong depending on context.
 * The clearest example below is {@code FALSE_ALARM}: there {@code IGNORE} is the
 * <i>correct</i> action, whereas for every real incident {@code IGNORE} is a trap.
 */
public enum RootCause {
    CELL_OVERLOAD("Cell overload", ActionType.REBALANCE_TRAFFIC, Set.of(ActionType.RESTART_CELL, ActionType.IGNORE)),
    NEIGHBOUR_CONFIG_CHANGE("Neighbour config change", ActionType.ROLLBACK_CONFIG, Set.of(ActionType.RESTART_CELL, ActionType.IGNORE)),
    TRANSPORT_LINK_FAULT("Transport link fault", ActionType.ESCALATE, Set.of(ActionType.RESTART_CELL, ActionType.IGNORE)),
    ALARM_STORM("Alarm storm", ActionType.FILTER_ALARMS, Set.of(ActionType.RESTART_CELL, ActionType.IGNORE)),
    NEIGHBOUR_INTERFERENCE("Neighbour interference", ActionType.INCREASE_TRANSMIT_POWER, Set.of(ActionType.RESTART_CELL, ActionType.IGNORE)),
    SOFTWARE_UPGRADE_FAULT("Software upgrade fault", ActionType.ROLLBACK_SOFTWARE, Set.of(ActionType.INCREASE_TRANSMIT_POWER, ActionType.IGNORE)),
    ROGUE_AUTOMATION("Rogue automation", ActionType.DISABLE_AUTOMATION, Set.of(ActionType.REBALANCE_TRAFFIC, ActionType.IGNORE)),
    FALSE_ALARM("False alarm", ActionType.IGNORE, Set.of(ActionType.RESTART_CELL));

    private final String label;
    private final ActionType correctAction;
    private final Set<ActionType> trapActions;

    RootCause(String label, ActionType correctAction, Set<ActionType> trapActions) {
        this.label = label;
        this.correctAction = correctAction;
        this.trapActions = trapActions;
    }

    /** Friendly display name for the candidate list (the cause itself stays hidden until deduced). */
    public String label() {
        return label;
    }

    public ActionType correctAction() {
        return correctAction;
    }

    public boolean isTrap(ActionType action) {
        return trapActions.contains(action);
    }
}

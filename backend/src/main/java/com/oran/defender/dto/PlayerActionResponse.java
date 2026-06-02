package com.oran.defender.dto;

import com.oran.defender.engine.ActionType;
import com.oran.defender.engine.RootCause;
import com.oran.defender.engine.SymptomGroup;
import com.oran.defender.model.PlayerAction;
import java.time.Instant;
import java.util.List;

/**
 * The outcome of a submitted action. Mirrors what the engine decided, and — for the curriculum —
 * carries the CLI lesson: whether this was the first time the player used the action, the command
 * that performs it, and the diagnostic commands relevant to the incident. The UI shows these once
 * in a teaching modal. (None of this reveals the hidden root cause.)
 */
public record PlayerActionResponse(
        Long id,
        Long playerId,
        Long incidentId,
        Long actionId,
        String result,
        Integer pointsAwarded,
        Instant submittedAt,
        boolean justLearned,
        String actionCommand,
        List<String> diagnoseCommands
) {
    public static PlayerActionResponse from(PlayerAction pa) {
        ActionType action = ActionType.valueOf(pa.getAction().getActionName());
        return new PlayerActionResponse(
                pa.getId(),
                pa.getPlayer().getId(),
                pa.getIncident().getId(),
                pa.getAction().getId(),
                pa.getResult().name(),
                pa.getPointsAwarded(),
                pa.getSubmittedAt(),
                pa.isNewlyLearnedAction(),
                action.command(),
                diagnoseCommands(pa.getIncident().getRootCause()));
    }

    private static List<String> diagnoseCommands(String rootCause) {
        if (rootCause == null) {
            return List.of();
        }
        try {
            return SymptomGroup.of(RootCause.valueOf(rootCause)).diagnostics().stream()
                    .map(com.oran.defender.engine.DiagnosticType::command).toList();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return List.of();
        }
    }
}

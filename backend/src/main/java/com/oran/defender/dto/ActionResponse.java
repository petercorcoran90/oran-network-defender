package com.oran.defender.dto;

import com.oran.defender.engine.ActionType;
import com.oran.defender.model.Action;

public record ActionResponse(
        Long id,
        String actionName,
        String displayName,
        String description,
        Integer remediationCost) {

    public static ActionResponse from(Action action) {
        ActionType type = ActionType.valueOf(action.getActionName());
        return new ActionResponse(
                action.getId(),
                action.getActionName(),
                displayName(action.getActionName()),
                action.getDescription(),
                type.remediationCost());
    }

    private static String displayName(String actionName) {
        String[] words = actionName.toLowerCase().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }
}

package com.oran.defender.dto;

import com.oran.defender.model.Action;

/** A remediation action the player can choose. */
public record ActionResponse(
        Long id,
        String actionName,
        String description
) {
    public static ActionResponse from(Action a) {
        return new ActionResponse(a.getId(), a.getActionName(), a.getDescription());
    }
}

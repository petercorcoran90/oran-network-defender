package com.oran.defender.dto;

import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.engine.RootCause;
import com.oran.defender.engine.SkillTier;
import com.oran.defender.model.UserSkill;
import java.util.List;

/** A player's learned skills + derived tier, for the field-manual / progression UI. */
public record UserSkillResponse(
        Long userId,
        List<String> learnedActions,
        List<String> learnedDiagnostics,
        String tier,
        int learned,
        int total
) {
    public static UserSkillResponse from(UserSkill skill) {
        int total = RootCause.learnableActions().size() + DiagnosticType.values().length;
        int learned = skill.learnedCount();
        return new UserSkillResponse(
                skill.getUserId(),
                skill.getLearnedActions().stream().sorted().toList(),
                skill.getLearnedDiagnostics().stream().sorted().toList(),
                SkillTier.of(learned).name(),
                learned,
                total);
    }
}

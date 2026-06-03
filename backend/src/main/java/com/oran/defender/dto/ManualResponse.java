package com.oran.defender.dto;

import com.oran.defender.engine.ActionType;
import com.oran.defender.engine.DiagnosticType;
import com.oran.defender.engine.RootCause;
import com.oran.defender.engine.SkillTier;
import com.oran.defender.model.UserSkill;
import java.util.List;

/**
 * The player's field manual: the commands they've <b>learned</b>, with full detail. Only learned
 * entries are returned — unlearned commands are never sent, so the manual can't be used to skip the
 * curriculum (you still earn each command through play). Totals let the UI show progress.
 */
public record ManualResponse(
        String tier,
        List<DiagnosticEntry> diagnostics,
        List<ActionEntry> actions,
        int diagnosticsTotal,
        int actionsTotal
) {
    public record DiagnosticEntry(String name, String command, String investigates) {}
    public record ActionEntry(String name, String label, String command) {}

    public static ManualResponse from(UserSkill skill) {
        List<DiagnosticEntry> diagnostics = skill.getLearnedDiagnostics().stream().sorted()
                .map(DiagnosticType::valueOf)
                .map(d -> new DiagnosticEntry(d.name(), d.command(), d.hypothesis()))
                .toList();
        List<ActionEntry> actions = skill.getLearnedActions().stream().sorted()
                .map(ActionType::valueOf)
                .map(a -> new ActionEntry(a.name(), pretty(a.name()), a.command()))
                .toList();
        return new ManualResponse(
                SkillTier.of(skill.learnedCount()).name(),
                diagnostics, actions,
                DiagnosticType.values().length, RootCause.learnableActions().size());
    }

    private static String pretty(String enumName) {
        String s = enumName.toLowerCase().replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}

package com.oran.defender.engine;

/**
 * A player's onboarding tier, derived purely from how many actions + diagnostics they've learned.
 * Used to gate the curriculum (which crutches remain) and, later, adaptive difficulty.
 */
public enum SkillTier {
    TRAINEE,
    OPERATOR,
    ENGINEER;

    /** There are {@code ActionType.values().length + DiagnosticType.values().length} things to learn (15). */
    public static SkillTier of(int learned) {
        if (learned < 5) {
            return TRAINEE;
        }
        if (learned < 11) {
            return OPERATOR;
        }
        return ENGINEER;
    }
}

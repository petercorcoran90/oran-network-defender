package com.oran.defender.engine;

import org.springframework.stereotype.Component;

/**
 * Turns an {@link EvaluationResult} into a points delta. Pure and stateless like
 * {@link IncidentEvaluator} — no Spring context needed to unit-test it.
 *
 * <p>The delta combines three of the scoring factors from the brief:
 * <ol>
 *   <li><b>Action correctness</b> — a correct fix earns {@link #BASE_POINTS}; a trap loses
 *       {@link #HARMFUL_PENALTY}.</li>
 *   <li><b>Response time</b> — a bonus of up to {@link #MAX_TIME_BONUS} that decays linearly
 *       to zero over {@link #BONUS_DECAY_SECONDS}, so fast correct responses score higher.</li>
 *   <li><b>Remediation cost</b> — every action subtracts its {@link ActionType#remediationCost()},
 *       so even a correct fix is cheaper to do the efficient way.</li>
 * </ol>
 */
@Component
public class ScoreCalculator {

    static final int BASE_POINTS = 100;
    static final int MAX_TIME_BONUS = 50;
    static final int BONUS_DECAY_SECONDS = 60;
    static final int HARMFUL_PENALTY = 75;

    /**
     * @param result          the evaluator's verdict on the action
     * @param responseSeconds seconds between the incident being created and the action submitted
     * @param action          the action taken (supplies its remediation cost)
     * @return the points to add to the player's score (may be negative)
     */
    public int pointsFor(EvaluationResult result, long responseSeconds, ActionType action) {
        int cost = action.remediationCost();
        return switch (result) {
            case CORRECT -> BASE_POINTS + timeBonus(responseSeconds) - cost;
            case HARMFUL -> -HARMFUL_PENALTY - cost;
            case INEFFECTIVE -> -cost;
        };
    }

    /**
     * Linear decay: full bonus at 0s, nothing at or beyond {@link #BONUS_DECAY_SECONDS}.
     * Negative inputs are treated as "instant" and get the full bonus.
     */
    int timeBonus(long responseSeconds) {
        if (responseSeconds <= 0) {
            return MAX_TIME_BONUS;
        }
        if (responseSeconds >= BONUS_DECAY_SECONDS) {
            return 0;
        }
        double fraction = 1.0 - ((double) responseSeconds / BONUS_DECAY_SECONDS);
        return (int) Math.round(MAX_TIME_BONUS * fraction);
    }
}

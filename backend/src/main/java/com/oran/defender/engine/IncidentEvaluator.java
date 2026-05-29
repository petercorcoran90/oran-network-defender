package com.oran.defender.engine;

import org.springframework.stereotype.Component;

/**
 * Decides whether a chosen action solves an incident. This is the heart of the game's
 * fairness: it is pure, deterministic, and has no Spring/DB/HTTP dependencies, so the
 * same (rootCause, action) pair always yields the same result — for both players in a
 * head-to-head match, and in every unit test.
 *
 * <p>It is a Spring {@code @Component} so the service can inject it, but it holds no state,
 * so a test can simply do {@code new IncidentEvaluator()}.
 */
@Component
public class IncidentEvaluator {

    /**
     * @param rootCause the hidden, real problem behind the incident (server-side only)
     * @param action    the action the player submitted
     * @return CORRECT if the action fixes the root cause, HARMFUL if it is a trap,
     *         otherwise INEFFECTIVE
     */
    public EvaluationResult evaluate(RootCause rootCause, ActionType action) {
        if (action == rootCause.correctAction()) {
            return EvaluationResult.CORRECT;
        }
        if (rootCause.isTrap(action)) {
            return EvaluationResult.HARMFUL;
        }
        return EvaluationResult.INEFFECTIVE;
    }
}

package com.oran.defender.engine;

/**
 * The outcome of evaluating one action against an incident's root cause.
 *
 * <p>This is the engine's own vocabulary, deliberately separate from the persisted
 * {@code PlayerAction.ActionResult} (SUCCESS / PARTIAL / FAILED). The service maps
 * between them when it saves the action, e.g.:
 * <pre>
 *   CORRECT     -> ActionResult.SUCCESS  (and the incident becomes RESOLVED)
 *   INEFFECTIVE -> ActionResult.PARTIAL  (incident stays OPEN; player wasted the cost)
 *   HARMFUL     -> ActionResult.FAILED   (incident becomes FAILED)
 * </pre>
 * Keeping the engine's enum separate means game-logic decisions don't depend on the
 * database layer — the engine stays pure and unit-testable.
 */
public enum EvaluationResult {
    /** The chosen action fixes the root cause. */
    CORRECT,
    /** The action does nothing useful here — no fix, but no extra damage beyond its cost. */
    INEFFECTIVE,
    /** A trap: the action looked plausible but actively made things worse. */
    HARMFUL
}

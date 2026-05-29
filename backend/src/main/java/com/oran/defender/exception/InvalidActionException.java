package com.oran.defender.exception;

/** Thrown when a player action is not allowed in the current game state (maps to HTTP 400). */
public class InvalidActionException extends RuntimeException {
    public InvalidActionException(String message) {
        super(message);
    }
}

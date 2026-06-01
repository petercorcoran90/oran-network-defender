package com.oran.defender.exception;

/** Thrown when a request conflicts with current state, e.g. session full (maps to HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

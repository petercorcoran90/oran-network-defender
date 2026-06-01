package com.oran.defender.exception;

/** Thrown when a requested entity does not exist (maps to HTTP 404). */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}

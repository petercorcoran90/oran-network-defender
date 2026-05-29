package com.oran.defender.exception;

import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into clean HTTP responses. The body carries only a status and a
 * human-readable message — never a stack trace or internal detail — to satisfy the
 * "error messages must not leak implementation details" requirement. Anything not handled
 * here falls through to Spring's default handler, which is configured
 * ({@code server.error.include-stacktrace: never}) not to expose internals either.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Minimal error body returned to clients. */
    public record ApiError(int status, String error, String message) {}

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidActionException.class)
    public ResponseEntity<ApiError> handleInvalidAction(InvalidActionException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Bean-validation failures on @Valid request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return build(HttpStatus.BAD_REQUEST, message.isBlank() ? "Validation failed" : message);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), status.getReasonPhrase(), message));
    }
}

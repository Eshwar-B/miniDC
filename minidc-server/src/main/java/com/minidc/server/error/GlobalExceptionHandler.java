package com.minidc.server.error;

import com.minidc.server.lease.CapacityExceededException;
import com.minidc.server.lease.InvalidStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Centralized exception handler producing unified ApiError responses across REST endpoints.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CapacityExceededException.class)
    public ResponseEntity<ApiError> handleCapacityExceeded(CapacityExceededException ex) {
        ApiError error = new ApiError(Instant.now(), HttpStatus.CONFLICT.value(), "CAPACITY_EXCEEDED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<ApiError> handleInvalidState(InvalidStateException ex) {
        ApiError error = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), "INVALID_STATE", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        ApiError error = new ApiError(Instant.now(), HttpStatus.FORBIDDEN.value(), "FORBIDDEN_USER", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        ApiError error = new ApiError(Instant.now(), HttpStatus.BAD_REQUEST.value(), "BAD_REQUEST", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        String code = "BAD_REQUEST";
        String message = ex.getMessage();
        HttpStatus status = HttpStatus.BAD_REQUEST;

        if (message != null) {
            if (message.contains("capacity") || message.contains("Capacity")) {
                code = "CAPACITY_EXCEEDED";
                status = HttpStatus.CONFLICT;
            } else if (message.contains("window") || message.contains("Window") || message.contains("time window")) {
                code = "OUT_OF_WINDOW";
            } else if (message.contains("offline") || message.contains("Offline")) {
                code = "NODE_OFFLINE";
            } else if (message.contains("Unauthorized") || message.contains("forbidden") || message.contains("Forbidden") || message.contains("banned")) {
                code = "FORBIDDEN_USER";
                status = HttpStatus.FORBIDDEN;
            } else if (message.contains("state") || message.contains("State")) {
                code = "INVALID_STATE";
            }
        }

        ApiError error = new ApiError(Instant.now(), status.value(), code, message);
        return ResponseEntity.status(status).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        ApiError error = new ApiError(Instant.now(), HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

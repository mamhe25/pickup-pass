package com.pickuppass.exception;

import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<?> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<?> handleNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<?> handleConflict(ConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    /**
     * Translates the Firebase Auth error codes we're actually likely to hit
     * (bad email format, weak password, duplicate email slipping past our
     * own pre-check due to a race) into a message a non-developer admin can
     * act on, instead of a raw SDK error code.
     */
    @ExceptionHandler(FirebaseAuthException.class)
    public ResponseEntity<?> handleFirebaseAuth(FirebaseAuthException e) {
        String friendly = switch (e.getAuthErrorCode() != null ? e.getAuthErrorCode().name() : "") {
            case "EMAIL_ALREADY_EXISTS" -> "An account with this email already exists";
            case "INVALID_EMAIL" -> "That doesn't look like a valid email address";
            case "INVALID_PASSWORD" -> "Password does not meet requirements";
            default -> "Account service error: " + e.getMessage();
        };
        log.warn("FirebaseAuthException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", friendly));
    }

    /**
     * Previously this handler didn't log anything at all — every
     * "unexpected error" a client ever saw was completely invisible on the
     * server side too, with no stack trace anywhere. That made real bugs
     * (like the email-sending one this was added alongside) impossible to
     * diagnose from logs. Now every 500 is always logged with its full
     * stack trace and the request path it happened on.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception e, WebRequest request) {
        log.error("Unexpected error on {}: {}", request.getDescription(false), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unexpected error", "detail", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }
}

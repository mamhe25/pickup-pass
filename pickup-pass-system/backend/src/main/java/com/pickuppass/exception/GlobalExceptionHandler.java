package com.pickuppass.exception;

import com.google.firebase.auth.FirebaseAuthException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;
import java.util.concurrent.ExecutionException;

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

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<?> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Invalid request"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Invalid request");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    @ExceptionHandler(FirebaseAuthException.class)
    public ResponseEntity<?> handleFirebaseAuth(FirebaseAuthException e) {
        String friendly = switch (e.getAuthErrorCode() != null ? e.getAuthErrorCode().name() : "") {
            case "EMAIL_ALREADY_EXISTS" -> "An account with this email already exists";
            case "INVALID_EMAIL" -> "That doesn't look like a valid email address";
            case "INVALID_PASSWORD" -> "Password does not meet requirements";
            case "USER_DISABLED" -> "This account is disabled";
            default -> "Account service error: " + e.getMessage();
        };
        log.warn("FirebaseAuthException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", friendly));
    }

    /**
     * Firestore transaction callbacks surface application exceptions through
     * ApiFuture#get() as ExecutionException. Preserve the original API status
     * instead of collapsing expected conflicts into a generic HTTP 500.
     */
    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<?> handleExecutionException(ExecutionException e, WebRequest request) {
        Throwable known = findKnownApplicationCause(e);

        if (known instanceof ConflictException conflict) {
            return handleConflict(conflict);
        }
        if (known instanceof ForbiddenException forbidden) {
            return handleForbidden(forbidden);
        }
        if (known instanceof NotFoundException notFound) {
            return handleNotFound(notFound);
        }
        if (known instanceof IllegalArgumentException badRequest) {
            return handleBadRequest(badRequest);
        }

        log.error(
                "Unexpected async error on {}: {}",
                request.getDescription(false),
                deepestMessage(e),
                e
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unexpected server error"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneric(Exception e, WebRequest request) {
        log.error("Unexpected error on {}: {}", request.getDescription(false), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Unexpected server error"));
    }

    private Throwable findKnownApplicationCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConflictException
                    || current instanceof ForbiddenException
                    || current instanceof NotFoundException
                    || current instanceof IllegalArgumentException) {
                return current;
            }

            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return null;
    }

    private String deepestMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = throwable.getMessage();
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }

            Throwable next = current.getCause();
            if (next == current) {
                break;
            }
            current = next;
        }
        return message != null ? message : throwable.getClass().getSimpleName();
    }
}

package com.gurudutta.bank.common.exception;

import com.gurudutta.bank.common.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Translates every exception the application can throw into the single {@link ApiError} shape.
 *
 * <p>Without this, Spring's default error page leaks stack traces and internal class names. With
 * it, controllers stay free of try/catch and the client sees one predictable contract.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Every domain failure: not-found, duplicate, insufficient funds, frozen account, ... */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiError> handleBusiness(BusinessException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(ApiError.of(
                ex.getStatus().value(),
                ex.getStatus().getReasonPhrase(),
                ex.getCode(),
                ex.getMessage(),
                request.getRequestURI()));
    }

    /** {@code @Valid} failures on a request body -> 400 with a per-field breakdown. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.withViolations(
                400, "Bad Request", "VALIDATION_FAILED",
                "One or more fields are invalid.", request.getRequestURI(), violations));
    }

    /** {@code @Validated} failures on path variables / request params. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraint(ConstraintViolationException ex,
                                                     HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiError.withViolations(
                400, "Bad Request", "VALIDATION_FAILED",
                "One or more parameters are invalid.", request.getRequestURI(), violations));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ApiError.of(
                400, "Bad Request", "MALFORMED_REQUEST",
                "Request could not be read. Check the body and parameter types.",
                request.getRequestURI()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex,
                                                         HttpServletRequest request) {
        // Deliberately vague: never reveal whether it was the email or the password that was wrong.
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiError.of(
                401, "Unauthorized", "BAD_CREDENTIALS",
                "Invalid email or password.", request.getRequestURI()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                403, "Forbidden", "ACCOUNT_DISABLED",
                "This account has been disabled. Please contact support.", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of(
                403, "Forbidden", "ACCESS_DENIED",
                "You do not have permission to perform this action.", request.getRequestURI()));
    }

    /**
     * Lock contention that survived the retry policy.
     *
     * <p>{@code ConcurrencyFailureException} is the common superclass of optimistic-lock failures
     * ({@code @Version} mismatch), pessimistic-lock timeouts, and deadlock-victim aborts. All three
     * mean the same thing to a client - "nothing was applied, try again" - so all three map to a
     * retryable 409 rather than a 500. A 500 would wrongly suggest the request might have partly
     * succeeded.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiError> handleConcurrency(ConcurrencyFailureException ex,
                                                      HttpServletRequest request) {
        log.warn("Concurrency failure on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                409, "Conflict", "CONCURRENT_MODIFICATION",
                "That account is busy with another transaction. Please retry in a moment.",
                request.getRequestURI()));
    }

    /** A unique or FK constraint fired -> report as a conflict, not a 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex,
                                                        HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                409, "Conflict", "DATA_INTEGRITY_VIOLATION",
                "The request conflicts with existing data.", request.getRequestURI()));
    }

    /**
     * A URL that matches no controller.
     *
     * <p>Both types are needed: Spring MVC raises {@code NoHandlerFoundException} when no mapping
     * matches, while {@code NoResourceFoundException} (Spring 6.1+) is what the static-resource
     * handler throws for an unmatched path. Without the second one, a typo in an API path falls
     * through to the catch-all below and reports 500 - which would tell an on-call engineer the
     * server is broken when in fact the client asked for a route that never existed.
     */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> handleNoHandler(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of(
                404, "Not Found", "ENDPOINT_NOT_FOUND",
                "No endpoint " + request.getMethod() + " " + request.getRequestURI(),
                request.getRequestURI()));
    }

    /** Right path, wrong verb - e.g. GET on a POST-only endpoint. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiError.of(
                405, "Method Not Allowed", "METHOD_NOT_ALLOWED",
                "%s is not supported here. Supported: %s"
                        .formatted(request.getMethod(), String.join(", ", ex.getSupportedMethods() == null
                                ? new String[]{"n/a"} : ex.getSupportedMethods())),
                request.getRequestURI()));
    }

    /** Last resort. Log the full stack trace server-side, return a generic message to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                500, "Internal Server Error", "INTERNAL_ERROR",
                "Something went wrong on our side. Please try again later.", request.getRequestURI()));
    }
}

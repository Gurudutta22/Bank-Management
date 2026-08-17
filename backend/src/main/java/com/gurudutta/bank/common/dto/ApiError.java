package com.gurudutta.bank.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every failing endpoint returns, so the frontend has exactly one
 * error contract to handle.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp,
                       int status,
                       String error,
                       String code,
                       String message,
                       String path,
                       List<FieldViolation> violations) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, null);
    }

    public static ApiError withViolations(int status, String error, String code, String message,
                                          String path, List<FieldViolation> violations) {
        return new ApiError(Instant.now(), status, error, code, message, path, violations);
    }
}

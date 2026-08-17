package com.gurudutta.bank.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for every expected, domain-level failure.
 *
 * <p>Carrying the HTTP status and a stable machine-readable {@code code} on the exception means the
 * global handler can translate any of these into a response without a chain of instanceof checks,
 * and the React client can branch on {@code code} instead of parsing English.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}

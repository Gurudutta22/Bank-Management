package com.gurudutta.bank.common.exception;

import org.springframework.http.HttpStatus;

/** A request that is syntactically valid but not allowed for the current domain state. */
public class InvalidOperationException extends BusinessException {

    public InvalidOperationException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_OPERATION");
    }

    public InvalidOperationException(String message, String code) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}

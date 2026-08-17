package com.gurudutta.bank.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND");
    }

    public static ResourceNotFoundException of(String entity, Object id) {
        return new ResourceNotFoundException("%s not found: %s".formatted(entity, id));
    }
}

package com.gurudutta.bank.common.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientFundsException extends BusinessException {

    public InsufficientFundsException(String accountNumber, BigDecimal available, BigDecimal requested) {
        super("Insufficient funds in account %s. Available: %s, requested: %s"
                        .formatted(accountNumber, available.toPlainString(), requested.toPlainString()),
                HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS");
    }
}

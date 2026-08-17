package com.gurudutta.bank.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record DepositRequest(

        @NotBlank(message = "Account number is required")
        String accountNumber,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 2, message = "Amount may have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 255)
        String description,

        @Size(max = 60)
        String category,

        /** Optional de-duplication token; see TransactionService for how it is used. */
        @Size(max = 80)
        String idempotencyKey) {
}

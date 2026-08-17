package com.gurudutta.bank.account.dto;

import com.gurudutta.bank.account.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OpenAccountRequest(

        @NotNull(message = "Account type is required")
        AccountType type,

        @NotNull(message = "Opening balance is required")
        @DecimalMin(value = "0.00", message = "Opening balance cannot be negative")
        @Digits(integer = 15, fraction = 2, message = "Amount may have at most 2 decimal places")
        BigDecimal openingBalance) {
}

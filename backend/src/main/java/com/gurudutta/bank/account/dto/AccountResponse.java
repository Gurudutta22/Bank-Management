package com.gurudutta.bank.account.dto;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountStatus;
import com.gurudutta.bank.account.AccountType;
import com.gurudutta.bank.common.util.Money;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountResponse(String accountNumber,
                              String maskedAccountNumber,
                              AccountType type,
                              String typeLabel,
                              AccountStatus status,
                              BigDecimal balance,
                              String currency,
                              BigDecimal interestRate,
                              BigDecimal dailyTransferLimit,
                              String ownerName,
                              String ownerEmail,
                              Instant openedAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                mask(account.getAccountNumber()),
                account.getType(),
                account.getType().getLabel(),
                account.getStatus(),
                Money.normalise(account.getBalance()),
                account.getCurrency(),
                account.getType().getInterestRate(),
                Money.normalise(account.getDailyTransferLimit()),
                account.getOwner().getFullName(),
                account.getOwner().getEmail(),
                account.getCreatedAt());
    }

    /** Shows only the last four digits, the way a bank statement or a card receipt would. */
    private static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        return "•••• " + accountNumber.substring(accountNumber.length() - 4);
    }
}

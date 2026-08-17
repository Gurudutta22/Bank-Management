package com.gurudutta.bank.account;

import java.math.BigDecimal;

public enum AccountType {

    SAVINGS("Savings", new BigDecimal("3.50")),
    CURRENT("Current", BigDecimal.ZERO),
    FIXED_DEPOSIT("Fixed Deposit", new BigDecimal("7.10"));

    private final String label;
    /** Annual nominal interest rate, in percent. */
    private final BigDecimal interestRate;

    AccountType(String label, BigDecimal interestRate) {
        this.label = label;
        this.interestRate = interestRate;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }
}

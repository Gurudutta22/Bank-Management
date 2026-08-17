package com.gurudutta.bank.transaction;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    /** The debit leg of a transfer, written on the sender's account. */
    TRANSFER_OUT,
    /** The credit leg of a transfer, written on the receiver's account. */
    TRANSFER_IN,
    INTEREST_CREDIT,
    FEE
}

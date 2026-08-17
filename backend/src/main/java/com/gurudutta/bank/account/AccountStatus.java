package com.gurudutta.bank.account;

public enum AccountStatus {
    /** Normal: money can move in and out. */
    ACTIVE,
    /** Admin hold. Credits are still accepted, debits are rejected. */
    FROZEN,
    /** Terminal state. Requires a zero balance; nothing moves in or out afterwards. */
    CLOSED
}

package com.gurudutta.bank.user;

public enum Role {
    /** Owns accounts, moves their own money. */
    CUSTOMER,
    /** Back-office staff: sees every user/account, can freeze accounts and read audit logs. */
    ADMIN
}

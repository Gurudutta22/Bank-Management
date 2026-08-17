package com.gurudutta.bank.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Returned by a successful transfer: both legs plus the sender's new balance, in one round trip. */
public record TransferResponse(String reference,
                               String fromAccountNumber,
                               String toAccountNumber,
                               String toAccountHolder,
                               BigDecimal amount,
                               BigDecimal sourceBalanceAfter,
                               Instant completedAt) {
}

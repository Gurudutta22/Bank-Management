package com.gurudutta.bank.transaction.dto;

import com.gurudutta.bank.common.util.Money;
import com.gurudutta.bank.transaction.Transaction;
import com.gurudutta.bank.transaction.TransactionDirection;
import com.gurudutta.bank.transaction.TransactionStatus;
import com.gurudutta.bank.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(Long id,
                                  String reference,
                                  String accountNumber,
                                  String counterpartyAccountNumber,
                                  String counterpartyName,
                                  TransactionType type,
                                  TransactionDirection direction,
                                  BigDecimal amount,
                                  BigDecimal signedAmount,
                                  BigDecimal balanceAfter,
                                  TransactionStatus status,
                                  String description,
                                  String category,
                                  Instant createdAt) {

    public static TransactionResponse from(Transaction tx) {
        var counterparty = tx.getCounterpartyAccount();
        return new TransactionResponse(
                tx.getId(),
                tx.getReference(),
                tx.getAccount().getAccountNumber(),
                counterparty != null ? counterparty.getAccountNumber() : null,
                counterparty != null ? counterparty.getOwner().getFullName() : null,
                tx.getType(),
                tx.getDirection(),
                Money.normalise(tx.getAmount()),
                Money.normalise(tx.signedAmount()),
                Money.normalise(tx.getBalanceAfter()),
                tx.getStatus(),
                tx.getDescription(),
                tx.getCategory(),
                tx.getCreatedAt());
    }
}

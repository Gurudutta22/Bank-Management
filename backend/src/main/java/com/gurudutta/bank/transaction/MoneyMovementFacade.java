package com.gurudutta.bank.transaction;

import com.gurudutta.bank.common.exception.BusinessException;
import com.gurudutta.bank.transaction.dto.DepositRequest;
import com.gurudutta.bank.transaction.dto.TransactionResponse;
import com.gurudutta.bank.transaction.dto.TransferRequest;
import com.gurudutta.bank.transaction.dto.TransferResponse;
import com.gurudutta.bank.transaction.dto.WithdrawRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Owns the retry policy for money movement.
 *
 * <p><b>Why this class exists.</b> Pessimistic row locks stop two transfers corrupting a balance,
 * but they do not stop the database refusing one of them: under contention an engine may time out
 * waiting for a lock, or pick a transaction as a deadlock victim and abort it. Those failures are
 * <em>transient</em> - the correct response is to try again, not to tell the customer their
 * transfer failed.
 *
 * <p><b>Why it is a separate bean.</b> A retry has to begin a brand-new transaction: once a
 * transaction is marked rollback-only, every subsequent statement on it fails. Retrying inside
 * {@code TransactionService.transfer()} would therefore retry inside the already-doomed
 * transaction and achieve nothing. The loop has to sit <em>outside</em> the transactional
 * boundary, which - because Spring's {@code @Transactional} works through a proxy - means outside
 * the bean. Each call below crosses the proxy and so starts a fresh transaction.
 *
 * <p>Retries are safe to do blindly here because a failed attempt rolled back completely: no
 * partial debit survives. And when the caller supplies an idempotency key, a retry that actually
 * did commit before failing is caught by the unique index and replayed rather than reapplied.
 */
@Service
public class MoneyMovementFacade {

    private static final Logger log = LoggerFactory.getLogger(MoneyMovementFacade.class);

    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MILLIS = 25;

    private final TransactionService transactionService;

    public MoneyMovementFacade(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    public TransferResponse transfer(TransferRequest request) {
        return withRetry("transfer", () -> transactionService.transfer(request));
    }

    public TransactionResponse deposit(DepositRequest request) {
        return withRetry("deposit", () -> transactionService.deposit(request));
    }

    public TransactionResponse withdraw(WithdrawRequest request) {
        return withRetry("withdraw", () -> transactionService.withdraw(request));
    }

    /**
     * Runs {@code action}, retrying only on lock contention.
     *
     * <p>Business failures - insufficient funds, a frozen account, a breached daily limit - are
     * {@link BusinessException}s and propagate on the first attempt. Retrying those would just
     * fail four times more slowly.
     */
    private <T> T withRetry(String operation, Supplier<T> action) {
        ConcurrencyFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return action.get();
            } catch (ConcurrencyFailureException ex) {
                // Covers optimistic (@Version), pessimistic (lock timeout) and deadlock-victim
                // failures - they share this Spring superclass precisely because the remedy is
                // the same for all three.
                lastFailure = ex;
                if (attempt == MAX_ATTEMPTS) {
                    break;
                }
                backoff(attempt);
                log.warn("Lock contention on {} (attempt {}/{}), retrying: {}",
                        operation, attempt, MAX_ATTEMPTS, ex.getMostSpecificCause().getMessage());
            }
        }

        log.error("{} abandoned after {} attempts due to sustained lock contention",
                operation, MAX_ATTEMPTS, lastFailure);
        throw new BusinessException(
                "The account is busy with another transaction. Please try again in a moment.",
                HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
    }

    /**
     * Exponential backoff with jitter.
     *
     * <p>The randomness matters: without it, every loser of a collision would wake at the same
     * instant and collide again, turning one contended moment into a repeating one.
     */
    private void backoff(int attempt) {
        long ceiling = BASE_BACKOFF_MILLIS * (1L << (attempt - 1));
        long delay = ThreadLocalRandom.current().nextLong(BASE_BACKOFF_MILLIS, ceiling + BASE_BACKOFF_MILLIS);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Request interrupted. Please try again.",
                    HttpStatus.SERVICE_UNAVAILABLE, "INTERRUPTED");
        }
    }
}

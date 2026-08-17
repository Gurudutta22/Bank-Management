package com.gurudutta.bank.transaction;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountRepository;
import com.gurudutta.bank.audit.AuditService;
import com.gurudutta.bank.common.exception.InsufficientFundsException;
import com.gurudutta.bank.common.exception.InvalidOperationException;
import com.gurudutta.bank.common.exception.ResourceNotFoundException;
import com.gurudutta.bank.common.util.Money;
import com.gurudutta.bank.config.AppProperties;
import com.gurudutta.bank.security.SecurityUtils;
import com.gurudutta.bank.transaction.dto.DepositRequest;
import com.gurudutta.bank.transaction.dto.TransactionResponse;
import com.gurudutta.bank.transaction.dto.TransferRequest;
import com.gurudutta.bank.transaction.dto.TransferResponse;
import com.gurudutta.bank.transaction.dto.WithdrawRequest;
import com.gurudutta.bank.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * All money movement.
 *
 * <p>Three invariants are enforced here, and they are what make this more than a CRUD app:
 * <ol>
 *   <li><b>Atomicity</b> - a transfer's debit and credit either both commit or neither does. One
 *       {@code @Transactional} boundary spans both legs, so a crash between them cannot destroy
 *       money.</li>
 *   <li><b>Isolation</b> - balances are read under a pessimistic row lock, so two concurrent
 *       withdrawals cannot both pass the same "can you afford this?" check.</li>
 *   <li><b>Idempotency</b> - a client-supplied key makes a retried request return the original
 *       result instead of moving the money twice.</li>
 * </ol>
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Kolkata");

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;
    private final AppProperties props;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              AuditService auditService,
                              AppProperties props) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
        this.props = props;
    }

    // ------------------------------------------------------------------ deposit

    @Transactional
    public TransactionResponse deposit(DepositRequest request) {
        BigDecimal amount = validateAmount(request.amount());

        TransactionResponse replayed = replayIfDuplicate(request.idempotencyKey());
        if (replayed != null) {
            return replayed;
        }

        Account account = lockAndAuthorise(request.accountNumber());
        if (!account.isCreditable()) {
            throw new InvalidOperationException(
                    "Account %s is %s and cannot receive money."
                            .formatted(account.getAccountNumber(), account.getStatus()),
                    "ACCOUNT_NOT_CREDITABLE");
        }

        account.credit(amount);
        Transaction tx = record(account, null, TransactionType.DEPOSIT, TransactionDirection.CREDIT,
                amount, defaulted(request.description(), "Cash deposit"),
                defaulted(request.category(), "Deposit"), request.idempotencyKey());

        auditService.success("DEPOSIT", "Account", account.getAccountNumber(),
                "Deposited " + amount.toPlainString());
        return TransactionResponse.from(tx);
    }

    // ----------------------------------------------------------------- withdraw

    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request) {
        BigDecimal amount = validateAmount(request.amount());

        TransactionResponse replayed = replayIfDuplicate(request.idempotencyKey());
        if (replayed != null) {
            return replayed;
        }

        Account account = lockAndAuthorise(request.accountNumber());
        assertDebitable(account, amount);

        account.debit(amount);
        Transaction tx = record(account, null, TransactionType.WITHDRAWAL, TransactionDirection.DEBIT,
                amount, defaulted(request.description(), "Cash withdrawal"),
                defaulted(request.category(), "Withdrawal"), request.idempotencyKey());

        auditService.success("WITHDRAWAL", "Account", account.getAccountNumber(),
                "Withdrew " + amount.toPlainString());
        return TransactionResponse.from(tx);
    }

    // ----------------------------------------------------------------- transfer

    /**
     * Moves money between two accounts. One attempt, one transaction.
     *
     * <p>Runs at the default READ_COMMITTED isolation and relies on the explicit
     * {@code SELECT ... FOR UPDATE} row locks below for correctness. Raising the isolation level to
     * REPEATABLE_READ would add nothing - the row lock already prevents the balance changing
     * underneath us - while making the database far more likely to abort the transaction with a
     * serialization failure under load.
     *
     * <p>Callers must go through {@link MoneyMovementFacade}, which owns the retry policy for the
     * transient lock failures this method can still throw.
     */
    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        BigDecimal amount = validateAmount(request.amount());

        if (request.fromAccountNumber().equals(request.toAccountNumber())) {
            throw new InvalidOperationException("Source and destination accounts must be different.",
                    "SAME_ACCOUNT_TRANSFER");
        }

        Transaction replayed = findByIdempotencyKey(request.idempotencyKey());
        if (replayed != null) {
            return toTransferResponse(replayed);
        }

        // ---- Deadlock avoidance -------------------------------------------------
        // Two simultaneous transfers, A->B and B->A, would deadlock if each locked its own source
        // first: each would hold one row and wait forever for the other. Locking in a globally
        // consistent order (ascending account number) means one of them always acquires both locks
        // and the other simply waits. This ordering is the single most important line in the class.
        String first = min(request.fromAccountNumber(), request.toAccountNumber());
        String second = max(request.fromAccountNumber(), request.toAccountNumber());
        Account firstLocked = lockAccount(first);
        Account secondLocked = lockAccount(second);

        Account source = firstLocked.getAccountNumber().equals(request.fromAccountNumber())
                ? firstLocked : secondLocked;
        Account destination = source == firstLocked ? secondLocked : firstLocked;

        authoriseOwnership(source);

        assertDebitable(source, amount);
        if (!destination.isCreditable()) {
            throw new InvalidOperationException(
                    "Destination account %s cannot receive money right now."
                            .formatted(destination.getAccountNumber()),
                    "ACCOUNT_NOT_CREDITABLE");
        }
        assertWithinDailyLimit(source, amount);

        // Both legs share one reference: this pair *is* the double-entry record.
        String reference = UUID.randomUUID().toString();

        source.debit(amount);
        destination.credit(amount);

        Transaction debitLeg = record(reference, source, destination, TransactionType.TRANSFER_OUT,
                TransactionDirection.DEBIT, amount,
                defaulted(request.description(), "Transfer to " + destination.getAccountNumber()),
                defaulted(request.category(), "Transfer"), request.idempotencyKey());

        record(reference, destination, source, TransactionType.TRANSFER_IN,
                TransactionDirection.CREDIT, amount,
                defaulted(request.description(), "Transfer from " + source.getAccountNumber()),
                defaulted(request.category(), "Transfer"), null);

        auditService.success("TRANSFER", "Account", source.getAccountNumber(),
                "Transferred %s to %s (ref %s)"
                        .formatted(amount.toPlainString(), destination.getAccountNumber(), reference));
        log.info("Transfer {} of {} from {} to {}", reference, amount,
                source.getAccountNumber(), destination.getAccountNumber());

        return new TransferResponse(reference, source.getAccountNumber(),
                destination.getAccountNumber(), destination.getOwner().getFullName(),
                Money.normalise(amount), Money.normalise(source.getBalance()),
                debitLeg.getCreatedAt() != null ? debitLeg.getCreatedAt() : Instant.now());
    }

    // -------------------------------------------------------------- validation

    private BigDecimal validateAmount(BigDecimal raw) {
        BigDecimal amount = Money.normalise(raw);
        if (!Money.isPositive(amount)) {
            throw new InvalidOperationException("Amount must be greater than zero.", "INVALID_AMOUNT");
        }
        if (amount.compareTo(props.limits().maxTransactionAmount()) > 0) {
            throw new InvalidOperationException(
                    "Single transactions are capped at %s."
                            .formatted(props.limits().maxTransactionAmount().toPlainString()),
                    "AMOUNT_EXCEEDS_LIMIT");
        }
        return amount;
    }

    /**
     * Checks the account can actually part with the money.
     *
     * <p>Savings accounts must keep a minimum balance; current accounts get an overdraft buffer.
     * The comparison is against the <em>spendable</em> balance, not the raw balance.
     */
    private void assertDebitable(Account account, BigDecimal amount) {
        if (!account.isDebitable()) {
            throw new InvalidOperationException(
                    "Account %s is %s and cannot be debited."
                            .formatted(account.getAccountNumber(), account.getStatus()),
                    "ACCOUNT_NOT_DEBITABLE");
        }
        BigDecimal spendable = spendableBalance(account);
        if (Money.lt(spendable, amount)) {
            auditService.failure("INSUFFICIENT_FUNDS", "Account", account.getAccountNumber(),
                    "Requested %s, spendable %s".formatted(amount.toPlainString(), spendable.toPlainString()));
            throw new InsufficientFundsException(account.getAccountNumber(), spendable, amount);
        }
    }

    private BigDecimal spendableBalance(Account account) {
        return switch (account.getType()) {
            case SAVINGS -> account.getBalance().subtract(props.limits().savingsMinimumBalance());
            case CURRENT -> account.getBalance().add(props.limits().currentOverdraftLimit());
            case FIXED_DEPOSIT -> BigDecimal.ZERO; // locked until maturity
        };
    }

    /** Rolling check against everything already debited from this account since midnight. */
    private void assertWithinDailyLimit(Account account, BigDecimal amount) {
        Instant startOfDay = LocalDate.now(BANK_ZONE).atStartOfDay(BANK_ZONE).toInstant();
        BigDecimal spentToday = transactionRepository.sumDebitsSince(account.getId(), startOfDay);
        BigDecimal wouldBe = spentToday.add(amount);

        if (wouldBe.compareTo(account.getDailyTransferLimit()) > 0) {
            auditService.failure("DAILY_LIMIT_EXCEEDED", "Account", account.getAccountNumber(),
                    "Attempted %s, already spent %s today, limit %s".formatted(
                            amount.toPlainString(), spentToday.toPlainString(),
                            account.getDailyTransferLimit().toPlainString()));
            throw new InvalidOperationException(
                    "This transfer would exceed your daily limit of %s. You have already moved %s today."
                            .formatted(Money.normalise(account.getDailyTransferLimit()).toPlainString(),
                                    Money.normalise(spentToday).toPlainString()),
                    "DAILY_LIMIT_EXCEEDED");
        }
    }

    private Account lockAndAuthorise(String accountNumber) {
        Account account = lockAccount(accountNumber);
        authoriseOwnership(account);
        return account;
    }

    private Account lockAccount(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", accountNumber));
    }

    private void authoriseOwnership(Account account) {
        User current = SecurityUtils.currentUser();
        if (!SecurityUtils.isAdmin() && !account.getOwner().getId().equals(current.getId())) {
            auditService.failure("UNAUTHORISED_TRANSACTION", "Account", account.getAccountNumber(),
                    "User " + current.getPublicId() + " attempted to transact on another customer's account");
            throw ResourceNotFoundException.of("Account", account.getAccountNumber());
        }
    }

    // ------------------------------------------------------------- idempotency

    /**
     * If this key was already used, return the original outcome instead of repeating the work.
     *
     * <p>This is what makes a double-clicked "Send" button, or a mobile client retrying after a
     * dropped connection, safe. The unique index on {@code idempotency_key} is the real guarantee -
     * this lookup is the fast path that avoids relying on a constraint violation.
     */
    private TransactionResponse replayIfDuplicate(String idempotencyKey) {
        Transaction existing = findByIdempotencyKey(idempotencyKey);
        return existing == null ? null : TransactionResponse.from(existing);
    }

    private Transaction findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(tx -> {
                    log.info("Replaying idempotent request {} -> transaction {}",
                            idempotencyKey, tx.getReference());
                    return tx;
                })
                .orElse(null);
    }

    private TransferResponse toTransferResponse(Transaction debitLeg) {
        Account counterparty = debitLeg.getCounterpartyAccount();
        return new TransferResponse(
                debitLeg.getReference(),
                debitLeg.getAccount().getAccountNumber(),
                counterparty != null ? counterparty.getAccountNumber() : null,
                counterparty != null ? counterparty.getOwner().getFullName() : null,
                Money.normalise(debitLeg.getAmount()),
                Money.normalise(debitLeg.getBalanceAfter()),
                debitLeg.getCreatedAt());
    }

    // ------------------------------------------------------------------ ledger

    private Transaction record(Account account, Account counterparty, TransactionType type,
                               TransactionDirection direction, BigDecimal amount,
                               String description, String category, String idempotencyKey) {
        return record(UUID.randomUUID().toString(), account, counterparty, type, direction,
                amount, description, category, idempotencyKey);
    }

    private Transaction record(String reference, Account account, Account counterparty,
                               TransactionType type, TransactionDirection direction,
                               BigDecimal amount, String description, String category,
                               String idempotencyKey) {
        Transaction tx = new Transaction(reference, account, counterparty, type, direction,
                Money.store(amount), Money.store(account.getBalance()), description, category);
        tx.setIdempotencyKey(idempotencyKey);
        return transactionRepository.save(tx);
    }

    // ---------------------------------------------------------------- interest

    /**
     * Credits one month of interest to a single savings account.
     *
     * <p>Called by {@code InterestScheduler}, never by this class directly - a self-invocation
     * would go through {@code this} rather than the Spring proxy, and {@code @Transactional} would
     * silently do nothing.
     */
    @Transactional
    public boolean creditInterestFor(String accountNumber) {
        Account account = lockAccount(accountNumber);
        BigDecimal monthlyRate = account.getType().getInterestRate()
                .divide(new BigDecimal("1200"), 10, java.math.RoundingMode.HALF_UP);
        BigDecimal interest = Money.normalise(account.getBalance().multiply(monthlyRate));

        if (!Money.isPositive(interest)) {
            return false;
        }

        account.credit(interest);
        record(account, null, TransactionType.INTEREST_CREDIT, TransactionDirection.CREDIT,
                interest, "Monthly interest at %s%% p.a."
                        .formatted(account.getType().getInterestRate().toPlainString()),
                "Interest", null);
        return true;
    }

    private static String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String min(String a, String b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static String max(String a, String b) {
        return a.compareTo(b) > 0 ? a : b;
    }
}

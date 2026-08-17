package com.gurudutta.bank.account;

import com.gurudutta.bank.account.dto.AccountResponse;
import com.gurudutta.bank.account.dto.OpenAccountRequest;
import com.gurudutta.bank.audit.AuditService;
import com.gurudutta.bank.common.exception.InvalidOperationException;
import com.gurudutta.bank.common.exception.ResourceNotFoundException;
import com.gurudutta.bank.common.util.AccountNumberGenerator;
import com.gurudutta.bank.common.util.Money;
import com.gurudutta.bank.config.AppProperties;
import com.gurudutta.bank.security.SecurityUtils;
import com.gurudutta.bank.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final int MAX_ACCOUNTS_PER_CUSTOMER = 5;

    private final AccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AuditService auditService;
    private final AppProperties props;

    public AccountService(AccountRepository accountRepository,
                          AccountNumberGenerator accountNumberGenerator,
                          AuditService auditService,
                          AppProperties props) {
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.auditService = auditService;
        this.props = props;
    }

    @Transactional
    public AccountResponse openAccount(OpenAccountRequest request) {
        User owner = SecurityUtils.currentUser();

        if (accountRepository.countByOwnerId(owner.getId()) >= MAX_ACCOUNTS_PER_CUSTOMER) {
            throw new InvalidOperationException(
                    "You already have the maximum of " + MAX_ACCOUNTS_PER_CUSTOMER + " accounts.",
                    "ACCOUNT_LIMIT_REACHED");
        }

        BigDecimal opening = Money.normalise(request.openingBalance());
        BigDecimal minimum = minimumOpeningBalance(request.type());
        if (Money.lt(opening, minimum)) {
            throw new InvalidOperationException(
                    "%s accounts require a minimum opening balance of %s."
                            .formatted(request.type().getLabel(), minimum.toPlainString()),
                    "MINIMUM_OPENING_BALANCE");
        }

        Account account = new Account(
                accountNumberGenerator.generate(),
                owner,
                request.type(),
                Money.store(opening),
                props.limits().defaultDailyTransferLimit());

        Account saved = accountRepository.save(account);
        auditService.success("ACCOUNT_OPENED", "Account", saved.getAccountNumber(),
                "%s account opened with balance %s".formatted(request.type(), opening.toPlainString()));
        log.info("Opened {} account {} for user {}", request.type(),
                saved.getAccountNumber(), owner.getPublicId());

        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> myAccounts() {
        return accountRepository.findByOwnerIdOrderByCreatedAtDesc(SecurityUtils.currentUser().getId())
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountNumber) {
        return AccountResponse.from(requireOwnedAccount(accountNumber));
    }

    /**
     * Loads an account and enforces that the caller is allowed to see it.
     *
     * <p>This is the guard against IDOR (insecure direct object reference): without the ownership
     * check, any authenticated customer could read or move money on any account simply by putting a
     * different number in the URL. Admins are exempt because that is their job.
     */
    @Transactional(readOnly = true)
    public Account requireOwnedAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", accountNumber));

        User current = SecurityUtils.currentUser();
        if (!SecurityUtils.isAdmin() && !account.getOwner().getId().equals(current.getId())) {
            auditService.failure("UNAUTHORISED_ACCOUNT_ACCESS", "Account", accountNumber,
                    "User " + current.getPublicId() + " attempted to access another customer's account");
            // 404 rather than 403 on purpose: a 403 would confirm the account number exists.
            throw ResourceNotFoundException.of("Account", accountNumber);
        }
        return account;
    }

    @Transactional
    public AccountResponse closeAccount(String accountNumber) {
        Account account = requireOwnedAccount(accountNumber);

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidOperationException("This account is already closed.");
        }
        if (!Money.isZero(account.getBalance())) {
            throw new InvalidOperationException(
                    "Withdraw or transfer the remaining balance of %s before closing this account."
                            .formatted(Money.normalise(account.getBalance()).toPlainString()),
                    "NON_ZERO_BALANCE");
        }

        account.setStatus(AccountStatus.CLOSED);
        auditService.success("ACCOUNT_CLOSED", "Account", accountNumber, "Closed by owner");
        return AccountResponse.from(account);
    }

    private BigDecimal minimumOpeningBalance(AccountType type) {
        return switch (type) {
            // A fixed deposit is a lump sum by definition, so it carries a higher floor.
            case FIXED_DEPOSIT -> props.limits().minOpeningBalance().multiply(BigDecimal.TEN);
            case SAVINGS -> props.limits().minOpeningBalance();
            case CURRENT -> BigDecimal.ZERO;
        };
    }
}

package com.gurudutta.bank.transaction;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountRepository;
import com.gurudutta.bank.account.AccountStatus;
import com.gurudutta.bank.account.AccountType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Monthly interest posting.
 *
 * <p>Deliberately a separate bean from {@link TransactionService}: calling a
 * {@code @Transactional} method from inside the same class bypasses Spring's proxy, so the
 * per-account transaction boundary would never be created. Crossing a bean boundary makes the
 * proxy - and therefore the transaction - real.
 *
 * <p>Each account is committed independently so one failure does not roll back the whole run.
 */
@Component
public class InterestScheduler {

    private static final Logger log = LoggerFactory.getLogger(InterestScheduler.class);

    private final AccountRepository accountRepository;
    private final TransactionService transactionService;

    public InterestScheduler(AccountRepository accountRepository,
                             TransactionService transactionService) {
        this.accountRepository = accountRepository;
        this.transactionService = transactionService;
    }

    /** 01:00 on the 1st of every month, bank local time. */
    @Scheduled(cron = "0 0 1 1 * *", zone = "Asia/Kolkata")
    public void creditMonthlyInterest() {
        List<Account> accounts =
                accountRepository.findByTypeAndStatus(AccountType.SAVINGS, AccountStatus.ACTIVE);
        log.info("Monthly interest run starting for {} savings accounts", accounts.size());

        int credited = 0;
        for (Account account : accounts) {
            try {
                if (transactionService.creditInterestFor(account.getAccountNumber())) {
                    credited++;
                }
            } catch (Exception ex) {
                log.error("Interest credit failed for account {}", account.getAccountNumber(), ex);
            }
        }
        log.info("Monthly interest run complete: {}/{} accounts credited", credited, accounts.size());
    }
}

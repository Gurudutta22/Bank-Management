package com.gurudutta.bank.common.util;

import com.gurudutta.bank.account.AccountRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Produces the 12-digit customer-facing account number.
 *
 * <p>Uses {@link SecureRandom} rather than a sequence so numbers are not guessable from one
 * another, then checks the database for a collision. With 9x10^11 possible values, a retry is
 * effectively never needed, but the loop makes the guarantee explicit instead of hoping.
 */
@Component
public class AccountNumberGenerator {

    private static final String BANK_PREFIX = "90";
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accountRepository;

    public AccountNumberGenerator(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = BANK_PREFIX + String.format("%010d", Math.abs(random.nextLong() % 10_000_000_000L));
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique account number after "
                + MAX_ATTEMPTS + " attempts");
    }
}

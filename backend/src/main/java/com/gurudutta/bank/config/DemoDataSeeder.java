package com.gurudutta.bank.config;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountRepository;
import com.gurudutta.bank.account.AccountType;
import com.gurudutta.bank.beneficiary.Beneficiary;
import com.gurudutta.bank.beneficiary.BeneficiaryRepository;
import com.gurudutta.bank.transaction.Transaction;
import com.gurudutta.bank.transaction.TransactionDirection;
import com.gurudutta.bank.transaction.TransactionRepository;
import com.gurudutta.bank.transaction.TransactionType;
import com.gurudutta.bank.user.Role;
import com.gurudutta.bank.user.User;
import com.gurudutta.bank.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Populates a realistic demo dataset on first start.
 *
 * <p>Exists so the project can be cloned and demonstrated in one command, with charts that already
 * have data in them. Guarded by {@code app.seed.enabled} and skipped entirely if any user already
 * exists, so it can never overwrite real data.
 *
 * <p>Implemented as a {@code @Component} rather than a {@code @Bean}-returned lambda: Spring can
 * only apply {@code @Transactional} to a call that crosses a proxy, and a lambda invoking a method
 * on its enclosing configuration class does not.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String[] SPEND_CATEGORIES =
            {"Groceries", "Utilities", "Dining", "Transport", "Shopping", "Entertainment", "Health"};
    private static final String[] MERCHANTS =
            {"BigBazaar", "TataPower", "Swiggy", "Uber", "Myntra", "BookMyShow", "Apollo Pharmacy"};

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties props;

    public DemoDataSeeder(UserRepository userRepository,
                          AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          BeneficiaryRepository beneficiaryRepository,
                          PasswordEncoder passwordEncoder,
                          JdbcTemplate jdbcTemplate,
                          AppProperties props) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.beneficiaryRepository = beneficiaryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.info("Demo seed skipped - database already contains {} users", userRepository.count());
            return;
        }

        log.info("Seeding demo data...");
        // Fixed seed so every run produces identical numbers, which keeps the screenshots in the
        // documentation reproducible.
        Random random = new Random(42);
        BigDecimal limit = props.limits().defaultDailyTransferLimit();

        User admin = user("Aarav Mehta", "admin@novabank.io", "9800000001",
                "Admin@123", Role.ADMIN, LocalDate.of(1988, 4, 12), "12 Residency Road, Bengaluru");
        User priya = user("Priya Sharma", "priya@novabank.io", "9800000002",
                "Customer@123", Role.CUSTOMER, LocalDate.of(1996, 9, 3), "44 Park Street, Kolkata");
        User rahul = user("Rahul Verma", "rahul@novabank.io", "9800000003",
                "Customer@123", Role.CUSTOMER, LocalDate.of(1993, 1, 28), "7 MG Road, Pune");
        User ananya = user("Ananya Iyer", "ananya@novabank.io", "9800000004",
                "Customer@123", Role.CUSTOMER, LocalDate.of(1999, 6, 17), "21 Marine Drive, Mumbai");

        admin.setKycVerified(true);
        priya.setKycVerified(true);
        rahul.setKycVerified(true);

        Account priyaSavings = account("900100100101", priya, AccountType.SAVINGS,
                new BigDecimal("184500.0000"), limit);
        Account priyaCurrent = account("900100100102", priya, AccountType.CURRENT,
                new BigDecimal("52300.0000"), limit);
        Account rahulSavings = account("900100100201", rahul, AccountType.SAVINGS,
                new BigDecimal("96750.0000"), limit);
        Account ananyaSavings = account("900100100301", ananya, AccountType.SAVINGS,
                new BigDecimal("41200.0000"), limit);
        account("900100100001", admin, AccountType.CURRENT, new BigDecimal("1000000.0000"), limit);

        beneficiaryRepository.save(favourite(new Beneficiary(
                priya, rahulSavings.getAccountNumber(), "Rahul (rent)", rahul.getFullName())));
        beneficiaryRepository.save(new Beneficiary(
                priya, ananyaSavings.getAccountNumber(), "Ananya", ananya.getFullName()));
        beneficiaryRepository.save(favourite(new Beneficiary(
                rahul, priyaSavings.getAccountNumber(), "Priya", priya.getFullName())));

        // Seven months of history so the dashboard charts are not empty on first login.
        List<Backdated> history = new ArrayList<>();
        history.addAll(buildHistory(random, priyaSavings, rahulSavings));
        history.addAll(buildHistory(random, priyaCurrent, ananyaSavings));
        history.addAll(buildHistory(random, rahulSavings, priyaSavings));

        transactionRepository.saveAll(history.stream().map(Backdated::transaction).toList());
        transactionRepository.flush();
        backdate(history);

        log.info("""

                ==========================================================
                 NovaBank demo data ready
                ----------------------------------------------------------
                  Admin     : admin@novabank.io    / Admin@123
                  Customer  : priya@novabank.io    / Customer@123
                  Customer  : rahul@novabank.io    / Customer@123
                  Customer  : ananya@novabank.io   / Customer@123
                ----------------------------------------------------------
                  Swagger UI: http://localhost:8080/swagger-ui.html
                ==========================================================
                """);
        log.info("Seeded {} users, {} accounts, {} transactions",
                userRepository.count(), accountRepository.count(), transactionRepository.count());
    }

    /** Pairs a transaction with the historical timestamp it should carry. */
    private record Backdated(Transaction transaction, Instant at) {
    }

    /**
     * Rewrites {@code created_at} on the seeded rows.
     *
     * <p>Necessary because Spring Data's auditing listener stamps {@code @CreatedDate}
     * unconditionally on insert - it would collapse seven months of history onto today and leave
     * every chart flat. A direct UPDATE after the insert is the least invasive way to backdate
     * without weakening auditing for real writes.
     */
    private void backdate(List<Backdated> history) {
        List<Object[]> params = history.stream()
                .map(b -> new Object[]{Timestamp.from(b.at()), b.transaction().getId()})
                .toList();
        jdbcTemplate.batchUpdate("UPDATE transactions SET created_at = ? WHERE id = ?", params);
        log.debug("Backdated {} seeded transactions", params.size());
    }

    /**
     * Builds a plausible seven-month history: a monthly salary credit, a handful of card spends,
     * and one peer transfer per month written as a proper two-leg double entry.
     *
     * <p>Balances walk forward as the rows are generated, so every row's {@code balance_after} is
     * consistent with the row before it and the account's closing balance matches the ledger.
     */
    private List<Backdated> buildHistory(Random random, Account primary, Account peer) {
        List<Backdated> rows = new ArrayList<>();
        BigDecimal running = primary.getBalance();
        BigDecimal peerRunning = peer.getBalance();

        for (int monthsAgo = 6; monthsAgo >= 0; monthsAgo--) {
            Instant monthBase = Instant.now().minus(monthsAgo * 30L, ChronoUnit.DAYS);

            BigDecimal salary = new BigDecimal(65000 + random.nextInt(15000)).setScale(4);
            running = running.add(salary);
            rows.add(new Backdated(new Transaction(UUID.randomUUID().toString(), primary, null,
                    TransactionType.DEPOSIT, TransactionDirection.CREDIT, salary, running,
                    "Salary credit", "Salary"), monthBase.plus(1, ChronoUnit.DAYS)));

            int spends = 4 + random.nextInt(4);
            for (int i = 0; i < spends; i++) {
                int idx = random.nextInt(SPEND_CATEGORIES.length);
                BigDecimal amount = new BigDecimal(400 + random.nextInt(6500)).setScale(4);
                if (amount.compareTo(running) >= 0) {
                    continue;
                }
                running = running.subtract(amount);
                rows.add(new Backdated(new Transaction(UUID.randomUUID().toString(), primary, null,
                        TransactionType.WITHDRAWAL, TransactionDirection.DEBIT, amount, running,
                        MERCHANTS[idx], SPEND_CATEGORIES[idx]),
                        monthBase.plus(2L + i * 3L, ChronoUnit.DAYS)));
            }

            BigDecimal transferAmount = new BigDecimal(5000 + random.nextInt(10000)).setScale(4);
            if (transferAmount.compareTo(running) < 0) {
                running = running.subtract(transferAmount);
                peerRunning = peerRunning.add(transferAmount);

                String reference = UUID.randomUUID().toString();
                Instant at = monthBase.plus(20, ChronoUnit.DAYS);

                rows.add(new Backdated(new Transaction(reference, primary, peer,
                        TransactionType.TRANSFER_OUT, TransactionDirection.DEBIT, transferAmount,
                        running, "Transfer to " + peer.getOwner().getFullName(), "Transfer"), at));
                rows.add(new Backdated(new Transaction(reference, peer, primary,
                        TransactionType.TRANSFER_IN, TransactionDirection.CREDIT, transferAmount,
                        peerRunning, "Transfer from " + primary.getOwner().getFullName(),
                        "Transfer"), at));
            }
        }

        // The ledger is the source of truth: park the walked balances back onto the accounts.
        primary.setBalance(running);
        peer.setBalance(peerRunning);
        return rows;
    }

    private Beneficiary favourite(Beneficiary beneficiary) {
        beneficiary.setFavourite(true);
        return beneficiary;
    }

    private User user(String name, String email, String phone, String password,
                      Role role, LocalDate dob, String address) {
        User user = new User(name, email, phone, passwordEncoder.encode(password), role);
        user.setDateOfBirth(dob);
        user.setAddress(address);
        return userRepository.save(user);
    }

    private Account account(String number, User owner, AccountType type,
                            BigDecimal balance, BigDecimal dailyLimit) {
        return accountRepository.save(new Account(number, owner, type, balance, dailyLimit));
    }
}

package com.gurudutta.bank.transaction;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountRepository;
import com.gurudutta.bank.account.AccountType;
import com.gurudutta.bank.security.AppUserPrincipal;
import com.gurudutta.bank.transaction.dto.TransferRequest;
import com.gurudutta.bank.user.Role;
import com.gurudutta.bank.user.User;
import com.gurudutta.bank.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test that justifies the whole locking design.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}: a transactional test would roll everything
 * back into one transaction, and the worker threads would never see each other's committed data -
 * which is precisely the interaction being tested.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.seed.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:concurrency-it;DB_CLOSE_DELAY=-1",
})
@DisplayName("Concurrent money movement")
class TransferConcurrencyIT {

    private static final BigDecimal OPENING = new BigDecimal("100000.0000");

    @Autowired private MoneyMovementFacade moneyMovement;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionRepository transactionRepository;

    private Account alice;
    private Account bob;

    @BeforeEach
    void setUp() {
        User aliceUser = userRepository.save(
                new User("Alice Test", "alice-it@test.io", "9000000101", "x", Role.CUSTOMER));
        User bobUser = userRepository.save(
                new User("Bob Test", "bob-it@test.io", "9000000102", "x", Role.CUSTOMER));

        alice = accountRepository.save(new Account("910000000101", aliceUser,
                AccountType.CURRENT, OPENING, new BigDecimal("10000000.0000")));
        bob = accountRepository.save(new Account("910000000102", bobUser,
                AccountType.CURRENT, OPENING, new BigDecimal("10000000.0000")));

        authenticateAs(aliceUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("20 concurrent transfers all apply exactly once and conserve the total")
    void concurrentTransfersConserveMoney() throws Exception {
        int transfers = 20;
        BigDecimal amount = new BigDecimal("100.00");
        User aliceUser = alice.getOwner();

        AtomicInteger succeeded = new AtomicInteger();
        List<Callable<Void>> tasks = new ArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 0; i < transfers; i++) {
            final int index = i;
            tasks.add(() -> {
                // Each worker needs its own SecurityContext - the holder is thread-local, so the
                // main thread's authentication is invisible here.
                authenticateAs(aliceUser);
                // Release every thread at the same instant to maximise real contention.
                startGate.await();
                moneyMovement.transfer(new TransferRequest(
                        alice.getAccountNumber(), bob.getAccountNumber(),
                        amount, "concurrent test " + index, "Transfer", "it-key-" + index));
                succeeded.incrementAndGet();
                return null;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(transfers);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            startGate.countDown();
            for (Future<Void> future : futures) {
                future.get(45, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        BigDecimal aliceFinal = accountRepository.findById(alice.getId()).orElseThrow().getBalance();
        BigDecimal bobFinal = accountRepository.findById(bob.getId()).orElseThrow().getBalance();
        BigDecimal moved = amount.multiply(BigDecimal.valueOf(transfers));

        assertThat(succeeded.get())
                .as("every transfer should eventually succeed thanks to the retry policy")
                .isEqualTo(transfers);

        // The invariant that matters: not one rupee created, not one destroyed.
        assertThat(aliceFinal.add(bobFinal))
                .as("total money in the system is unchanged")
                .isEqualByComparingTo(OPENING.add(OPENING));

        assertThat(aliceFinal)
                .as("no lost update: every debit was applied")
                .isEqualByComparingTo(OPENING.subtract(moved));
        assertThat(bobFinal)
                .as("every credit was applied")
                .isEqualByComparingTo(OPENING.add(moved));

        // Double entry: 20 transfers must produce exactly 40 ledger rows.
        assertThat(transactionRepository.count())
                .as("each transfer writes one debit leg and one credit leg")
                .isEqualTo(transfers * 2L);
    }

    @Test
    @DisplayName("Opposing A->B and B->A transfers do not deadlock")
    void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        int pairs = 10;
        BigDecimal amount = new BigDecimal("50.00");
        User aliceUser = alice.getOwner();
        User bobUser = bob.getOwner();

        List<Callable<Void>> tasks = new ArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 0; i < pairs; i++) {
            final int index = i;
            // A -> B
            tasks.add(() -> {
                authenticateAs(aliceUser);
                startGate.await();
                moneyMovement.transfer(new TransferRequest(alice.getAccountNumber(),
                        bob.getAccountNumber(), amount, "a2b", "Transfer", "it-a2b-" + index));
                return null;
            });
            // B -> A, at the same moment. Without a consistent lock order these two would each
            // hold one row and wait forever for the other.
            tasks.add(() -> {
                authenticateAs(bobUser);
                startGate.await();
                moneyMovement.transfer(new TransferRequest(bob.getAccountNumber(),
                        alice.getAccountNumber(), amount, "b2a", "Transfer", "it-b2a-" + index));
                return null;
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            startGate.countDown();
            for (Future<Void> future : futures) {
                future.get(45, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        BigDecimal aliceFinal = accountRepository.findById(alice.getId()).orElseThrow().getBalance();
        BigDecimal bobFinal = accountRepository.findById(bob.getId()).orElseThrow().getBalance();

        // Equal traffic both ways, so both balances must land exactly where they started.
        assertThat(aliceFinal).isEqualByComparingTo(OPENING);
        assertThat(bobFinal).isEqualByComparingTo(OPENING);
    }

    @Test
    @DisplayName("The same idempotency key charges the customer only once")
    void repeatedIdempotencyKeyIsAppliedOnce() {
        String key = "it-duplicate-key";
        TransferRequest request = new TransferRequest(alice.getAccountNumber(),
                bob.getAccountNumber(), new BigDecimal("250.00"), "dupe", "Transfer", key);

        moneyMovement.transfer(request);
        moneyMovement.transfer(request);
        moneyMovement.transfer(request);

        assertThat(accountRepository.findById(alice.getId()).orElseThrow().getBalance())
                .as("three identical submissions move the money once")
                .isEqualByComparingTo(OPENING.subtract(new BigDecimal("250.00")));

        assertThat(transactionRepository.count())
                .as("only the first submission wrote ledger rows")
                .isEqualTo(2L);
    }

    private void authenticateAs(User user) {
        AppUserPrincipal principal = new AppUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

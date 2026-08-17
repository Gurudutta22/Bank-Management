package com.gurudutta.bank.transaction;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountRepository;
import com.gurudutta.bank.account.AccountStatus;
import com.gurudutta.bank.account.AccountType;
import com.gurudutta.bank.audit.AuditService;
import com.gurudutta.bank.common.exception.BusinessException;
import com.gurudutta.bank.common.exception.InsufficientFundsException;
import com.gurudutta.bank.config.AppProperties;
import com.gurudutta.bank.security.AppUserPrincipal;
import com.gurudutta.bank.transaction.dto.TransferRequest;
import com.gurudutta.bank.transaction.dto.WithdrawRequest;
import com.gurudutta.bank.user.Role;
import com.gurudutta.bank.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Fast unit tests for the business rules, with the database mocked away.
 *
 * <p>Complements {@link TransferConcurrencyIT}: that test proves the locking behaves under real
 * concurrency, these prove each individual rule rejects what it should - in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Money movement rules")
class TransactionServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditService auditService;

    @InjectMocks private TransactionService transactionService;

    private User owner;
    private Account savings;
    private Account current;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("x".repeat(40), 15, 7, "novabank"),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Limits(
                        new BigDecimal("500.00"),      // min opening balance
                        new BigDecimal("500000.00"),   // max single transaction
                        new BigDecimal("100000.00"),   // default daily limit
                        new BigDecimal("500.00"),      // savings minimum balance
                        new BigDecimal("10000.00")));  // current overdraft
        ReflectionTestUtils.setField(transactionService, "props", props);

        owner = new User("Test Owner", "owner@test.io", "9000000001", "hash", Role.CUSTOMER);
        ReflectionTestUtils.setField(owner, "id", 1L);

        savings = account("900000000001", owner, AccountType.SAVINGS, "10000.0000", 10L);
        current = account("900000000002", owner, AccountType.CURRENT, "5000.0000", 11L);

        authenticate(owner);
        lenient().when(transactionRepository.sumDebitsSince(anyLong(), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a withdrawal that would break the savings minimum balance is refused")
    void savingsMinimumBalanceIsEnforced() {
        when(accountRepository.findByAccountNumberForUpdate("900000000001"))
                .thenReturn(Optional.of(savings));

        // 10,000 balance minus the 500 floor leaves 9,500 spendable, so 9,600 must fail.
        assertThatThrownBy(() -> transactionService.withdraw(
                new WithdrawRequest("900000000001", new BigDecimal("9600.00"), null, null, null)))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(savings.getBalance()).isEqualByComparingTo("10000.0000");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("a current account may overdraw into its arranged buffer")
    void currentAccountOverdraftIsAllowed() {
        when(accountRepository.findByAccountNumberForUpdate("900000000002"))
                .thenReturn(Optional.of(current));

        // 5,000 balance + 10,000 overdraft = 15,000 spendable.
        transactionService.withdraw(
                new WithdrawRequest("900000000002", new BigDecimal("12000.00"), null, null, null));

        assertThat(current.getBalance()).isEqualByComparingTo("-7000.0000");
    }

    @Test
    @DisplayName("a frozen account cannot be debited")
    void frozenAccountCannotBeDebited() {
        savings.setStatus(AccountStatus.FROZEN);
        when(accountRepository.findByAccountNumberForUpdate("900000000001"))
                .thenReturn(Optional.of(savings));

        assertThatThrownBy(() -> transactionService.withdraw(
                new WithdrawRequest("900000000001", new BigDecimal("100.00"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ACCOUNT_NOT_DEBITABLE");
    }

    @Test
    @DisplayName("transferring to the same account is rejected before any lock is taken")
    void selfTransferIsRejected() {
        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(
                "900000000001", "900000000001", new BigDecimal("100.00"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("SAME_ACCOUNT_TRANSFER");

        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
    }

    @Test
    @DisplayName("a transfer beyond the daily limit is refused even when funds are available")
    void dailyTransferLimitIsEnforced() {
        Account destination = account("900000000003", otherUser(), AccountType.SAVINGS, "1000.0000", 12L);
        when(accountRepository.findByAccountNumberForUpdate("900000000001")).thenReturn(Optional.of(savings));
        when(accountRepository.findByAccountNumberForUpdate("900000000003")).thenReturn(Optional.of(destination));
        // Already moved 99,500 today against a 100,000 limit.
        when(transactionRepository.sumDebitsSince(anyLong(), any(Instant.class)))
                .thenReturn(new BigDecimal("99500.00"));

        assertThatThrownBy(() -> transactionService.transfer(new TransferRequest(
                "900000000001", "900000000003", new BigDecimal("1000.00"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("DAILY_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("a single transaction above the hard cap is refused")
    void singleTransactionCapIsEnforced() {
        assertThatThrownBy(() -> transactionService.withdraw(
                new WithdrawRequest("900000000001", new BigDecimal("500001.00"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("AMOUNT_EXCEEDS_LIMIT");
    }

    @Test
    @DisplayName("a customer cannot debit an account they do not own")
    void cannotTransactOnSomeoneElsesAccount() {
        Account strangersAccount = account("900000000009", otherUser(), AccountType.SAVINGS, "9999.0000", 20L);
        when(accountRepository.findByAccountNumberForUpdate("900000000009"))
                .thenReturn(Optional.of(strangersAccount));

        // 404, not 403: a 403 would confirm to an attacker that the account number is real.
        assertThatThrownBy(() -> transactionService.withdraw(
                new WithdrawRequest("900000000009", new BigDecimal("100.00"), null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    @DisplayName("a transfer writes two ledger legs sharing one reference")
    void transferWritesBothLegs() {
        Account destination = account("900000000003", otherUser(), AccountType.SAVINGS, "1000.0000", 12L);
        when(accountRepository.findByAccountNumberForUpdate("900000000001")).thenReturn(Optional.of(savings));
        when(accountRepository.findByAccountNumberForUpdate("900000000003")).thenReturn(Optional.of(destination));

        transactionService.transfer(new TransferRequest(
                "900000000001", "900000000003", new BigDecimal("1500.00"), "rent", "Rent", null));

        assertThat(savings.getBalance()).isEqualByComparingTo("8500.0000");
        assertThat(destination.getBalance()).isEqualByComparingTo("2500.0000");
        verify(transactionRepository, org.mockito.Mockito.times(2)).save(any(Transaction.class));
    }

    @Test
    @DisplayName("a replayed idempotency key returns the original result without moving money")
    void idempotentReplayDoesNotMoveMoney() {
        Transaction original = new Transaction("ref-1", savings, null, TransactionType.WITHDRAWAL,
                TransactionDirection.DEBIT, new BigDecimal("100.0000"), new BigDecimal("9900.0000"),
                "original", "Withdrawal");
        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(original));

        var response = transactionService.withdraw(
                new WithdrawRequest("900000000001", new BigDecimal("100.00"), null, null, "key-1"));

        assertThat(response.reference()).isEqualTo("ref-1");
        assertThat(savings.getBalance()).isEqualByComparingTo("10000.0000");
        verify(accountRepository, never()).findByAccountNumberForUpdate(any());
    }

    /* ---------------------------------------------------------------- helpers */

    private Account account(String number, User user, AccountType type, String balance, long id) {
        Account account = new Account(number, user, type, new BigDecimal(balance),
                new BigDecimal("100000.0000"));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private User otherUser() {
        User other = new User("Someone Else", "other@test.io", "9000000002", "hash", Role.CUSTOMER);
        ReflectionTestUtils.setField(other, "id", 99L);
        return other;
    }

    private void authenticate(User user) {
        AppUserPrincipal principal = new AppUserPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}

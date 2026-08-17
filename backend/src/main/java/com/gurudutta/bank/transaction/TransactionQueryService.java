package com.gurudutta.bank.transaction;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountRepository;
import com.gurudutta.bank.account.AccountService;
import com.gurudutta.bank.account.dto.AccountResponse;
import com.gurudutta.bank.common.dto.PageResponse;
import com.gurudutta.bank.common.util.Money;
import com.gurudutta.bank.security.SecurityUtils;
import com.gurudutta.bank.transaction.dto.DashboardResponse;
import com.gurudutta.bank.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side of the transaction module.
 *
 * <p>Split from {@link TransactionService} on purpose. Writes need locks, isolation and audit
 * trails; reads need none of that and run under {@code readOnly = true}, which lets Hibernate skip
 * dirty checking and lets the driver route to a replica if one is ever added.
 */
@Service
@Transactional(readOnly = true)
public class TransactionQueryService {

    private static final ZoneId BANK_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM");

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;

    public TransactionQueryService(TransactionRepository transactionRepository,
                                   AccountRepository accountRepository,
                                   AccountService accountService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
    }

    /**
     * Paged, filtered transaction history across the caller's accounts.
     *
     * <p>Scoping to {@code accountIds} owned by the caller is the authorization boundary: the
     * filter cannot be widened by tampering with query parameters.
     */
    public PageResponse<TransactionResponse> search(String accountNumber,
                                                    TransactionType type,
                                                    LocalDate from,
                                                    LocalDate to,
                                                    BigDecimal minAmount,
                                                    String search,
                                                    int page,
                                                    int size) {
        List<Long> accountIds = resolveAccountIds(accountNumber);
        if (accountIds.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0, true, true);
        }

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100), // cap the page size so a client cannot request 1e6 rows
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Transaction> result = transactionRepository.search(
                accountIds, type,
                from == null ? null : from.atStartOfDay(BANK_ZONE).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(BANK_ZONE).toInstant(),
                minAmount,
                search,
                pageable);

        return PageResponse.from(result, TransactionResponse::from);
    }

    /** Everything the customer dashboard renders, in a single round trip. */
    public DashboardResponse dashboard() {
        Long userId = SecurityUtils.currentUser().getId();
        List<Account> accounts = accountRepository.findByOwnerIdOrderByCreatedAtDesc(userId);
        List<Long> accountIds = accounts.stream().map(Account::getId).toList();

        BigDecimal totalBalance = accounts.stream()
                .filter(a -> a.getStatus() != com.gurudutta.bank.account.AccountStatus.CLOSED)
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (accountIds.isEmpty()) {
            return new DashboardResponse(BigDecimal.ZERO, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(), List.of(), List.of(), List.of());
        }

        Instant monthStart = YearMonth.now(BANK_ZONE).atDay(1).atStartOfDay(BANK_ZONE).toInstant();

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal spend = BigDecimal.ZERO;
        for (Object[] row : transactionRepository.sumByDirectionSince(accountIds, monthStart)) {
            TransactionDirection direction = (TransactionDirection) row[0];
            BigDecimal total = (BigDecimal) row[1];
            if (direction == TransactionDirection.CREDIT) {
                income = total;
            } else {
                spend = total;
            }
        }

        List<DashboardResponse.CategorySpend> byCategory =
                transactionRepository.sumSpendByCategorySince(accountIds, monthStart).stream()
                        .limit(6)
                        .map(row -> new DashboardResponse.CategorySpend(
                                (String) row[0], Money.normalise((BigDecimal) row[1])))
                        .toList();

        List<TransactionResponse> recent =
                transactionRepository.findRecent(accountIds, PageRequest.of(0, 8)).stream()
                        .map(TransactionResponse::from)
                        .toList();

        return new DashboardResponse(
                Money.normalise(totalBalance),
                accounts.size(),
                Money.normalise(income),
                Money.normalise(spend),
                accounts.stream().map(AccountResponse::from).toList(),
                recent,
                byCategory,
                monthlyTrend(accountIds));
    }

    /**
     * Six months of income vs spend for the dashboard area chart.
     *
     * <p>Fetches the whole window in one query and buckets by month in memory, rather than issuing
     * six queries in a loop - the classic N+1 mistake, just with months instead of rows.
     */
    private List<DashboardResponse.MonthlyPoint> monthlyTrend(List<Long> accountIds) {
        YearMonth current = YearMonth.now(BANK_ZONE);
        YearMonth earliest = current.minusMonths(5);

        Instant from = earliest.atDay(1).atStartOfDay(BANK_ZONE).toInstant();
        Instant to = current.plusMonths(1).atDay(1).atStartOfDay(BANK_ZONE).toInstant();

        Map<YearMonth, BigDecimal[]> buckets = new LinkedHashMap<>();
        for (int i = 5; i >= 0; i--) {
            buckets.put(current.minusMonths(i), new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }

        for (Object[] row : transactionRepository.findAmountsInRange(accountIds, from, to)) {
            YearMonth month = YearMonth.from(((Instant) row[0]).atZone(BANK_ZONE));
            BigDecimal[] bucket = buckets.get(month);
            if (bucket == null) {
                continue;
            }
            int slot = row[1] == TransactionDirection.CREDIT ? 0 : 1;
            bucket[slot] = bucket[slot].add((BigDecimal) row[2]);
        }

        List<DashboardResponse.MonthlyPoint> points = new ArrayList<>();
        buckets.forEach((month, totals) -> points.add(new DashboardResponse.MonthlyPoint(
                month.format(MONTH_LABEL), Money.normalise(totals[0]), Money.normalise(totals[1]))));
        return points;
    }

    /**
     * Builds an account statement as CSV.
     *
     * <p>Every field is quoted and embedded quotes are doubled - the RFC 4180 rule. Skipping that
     * would let a description containing a comma silently shift every later column.
     */
    public String statementCsv(String accountNumber, LocalDate from, LocalDate to) {
        Account account = accountService.requireOwnedAccount(accountNumber);
        Instant fromInstant = from.atStartOfDay(BANK_ZONE).toInstant();
        Instant toInstant = to.plusDays(1).atStartOfDay(BANK_ZONE).toInstant();

        List<Transaction> transactions =
                transactionRepository.findForStatement(accountNumber, fromInstant, toInstant);

        StringBuilder csv = new StringBuilder();
        csv.append("Date,Reference,Type,Description,Category,Debit,Credit,Balance\n");

        for (Transaction tx : transactions) {
            boolean isCredit = tx.getDirection() == TransactionDirection.CREDIT;
            csv.append(quote(tx.getCreatedAt().atZone(BANK_ZONE)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))).append(',')
                    .append(quote(tx.getReference())).append(',')
                    .append(quote(tx.getType().name())).append(',')
                    .append(quote(tx.getDescription())).append(',')
                    .append(quote(tx.getCategory())).append(',')
                    .append(isCredit ? "" : Money.normalise(tx.getAmount()).toPlainString()).append(',')
                    .append(isCredit ? Money.normalise(tx.getAmount()).toPlainString() : "").append(',')
                    .append(Money.normalise(tx.getBalanceAfter()).toPlainString())
                    .append('\n');
        }

        csv.append("\nAccount,").append(quote(account.getAccountNumber()))
                .append("\nHolder,").append(quote(account.getOwner().getFullName()))
                .append("\nPeriod,").append(quote(from + " to " + to))
                .append("\nClosing balance,")
                .append(Money.normalise(account.getBalance()).toPlainString())
                .append('\n');

        return csv.toString();
    }

    private static String quote(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /**
     * Turns an optional account-number filter into the set of account ids the caller may query.
     * Passing no account number means "all of my accounts".
     */
    private List<Long> resolveAccountIds(String accountNumber) {
        if (accountNumber != null && !accountNumber.isBlank()) {
            return List.of(accountService.requireOwnedAccount(accountNumber).getId());
        }
        return accountRepository
                .findByOwnerIdOrderByCreatedAtDesc(SecurityUtils.currentUser().getId())
                .stream()
                .map(Account::getId)
                .toList();
    }
}

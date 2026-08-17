package com.gurudutta.bank.admin;

import com.gurudutta.bank.account.Account;
import com.gurudutta.bank.account.AccountRepository;
import com.gurudutta.bank.account.AccountStatus;
import com.gurudutta.bank.account.dto.AccountResponse;
import com.gurudutta.bank.admin.dto.AdminStatsResponse;
import com.gurudutta.bank.admin.dto.AuditLogResponse;
import com.gurudutta.bank.admin.dto.StatusUpdateRequest;
import com.gurudutta.bank.audit.AuditLogRepository;
import com.gurudutta.bank.audit.AuditService;
import com.gurudutta.bank.auth.RefreshTokenRepository;
import com.gurudutta.bank.common.dto.PageResponse;
import com.gurudutta.bank.common.exception.InvalidOperationException;
import com.gurudutta.bank.common.exception.ResourceNotFoundException;
import com.gurudutta.bank.common.util.Money;
import com.gurudutta.bank.security.SecurityUtils;
import com.gurudutta.bank.transaction.TransactionRepository;
import com.gurudutta.bank.user.Role;
import com.gurudutta.bank.user.User;
import com.gurudutta.bank.user.UserRepository;
import com.gurudutta.bank.user.dto.UserResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Back-office operations.
 *
 * <p>Every method carries {@code @PreAuthorize("hasRole('ADMIN')")} even though
 * {@code /api/v1/admin/**} is already locked down in {@link com.gurudutta.bank.config.SecurityConfig}.
 * That duplication is intentional: if someone later exposes one of these methods through a
 * different URL, the authorization travels with the method rather than with the route.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository,
                        AccountRepository accountRepository,
                        TransactionRepository transactionRepository,
                        AuditLogRepository auditLogRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse stats() {
        Instant thirtyDaysAgo = Instant.now().minus(Duration.ofDays(30));

        long totalUsers = userRepository.count();
        long customers = userRepository.countByRole(Role.CUSTOMER);
        long admins = userRepository.countByRole(Role.ADMIN);

        return new AdminStatsResponse(
                totalUsers,
                customers,
                admins,
                userRepository.countByCreatedAtAfter(thirtyDaysAgo),
                accountRepository.count(),
                accountRepository.countByStatus(AccountStatus.ACTIVE),
                accountRepository.countByStatus(AccountStatus.FROZEN),
                accountRepository.countByStatus(AccountStatus.CLOSED),
                Money.normalise(accountRepository.totalHoldings()),
                transactionRepository.countByCreatedAtAfter(thirtyDaysAgo),
                Money.normalise(transactionRepository.totalVolumeSince(thirtyDaysAgo)),
                dailyVolume(thirtyDaysAgo));
    }

    private List<AdminStatsResponse.DailyVolumePoint> dailyVolume(Instant since) {
        // The query returns one row per (day, direction); fold them into one row per day.
        Map<String, BigDecimal[]> byDay = new LinkedHashMap<>();
        Map<String, Long> counts = new LinkedHashMap<>();

        for (Object[] row : transactionRepository.dailyVolumeSince(since)) {
            String day = String.valueOf((LocalDate) row[0]);
            BigDecimal[] totals = byDay.computeIfAbsent(day,
                    k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            boolean credit = row[1] == com.gurudutta.bank.transaction.TransactionDirection.CREDIT;
            totals[credit ? 0 : 1] = totals[credit ? 0 : 1].add((BigDecimal) row[2]);
            counts.merge(day, (Long) row[3], Long::sum);
        }

        List<AdminStatsResponse.DailyVolumePoint> points = new ArrayList<>();
        byDay.forEach((day, totals) -> points.add(new AdminStatsResponse.DailyVolumePoint(
                day, Money.normalise(totals[0]), Money.normalise(totals[1]),
                counts.getOrDefault(day, 0L))));
        return points;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listUsers(String search, Role role, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(userRepository.search(search, role, pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<AccountResponse> listAccounts(String search, AccountStatus status,
                                                      int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(accountRepository.search(search, status, pageable),
                AccountResponse::from);
    }

    /** Enable or disable a customer login. Disabling also kills their live sessions. */
    @Transactional
    public UserResponse updateUserStatus(String publicId, StatusUpdateRequest request) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", publicId));

        if (user.getId().equals(SecurityUtils.currentUser().getId())) {
            // Prevents an admin from locking themselves out of the system entirely.
            throw new InvalidOperationException("You cannot change your own account status.",
                    "SELF_STATUS_CHANGE");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new InvalidOperationException("Administrator accounts cannot be disabled here.",
                    "ADMIN_IMMUTABLE");
        }

        boolean enable = "ENABLED".equalsIgnoreCase(request.status())
                || "ACTIVE".equalsIgnoreCase(request.status());
        user.setEnabled(enable);

        if (!enable) {
            refreshTokenRepository.revokeAllForUser(user.getId());
        }

        auditService.success(enable ? "USER_ENABLED" : "USER_DISABLED", "User", publicId,
                request.reason() == null ? "No reason given" : request.reason());
        return UserResponse.from(user);
    }

    /** Freeze, unfreeze or close an account from the back office. */
    @Transactional
    public AccountResponse updateAccountStatus(String accountNumber, StatusUpdateRequest request) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> ResourceNotFoundException.of("Account", accountNumber));

        AccountStatus target;
        try {
            target = AccountStatus.valueOf(request.status().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidOperationException(
                    "Status must be one of ACTIVE, FROZEN or CLOSED.", "INVALID_STATUS");
        }

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidOperationException("A closed account cannot be reopened.",
                    "ACCOUNT_CLOSED");
        }
        if (target == AccountStatus.CLOSED && !Money.isZero(account.getBalance())) {
            throw new InvalidOperationException(
                    "An account can only be closed once its balance reaches zero.",
                    "NON_ZERO_BALANCE");
        }

        account.setStatus(target);
        auditService.success("ACCOUNT_STATUS_CHANGED", "Account", accountNumber,
                "Status set to %s. Reason: %s".formatted(target,
                        request.reason() == null ? "not given" : request.reason()));
        return AccountResponse.from(account);
    }

    @Transactional
    public UserResponse setKycVerified(String publicId, boolean verified) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> ResourceNotFoundException.of("User", publicId));
        user.setKycVerified(verified);
        auditService.success(verified ? "KYC_VERIFIED" : "KYC_REVOKED", "User", publicId,
                "KYC flag set to " + verified);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> auditLogs(String search, String action, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(auditLogRepository.search(search, action, pageable),
                AuditLogResponse::from);
    }
}

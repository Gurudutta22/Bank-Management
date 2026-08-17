package com.gurudutta.bank.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/** Everything the admin dashboard needs, assembled in one call instead of six. */
public record AdminStatsResponse(long totalUsers,
                                 long totalCustomers,
                                 long totalAdmins,
                                 long newUsersLast30Days,
                                 long totalAccounts,
                                 long activeAccounts,
                                 long frozenAccounts,
                                 long closedAccounts,
                                 BigDecimal totalHoldings,
                                 long transactionsLast30Days,
                                 BigDecimal volumeLast30Days,
                                 List<DailyVolumePoint> dailyVolume) {

    public record DailyVolumePoint(String date, BigDecimal credit, BigDecimal debit, long count) {
    }
}

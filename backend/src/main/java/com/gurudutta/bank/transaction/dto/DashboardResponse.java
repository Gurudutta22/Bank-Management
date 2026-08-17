package com.gurudutta.bank.transaction.dto;

import com.gurudutta.bank.account.dto.AccountResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * The customer dashboard payload.
 *
 * <p>Deliberately one endpoint rather than five: the landing screen would otherwise fire a burst of
 * parallel requests on every page load, and each one would re-authenticate and re-open a
 * transaction for the same user.
 */
public record DashboardResponse(BigDecimal totalBalance,
                                int accountCount,
                                BigDecimal monthlyIncome,
                                BigDecimal monthlySpend,
                                List<AccountResponse> accounts,
                                List<TransactionResponse> recentTransactions,
                                List<CategorySpend> spendByCategory,
                                List<MonthlyPoint> monthlyTrend) {

    public record CategorySpend(String category, BigDecimal amount) {
    }

    public record MonthlyPoint(String month, BigDecimal income, BigDecimal spend) {
    }
}

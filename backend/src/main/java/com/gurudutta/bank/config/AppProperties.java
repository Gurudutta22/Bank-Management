package com.gurudutta.bank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Type-safe binding for the {@code app.*} block of application.yml.
 *
 * <p>Keeps tunables (JWT lifetimes, limits, CORS origins) out of the code and lets every value be
 * overridden per environment with a plain environment variable.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Limits limits) {

    public record Jwt(String secret, long accessTokenMinutes, long refreshTokenDays, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }

    public record Limits(BigDecimal minOpeningBalance,
                         BigDecimal maxTransactionAmount,
                         BigDecimal defaultDailyTransferLimit,
                         BigDecimal savingsMinimumBalance,
                         BigDecimal currentOverdraftLimit) {
    }
}

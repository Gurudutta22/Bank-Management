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
public record AppProperties(Jwt jwt, Cors cors, Limits limits, Demo demo) {

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

    /**
     * Demo-only conveniences that would be unsafe in a real deployment.
     *
     * <p>{@code autoCreateRecipient}: when a transfer targets a 12-digit account number that
     * does not yet exist, auto-create a zero-balance account for a pool "walk-in" customer so
     * the money has a real destination. Keeps double-entry intact (every rupee lands somewhere),
     * but turns the destination endpoint into an enumeration oracle - which is why the flag
     * exists and defaults to disabled under the postgres profile.
     */
    public record Demo(boolean autoCreateRecipient) {
    }
}

package com.gurudutta.bank.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money helpers.
 *
 * <p>Every amount in this system is a {@link BigDecimal}. {@code double} cannot represent 0.10
 * exactly, so a few thousand floating-point additions drift by real currency - which is why no
 * banking system stores money in a binary float.
 */
public final class Money {

    /** Storage scale. Matches NUMERIC(19,4) in the schema. */
    public static final int SCALE = 4;
    /** Display/settlement scale - what actually moves between accounts. */
    public static final int DISPLAY_SCALE = 2;

    private Money() {
    }

    /** Normalises a client-supplied amount to 2dp, rounding half-up as a teller would. */
    public static BigDecimal normalise(BigDecimal amount) {
        return amount.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal store(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Value comparison that ignores scale, so 100 and 100.00 are treated as equal. */
    public static boolean isZero(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean gte(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0;
    }

    public static boolean lt(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) < 0;
    }
}

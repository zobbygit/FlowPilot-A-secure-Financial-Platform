package com.flowpilot.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/** Single source of truth for recurrence and monthly-equivalent math. */
public final class BillMath {
    private BillMath() {}

    public static LocalDate advance(LocalDate d, String frequency) {
        return switch (frequency) {
            case "WEEKLY" -> d.plusWeeks(1);
            case "MONTHLY" -> d.plusMonths(1);
            case "QUARTERLY" -> d.plusMonths(3);
            case "YEARLY" -> d.plusYears(1);
            default -> throw new IllegalArgumentException("Unknown frequency " + frequency);
        };
    }

    public static BigDecimal monthly(BigDecimal amount, String frequency) {
        return switch (frequency) {
            case "WEEKLY" -> amount.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            case "MONTHLY" -> amount;
            case "QUARTERLY" -> amount.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);
            case "YEARLY" -> amount.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP);
            default -> throw new IllegalArgumentException("Unknown frequency " + frequency);
        };
    }
}

package com.flowpilot.households;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.common.ApiException;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class SplitCalculatorTest {
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private static BigDecimal sum(Map<UUID, BigDecimal> m) { return m.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    @Test
    void equalSplitAlwaysSumsToTheTotalAndAssignsLeftoversDeterministically() {
        var s = SplitCalculator.equal(d("100.00"), List.of(C, A, B));
        assertEquals(0, d("100").compareTo(sum(s)));
        assertEquals(0, d("33.3334").compareTo(s.get(A)));      // the first member (sorted by id) takes the leftover unit
        assertEquals(0, d("33.3333").compareTo(s.get(B)));
        assertEquals(s, SplitCalculator.equal(d("100.00"), List.of(B, C, A)));   // input order never matters
    }

    @Test
    void percentSplitsMustTotal100AndStillSumExactly() {
        var s = SplitCalculator.percent(d("10"), Map.of(A, d("33.33"), B, d("33.33"), C, d("33.34")));
        assertEquals(0, d("10").compareTo(sum(s)));
        assertEquals("PERCENT_TOTAL", assertThrows(ApiException.class, () -> SplitCalculator.percent(d("10"), Map.of(A, d("50"), B, d("40")))).code());
    }

    @Test
    void exactSharesMustMatchTheTotal() {
        assertDoesNotThrow(() -> SplitCalculator.exact(d("30"), Map.of(A, d("10"), B, d("20"))));
        assertEquals("SHARES_MISMATCH", assertThrows(ApiException.class, () -> SplitCalculator.exact(d("30"), Map.of(A, d("10"), B, d("19")))).code());
        assertEquals("INVALID_SHARE", assertThrows(ApiException.class, () -> SplitCalculator.exact(d("30"), Map.of(A, d("-5"), B, d("35")))).code());
    }

    @Test
    void settleUpClearsEveryBalanceWithFewTransfers() {
        Map<UUID, BigDecimal> net = new LinkedHashMap<>(); net.put(A, d("60")); net.put(B, d("-20")); net.put(C, d("-40"));
        var plan = SplitCalculator.settleUp(net);
        assertEquals(2, plan.size());
        Map<UUID, BigDecimal> after = new HashMap<>(net);
        plan.forEach(t -> { after.merge(t.from(), t.amount(), BigDecimal::add); after.merge(t.to(), t.amount().negate(), BigDecimal::add); });
        after.values().forEach(v -> assertEquals(0, v.signum()));
    }

    @Test
    void nothingToSettleMeansNoTransfers() {
        assertTrue(SplitCalculator.settleUp(Map.of(A, BigDecimal.ZERO, B, BigDecimal.ZERO)).isEmpty());
    }
}

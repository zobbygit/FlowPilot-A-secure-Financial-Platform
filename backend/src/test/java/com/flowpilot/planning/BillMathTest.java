package com.flowpilot.planning;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BillMathTest {
    @Test
    void advancesDueDatesByFrequency() {
        LocalDate d = LocalDate.of(2026, 1, 31);
        assertEquals(LocalDate.of(2026, 2, 7), BillMath.advance(d, "WEEKLY"));
        assertEquals(LocalDate.of(2026, 2, 28), BillMath.advance(d, "MONTHLY"));   // month-end clamps, never overflows
        assertEquals(LocalDate.of(2026, 4, 30), BillMath.advance(d, "QUARTERLY"));
        assertEquals(LocalDate.of(2027, 1, 31), BillMath.advance(d, "YEARLY"));
        assertThrows(IllegalArgumentException.class, () -> BillMath.advance(d, "DAILY"));
    }

    @Test
    void convertsEveryFrequencyToAMonthlyEquivalent() {
        assertEquals(0, new BigDecimal("10").compareTo(BillMath.monthly(new BigDecimal("10"), "MONTHLY")));
        assertEquals(0, new BigDecimal("4.0000").compareTo(BillMath.monthly(new BigDecimal("12"), "QUARTERLY")));
        assertEquals(0, new BigDecimal("10.0000").compareTo(BillMath.monthly(new BigDecimal("120"), "YEARLY")));
        assertEquals(0, new BigDecimal("43.3333").compareTo(BillMath.monthly(new BigDecimal("10"), "WEEKLY")));
    }
}

package com.flowpilot.csv;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.csv.RecurringDetector.Entry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecurringDetectorTest {
    private static Entry e(LocalDate d, String amt, String desc) { return new Entry(d, new BigDecimal(amt), desc); }

    @Test
    void normalisesMerchantNoise() {
        assertEquals("netflix", RecurringDetector.normalize("NETFLIX.COM 4412 POS"));
        assertEquals("netflix", RecurringDetector.normalize("Netflix #882"));
        assertEquals("", RecurringDetector.normalize("12345"));
    }

    @Test
    void findsAMonthlySubscriptionWithReasonsAndANextDate() {
        List<Entry> es = new ArrayList<>();
        for (int m = 1; m <= 5; m++) es.add(e(LocalDate.of(2026, m, 5), "-15.99", "NETFLIX.COM " + (4000 + m)));
        var c = RecurringDetector.detect(es);
        assertEquals(1, c.size());
        assertEquals("MONTHLY", c.get(0).frequency());
        assertEquals("15.99", c.get(0).typicalAmount());
        assertTrue(c.get(0).confidence() >= 85);
        assertFalse(c.get(0).reasons().isEmpty());
        assertNull(c.get(0).priceChangeNote());
    }

    @Test
    void flagsAPossiblePriceChange() {
        List<Entry> es = new ArrayList<>();
        for (int m = 1; m <= 4; m++) es.add(e(LocalDate.of(2026, m, 10), "-9.99", "Spotify"));
        es.add(e(LocalDate.of(2026, 5, 10), "-11.99", "Spotify"));
        var c = RecurringDetector.detect(es);
        assertNotNull(c.get(0).priceChangeNote());
    }

    @Test
    void doesNotTreatIrregularRepeatsIncomeOrTwoOffsAsRecurring() {
        List<Entry> irregular = List.of(e(LocalDate.of(2026, 1, 3), "-4.50", "Cafe"), e(LocalDate.of(2026, 1, 9), "-4.50", "Cafe"), e(LocalDate.of(2026, 2, 21), "-4.50", "Cafe"), e(LocalDate.of(2026, 2, 22), "-4.50", "Cafe"));
        assertTrue(RecurringDetector.detect(irregular).isEmpty());
        List<Entry> income = List.of(e(LocalDate.of(2026, 1, 1), "2000", "Salary"), e(LocalDate.of(2026, 2, 1), "2000", "Salary"), e(LocalDate.of(2026, 3, 1), "2000", "Salary"));
        assertTrue(RecurringDetector.detect(income).isEmpty());
        List<Entry> two = List.of(e(LocalDate.of(2026, 1, 1), "-9", "Gym"), e(LocalDate.of(2026, 2, 1), "-9", "Gym"));
        assertTrue(RecurringDetector.detect(two).isEmpty());
    }
}

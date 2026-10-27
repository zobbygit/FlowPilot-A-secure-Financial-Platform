package com.flowpilot.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.sandbox.SandboxEngine.Event;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SandboxEngineTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 1, 1);
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void noEventsMeansScenarioEqualsBaseline() {
        var r = SandboxEngine.simulate(d("1000"), d("10"), Map.of(), List.of(), TODAY, 30);
        assertEquals(0, r.endBaseline().compareTo(r.endScenario()));
        assertEquals(0, d("1300").compareTo(r.endBaseline()));
    }

    @Test
    void aOneOffPurchaseLowersTheScenarioOnlyAndCanGoNegative() {
        var buy = new Event("ONE_OFF", "Laptop", TODAY.plusDays(10), d("-1500"), null);
        var r = SandboxEngine.simulate(d("1000"), d("0"), Map.of(), List.of(buy), TODAY, 30);
        assertEquals(0, d("1000").compareTo(r.endBaseline()));
        assertEquals(0, d("-500").compareTo(r.endScenario()));
        assertEquals(TODAY.plusDays(10), r.firstNegativeScenario());
        assertEquals(TODAY.plusDays(10), r.lowestScenarioDate());
    }

    @Test
    void cancellingABillOnlyAffectsTheScenarioLine() {
        UUID bill = UUID.randomUUID();
        var bills = Map.of(TODAY.plusDays(5), List.<Map.Entry<UUID, BigDecimal>>of(Map.entry(bill, d("40"))));
        var r = SandboxEngine.simulate(d("100"), d("0"), bills, List.of(new Event("CANCEL_BILL", "Cancel", null, null, bill)), TODAY, 30);
        assertEquals(0, d("60").compareTo(r.endBaseline()));
        assertEquals(0, d("100").compareTo(r.endScenario()));
    }

    @Test
    void aMonthlyChangeRepeatsOnTheSameDayOfEachMonthAndClampsShortMonths() {
        var raise = new Event("MONTHLY_CHANGE", "Raise", LocalDate.of(2026, 1, 31), d("100"), null);
        var r = SandboxEngine.simulate(d("0"), d("0"), Map.of(), List.of(raise), TODAY, 90);
        // Jan 31, Feb 28 (clamped), Mar 31 are all inside Jan 1 + 90 days (Apr 1)
        assertEquals(0, d("300").compareTo(r.endScenario()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.endBaseline()));
    }

    @Test
    void pointsAreWeeklyPlusTheFinalDay() {
        var r = SandboxEngine.simulate(d("0"), d("1"), Map.of(), List.of(), TODAY, 30);
        assertEquals(TODAY, r.points().get(0).date());
        assertEquals(TODAY.plusDays(30), r.points().get(r.points().size() - 1).date());
        assertEquals(1 + 4 + 1, r.points().size());   // start + days 7,14,21,28 + final day 30
    }
}

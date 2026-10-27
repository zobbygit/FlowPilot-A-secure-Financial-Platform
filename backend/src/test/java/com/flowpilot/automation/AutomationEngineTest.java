package com.flowpilot.automation;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.automation.AutomationEngine.Action;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutomationEngineTest {
    private static Action act(int pos, String type, String amountType, String value) {
        return new Action(pos, type, UUID.randomUUID(), type + pos, amountType, new BigDecimal(value), true, null);
    }

    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void plansInPriorityOrderAndNeverExceedsAvailableFunds() {
        var out = AutomationEngine.compute(List.of(act(1, "GOAL", "FIXED", "100"), act(2, "BILL", "FIXED", "80"), act(3, "BUDGET", "FIXED", "50")), null, d("200"), null, Set.of());
        assertEquals("PLANNED", out.get(0).status());                 // 200 -> 100 left
        assertEquals("PLANNED", out.get(1).status());                 // 100 -> 20 left
        assertEquals("SKIPPED_INSUFFICIENT", out.get(2).status());    // 50 > 20: skipped explicitly
        assertEquals(0, d("180").compareTo(AutomationEngine.total(out)));
    }

    @Test
    void insufficientFundsAreReportedPerActionNotSilentlyReduced() {
        var out = AutomationEngine.compute(List.of(act(1, "GOAL", "FIXED", "150"), act(2, "BILL", "FIXED", "100")), null, d("200"), null, Set.of());
        assertEquals("PLANNED", out.get(0).status());
        assertEquals("SKIPPED_INSUFFICIENT", out.get(1).status());
        assertEquals("0", out.get(1).allocated());
        assertEquals("PARTIAL", AutomationEngine.overallStatus(out));
        assertEquals(0, d("150").compareTo(AutomationEngine.total(out)));
    }

    @Test
    void percentOfIncomeRequiresAnIncomeAmount() {
        var none = AutomationEngine.compute(List.of(act(1, "GOAL", "PERCENT", "10")), null, d("1000"), null, Set.of());
        assertEquals("SKIPPED_INVALID", none.get(0).status());
        var some = AutomationEngine.compute(List.of(act(1, "GOAL", "PERCENT", "10")), d("2500"), d("1000"), null, Set.of());
        assertEquals(0, d("250").compareTo(new BigDecimal(some.get(0).allocated())));
        assertEquals("SUCCESS", AutomationEngine.overallStatus(some));
    }

    @Test
    void perRunLimitCapsTheTotal() {
        var out = AutomationEngine.compute(List.of(act(1, "GOAL", "FIXED", "60"), act(2, "BILL", "FIXED", "60")), null, d("1000"), d("100"), Set.of());
        assertEquals("PLANNED", out.get(0).status());
        assertEquals("SKIPPED_LIMIT", out.get(1).status());
    }

    @Test
    void retryDoesNotPlanTheSameTargetTwiceAndAllDoneIsSuccess() {
        Action a = act(1, "GOAL", "FIXED", "50");
        var out = AutomationEngine.compute(List.of(a), null, d("500"), null, Set.of("GOAL:" + a.targetId()));
        assertEquals("SKIPPED_DONE", out.get(0).status());
        assertEquals("SUCCESS", AutomationEngine.overallStatus(out));
        assertEquals(0, BigDecimal.ZERO.compareTo(AutomationEngine.total(out)));
    }

    @Test
    void invalidTargetsAreSkippedWithTheirReason() {
        var bad = new Action(1, "BILL", UUID.randomUUID(), "Gone", "FIXED", d("10"), false, "That bill was cancelled.");
        var out = AutomationEngine.compute(List.of(bad), null, d("100"), null, Set.of());
        assertEquals("SKIPPED_INVALID", out.get(0).status());
        assertEquals("That bill was cancelled.", out.get(0).message());
        assertEquals("FAILED", AutomationEngine.overallStatus(out));
    }
}

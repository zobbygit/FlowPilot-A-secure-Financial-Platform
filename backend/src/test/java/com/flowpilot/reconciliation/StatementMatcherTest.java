package com.flowpilot.reconciliation;

import static org.junit.jupiter.api.Assertions.*;

import com.flowpilot.reconciliation.StatementMatcher.Recorded;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatementMatcherTest {
    private static final LocalDate D = LocalDate.of(2026, 3, 10);
    private static BigDecimal a(String s) { return new BigDecimal(s); }
    private static Recorded rec(String date, String amt, String text) { return new Recorded(UUID.randomUUID(), LocalDate.parse(date), a(amt), text); }

    @Test
    void amountMustMatchExactlyAndDateWithinTheWindow() {
        assertEquals(0, StatementMatcher.score(D, a("-9.99"), "x", null, rec("2026-03-10", "-10.00", "x")));
        assertEquals(0, StatementMatcher.score(D, a("-9.99"), "x", null, rec("2026-03-20", "-9.99", "x")));
        assertTrue(StatementMatcher.score(D, a("-9.99"), "x", null, rec("2026-03-10", "-9.99", "x")) >= 70);
    }

    @Test
    void closerDatesAndSimilarTextScoreHigher() {
        int sameDay = StatementMatcher.score(D, a("-20"), "SHELL FUEL", null, rec("2026-03-10", "-20", "Shell fuel stop"));
        int twoDays = StatementMatcher.score(D, a("-20"), "SHELL FUEL", null, rec("2026-03-12", "-20", "Shell fuel stop"));
        int noText = StatementMatcher.score(D, a("-20"), "SHELL FUEL", null, rec("2026-03-10", "-20", "Lunch"));
        assertTrue(sameDay > twoDays);
        assertTrue(sameDay > noText);
    }

    @Test
    void aTransactionAlreadyTakenIsNeverSuggestedAgain() {
        Recorded only = rec("2026-03-10", "-5", "Coffee");
        assertTrue(StatementMatcher.best(D, a("-5"), "Coffee", null, List.of(only), Set.of()).isPresent());
        assertTrue(StatementMatcher.best(D, a("-5"), "Coffee", null, List.of(only), Set.of(only.id())).isEmpty());
    }

    @Test
    void picksTheBestOfSeveralCandidates() {
        Recorded far = rec("2026-03-13", "-5", "Coffee"), near = rec("2026-03-10", "-5", "Coffee");
        assertEquals(near.id(), StatementMatcher.best(D, a("-5"), "Coffee", null, List.of(far, near), Set.of()).orElseThrow().transactionId());
    }

    @Test
    void guessesTheMappingFromCommonHeaders() {
        var g = ReconciliationService.guess(List.of("Date", "Description", "Debit", "Credit", "Reference"), List.of(List.of("Date"), List.of("2026-01-02")), true);
        assertEquals(0, g.dateCol()); assertEquals(1, g.descCol()); assertEquals(2, g.debitCol()); assertEquals(3, g.creditCol()); assertEquals(4, g.refCol()); assertNull(g.amountCol());
        assertEquals("YMD_DASH", g.dateFormat());
    }
}

package com.flowpilot.sandbox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Pure projection maths (no database, no clock). Everything it returns is a PROJECTION built from stated assumptions. */
public final class SandboxEngine {
    private SandboxEngine() {}

    /** amount is signed: negative = money out, positive = money in. */
    public record Event(String kind, String label, LocalDate date, BigDecimal amount, UUID billId) {}
    public record Point(LocalDate date, BigDecimal baseline, BigDecimal scenario) {}
    public record Result(List<Point> points, BigDecimal lowestBaseline, LocalDate lowestBaselineDate, BigDecimal lowestScenario, LocalDate lowestScenarioDate,
                         LocalDate firstNegativeScenario, BigDecimal endBaseline, BigDecimal endScenario) {}

    /**
     * @param bills scheduled bill occurrences: date -> list of (billId, amount) as positive numbers
     */
    public static Result simulate(BigDecimal start, BigDecimal dailyNet, Map<LocalDate, List<Map.Entry<UUID, BigDecimal>>> bills, List<Event> events, LocalDate today, int horizon) {
        Set<UUID> cancelled = new HashSet<>();
        for (Event e : events) if (e.kind().equals("CANCEL_BILL") && e.billId() != null) cancelled.add(e.billId());

        BigDecimal base = start, scen = start;
        BigDecimal lowB = start, lowS = start; LocalDate lowBD = today, lowSD = today, firstNeg = start.signum() < 0 ? today : null;
        List<Point> points = new ArrayList<>(List.of(new Point(today, start, start)));
        for (int i = 1; i <= horizon; i++) {
            LocalDate d = today.plusDays(i);
            BigDecimal billsBase = BigDecimal.ZERO, billsScen = BigDecimal.ZERO;
            for (Map.Entry<UUID, BigDecimal> b : bills.getOrDefault(d, List.of())) {
                billsBase = billsBase.add(b.getValue());
                if (!cancelled.contains(b.getKey())) billsScen = billsScen.add(b.getValue());
            }
            BigDecimal delta = BigDecimal.ZERO;
            for (Event e : events) {
                if (e.kind().equals("ONE_OFF") && e.date().equals(d)) delta = delta.add(e.amount());
                // a monthly change applies on the same day-of-month as its start date (clamped to short months), starting on that date
                if (e.kind().equals("MONTHLY_CHANGE") && !d.isBefore(e.date()) && d.getDayOfMonth() == Math.min(e.date().getDayOfMonth(), d.lengthOfMonth())) delta = delta.add(e.amount());
            }
            base = base.add(dailyNet).subtract(billsBase);
            scen = scen.add(dailyNet).subtract(billsScen).add(delta);
            if (base.compareTo(lowB) < 0) { lowB = base; lowBD = d; }
            if (scen.compareTo(lowS) < 0) { lowS = scen; lowSD = d; }
            if (firstNeg == null && scen.signum() < 0) firstNeg = d;
            if (i % 7 == 0 || i == horizon) points.add(new Point(d, round(base), round(scen)));
        }
        return new Result(points, round(lowB), lowBD, round(lowS), lowSD, firstNeg, round(base), round(scen));
    }

    private static BigDecimal round(BigDecimal v) { return v.setScale(2, RoundingMode.HALF_UP); }
}

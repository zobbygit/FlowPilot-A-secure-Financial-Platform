package com.flowpilot.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure scoring of "does this statement line correspond to this recorded transaction?" The amount must match exactly. */
public final class StatementMatcher {
    private StatementMatcher() {}

    public record Recorded(UUID id, LocalDate date, BigDecimal amount, String text) {}
    public record Match(UUID transactionId, int score, String why) {}

    public static final int SUGGEST_THRESHOLD = 60;
    public static final int WINDOW_DAYS = 3;

    public static int score(LocalDate stmtDate, BigDecimal stmtAmount, String stmtText, String stmtRef, Recorded r) {
        if (stmtAmount.compareTo(r.amount()) != 0) return 0;
        long days = Math.abs(ChronoUnit.DAYS.between(stmtDate, r.date()));
        if (days > WINDOW_DAYS) return 0;
        int s = 40 + (days == 0 ? 30 : days == 1 ? 20 : days == 2 ? 10 : 5);
        s += (int) Math.round(25 * similarity(stmtText, r.text()));
        if (stmtRef != null && !stmtRef.isBlank() && r.text() != null && r.text().toLowerCase(Locale.ROOT).contains(stmtRef.toLowerCase(Locale.ROOT))) s += 15;
        return Math.min(100, s);
    }

    /** Best candidate among those not already taken, or empty. Ties keep the earliest-dated, so results are deterministic. */
    public static Optional<Match> best(LocalDate date, BigDecimal amount, String text, String ref, List<Recorded> candidates, Set<UUID> taken) {
        Match best = null;
        for (Recorded r : candidates) {
            if (taken.contains(r.id())) continue;
            int s = score(date, amount, text, ref, r);
            if (s >= SUGGEST_THRESHOLD && (best == null || s > best.score())) best = new Match(r.id(), s, describe(date, r));
        }
        return Optional.ofNullable(best);
    }

    static String describe(LocalDate d, Recorded r) {
        long days = Math.abs(ChronoUnit.DAYS.between(d, r.date()));
        return "Same amount" + (days == 0 ? ", same day" : ", " + days + " day" + (days == 1 ? "" : "s") + " apart");
    }

    /** Jaccard overlap of alphanumeric word sets (0..1). */
    static double similarity(String a, String b) {
        Set<String> x = words(a), y = words(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(x); inter.retainAll(y);
        Set<String> union = new HashSet<>(x); union.addAll(y);
        return (double) inter.size() / union.size();
    }

    private static Set<String> words(String s) {
        if (s == null) return Set.of();
        Set<String> out = new HashSet<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) if (w.length() > 2) out.add(w);
        return out;
    }
}

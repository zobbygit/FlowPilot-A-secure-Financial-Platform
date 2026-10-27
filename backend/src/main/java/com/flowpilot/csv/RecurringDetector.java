package com.flowpilot.csv;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Suggests possible recurring charges. It NEVER decides: every result is a candidate with a confidence and the reasons
 * behind it, shown to the user for review. A repeated purchase is not assumed to be a subscription.
 */
public final class RecurringDetector {
    private RecurringDetector() {}

    public record Entry(LocalDate date, BigDecimal amount, String description) {}
    public record Candidate(String merchant, String frequency, String typicalAmount, int occurrences, LocalDate lastDate, LocalDate nextExpected, int confidence, List<String> reasons, String priceChangeNote) {}

    public static List<Candidate> detect(List<Entry> entries) {
        Map<String, List<Entry>> byMerchant = new TreeMap<>();
        for (Entry e : entries) {
            if (e.amount().signum() >= 0) continue;                       // only money out can be a recurring charge
            String key = normalize(e.description());
            if (!key.isEmpty()) byMerchant.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<Candidate> out = new ArrayList<>();
        for (var g : byMerchant.entrySet()) {
            List<Entry> list = g.getValue(); list.sort(Comparator.comparing(Entry::date));
            if (list.size() < 3) continue;
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < list.size(); i++) gaps.add(ChronoUnit.DAYS.between(list.get(i - 1).date(), list.get(i).date()));
            long median = median(gaps);
            String freq = frequency(median);
            if (freq == null) continue;
            long tolerance = freq.equals("WEEKLY") ? 1 : freq.equals("BIWEEKLY") ? 2 : freq.equals("MONTHLY") ? 3 : freq.equals("QUARTERLY") ? 6 : 10;
            long regular = gaps.stream().filter(x -> Math.abs(x - target(freq)) <= tolerance).count();
            double regularity = (double) regular / gaps.size();
            if (regularity < 0.6) continue;
            List<BigDecimal> amounts = list.stream().map(e -> e.amount().abs()).sorted().toList();
            BigDecimal med = amounts.get(amounts.size() / 2);
            long sameAmount = amounts.stream().filter(a -> med.signum() > 0 && a.subtract(med).abs().divide(med, 4, RoundingMode.HALF_UP).doubleValue() <= 0.02).count();
            double amountConsistency = (double) sameAmount / amounts.size();

            int score = (int) Math.round(50 * regularity + 35 * amountConsistency + Math.min(15, (list.size() - 2) * 5));
            List<String> reasons = new ArrayList<>();
            reasons.add(list.size() + " charges, " + Math.round(regularity * 100) + "% of the gaps match a " + freq.toLowerCase() + " rhythm (typically " + median + " days).");
            reasons.add(Math.round(amountConsistency * 100) + "% of the amounts are within 2% of " + med.setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");
            if (list.size() < 4) reasons.add("Only a few occurrences, so treat this as tentative.");
            Entry last = list.get(list.size() - 1);
            String note = last.amount().abs().subtract(med).abs().divide(med.signum() == 0 ? BigDecimal.ONE : med, 4, RoundingMode.HALF_UP).doubleValue() > 0.01
                    ? "The latest charge (" + last.amount().abs().setScale(2, RoundingMode.HALF_UP).toPlainString() + ") differs from the usual " + med.setScale(2, RoundingMode.HALF_UP).toPlainString() + " — a possible price change." : null;
            out.add(new Candidate(display(list), freq, med.setScale(2, RoundingMode.HALF_UP).toPlainString(), list.size(), last.date(), last.date().plusDays(target(freq)), Math.min(99, score), reasons, note));
        }
        out.sort(Comparator.comparingInt(Candidate::confidence).reversed());
        return out;
    }

    /** Lower-case, drop digits/reference tokens/punctuation and common payment-processor noise so "NETFLIX.COM 4412" == "Netflix". */
    public static String normalize(String s) {
        if (s == null) return "";
        String t = s.toLowerCase(Locale.ROOT).replaceAll("\\b(pos|card|purchase|payment|debit|visa|mastercard|recurring|www|com|inc|llc|ltd|pvt|co)\\b", " ")
                .replaceAll("[0-9#*_./\\\\-]+", " ").replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
        String[] words = t.split(" ");
        return String.join(" ", Arrays.copyOf(words, Math.min(words.length, 3))).trim();
    }

    static String frequency(long median) {
        if (median >= 6 && median <= 8) return "WEEKLY";
        if (median >= 12 && median <= 16) return "BIWEEKLY";
        if (median >= 27 && median <= 34) return "MONTHLY";
        if (median >= 84 && median <= 98) return "QUARTERLY";
        if (median >= 350 && median <= 380) return "YEARLY";
        return null;
    }

    static long target(String freq) { return switch (freq) { case "WEEKLY" -> 7; case "BIWEEKLY" -> 14; case "MONTHLY" -> 30; case "QUARTERLY" -> 91; default -> 365; }; }

    private static long median(List<Long> v) { List<Long> s = new ArrayList<>(v); Collections.sort(s); return s.get(s.size() / 2); }

    private static String display(List<Entry> list) {
        String d = list.get(list.size() - 1).description();
        return d == null || d.isBlank() ? "Unnamed charge" : d.length() > 60 ? d.substring(0, 60) : d;
    }
}

package com.flowpilot.households;

import com.flowpilot.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Pure, exact (4-decimal) splitting. Shares always sum to the total; leftovers are assigned deterministically. */
public final class SplitCalculator {
    private SplitCalculator() {}

    private static final BigDecimal UNIT = new BigDecimal("0.0001");

    public static Map<UUID, BigDecimal> equal(BigDecimal total, List<UUID> members) {
        if (members.isEmpty()) throw ApiException.badRequest("NO_MEMBERS", "Choose at least one person to split with.");
        List<UUID> sorted = new ArrayList<>(new TreeSet<>(members));
        BigDecimal base = total.divide(BigDecimal.valueOf(sorted.size()), 4, RoundingMode.DOWN);
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        sorted.forEach(u -> out.put(u, base));
        return distributeRemainder(total, out, sorted);
    }

    public static Map<UUID, BigDecimal> percent(BigDecimal total, Map<UUID, BigDecimal> percents) {
        BigDecimal sum = percents.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(BigDecimal.valueOf(100)) != 0) throw ApiException.badRequest("PERCENT_TOTAL", "Percentages must add up to exactly 100.");
        if (percents.values().stream().anyMatch(p -> p.signum() < 0)) throw ApiException.badRequest("INVALID_PERCENT", "Percentages can't be negative.");
        List<UUID> sorted = new ArrayList<>(new TreeSet<>(percents.keySet()));
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        for (UUID u : sorted) out.put(u, total.multiply(percents.get(u)).divide(BigDecimal.valueOf(100), 4, RoundingMode.DOWN));
        return distributeRemainder(total, out, sorted);
    }

    public static Map<UUID, BigDecimal> exact(BigDecimal total, Map<UUID, BigDecimal> amounts) {
        if (amounts.values().stream().anyMatch(a -> a.signum() < 0)) throw ApiException.badRequest("INVALID_SHARE", "Shares can't be negative.");
        BigDecimal sum = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) throw ApiException.badRequest("SHARES_MISMATCH", "The shares add up to " + sum.toPlainString() + " but the expense is " + total.toPlainString() + ".");
        return new LinkedHashMap<>(new TreeMap<>(amounts));
    }

    private static Map<UUID, BigDecimal> distributeRemainder(BigDecimal total, Map<UUID, BigDecimal> shares, List<UUID> order) {
        BigDecimal assigned = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long units = total.subtract(assigned).divide(UNIT, 0, RoundingMode.HALF_UP).longValueExact();
        for (int i = 0; units > 0; i = (i + 1) % order.size(), units--) shares.merge(order.get(i), UNIT, BigDecimal::add);
        return shares;
    }

    public record Transfer(UUID from, UUID to, BigDecimal amount) {}

    /** Greedy minimal-ish settle-up: biggest debtor pays biggest creditor. net > 0 means the person is owed money. */
    public static List<Transfer> settleUp(Map<UUID, BigDecimal> net) {
        List<Map.Entry<UUID, BigDecimal>> debtors = new ArrayList<>(), creditors = new ArrayList<>();
        net.forEach((u, v) -> { if (v.signum() < 0) debtors.add(new AbstractMap.SimpleEntry<>(u, v.negate())); else if (v.signum() > 0) creditors.add(new AbstractMap.SimpleEntry<>(u, v)); });
        Comparator<Map.Entry<UUID, BigDecimal>> byAmount = Comparator.<Map.Entry<UUID, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue).reversed().thenComparing(Map.Entry::getKey);
        debtors.sort(byAmount); creditors.sort(byAmount);
        List<Transfer> out = new ArrayList<>();
        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            BigDecimal pay = debtors.get(i).getValue().min(creditors.get(j).getValue());
            if (pay.signum() > 0) out.add(new Transfer(debtors.get(i).getKey(), creditors.get(j).getKey(), pay));
            debtors.get(i).setValue(debtors.get(i).getValue().subtract(pay)); creditors.get(j).setValue(creditors.get(j).getValue().subtract(pay));
            if (debtors.get(i).getValue().signum() == 0) i++;
            if (creditors.get(j).getValue().signum() == 0) j++;
        }
        return out;
    }
}

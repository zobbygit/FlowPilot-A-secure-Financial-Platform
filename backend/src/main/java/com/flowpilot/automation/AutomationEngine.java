package com.flowpilot.automation;

import com.flowpilot.automation.AutomationDtos.Outcome;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Pure decision logic (no database, no clock): given the rule's actions and the money that is genuinely unallocated,
 * decide what to plan. Insufficient funds are reported explicitly per action - never silently overspent or truncated.
 */
public final class AutomationEngine {
    private AutomationEngine() {}

    public record Action(int position, String targetType, UUID targetId, String targetName, String amountType, BigDecimal value, boolean targetValid, String invalidReason) {}

    public static List<Outcome> compute(List<Action> actions, BigDecimal triggerAmount, BigDecimal available, BigDecimal perRunLimit, Set<String> alreadyDone) {
        List<Outcome> out = new ArrayList<>();
        BigDecimal remainingFunds = available, remainingLimit = perRunLimit;
        for (Action a : actions) {
            if (alreadyDone.contains(a.targetType() + ":" + a.targetId())) { out.add(o(a, null, BigDecimal.ZERO, "SKIPPED_DONE", "Already planned in an earlier attempt.")); continue; }
            if (!a.targetValid()) { out.add(o(a, null, BigDecimal.ZERO, "SKIPPED_INVALID", a.invalidReason())); continue; }
            BigDecimal requested;
            if (a.amountType().equals("PERCENT")) {
                if (triggerAmount == null) { out.add(o(a, null, BigDecimal.ZERO, "SKIPPED_INVALID", "A percentage needs an income amount to apply to.")); continue; }
                requested = triggerAmount.multiply(a.value()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            } else requested = a.value();
            if (requested.signum() <= 0) { out.add(o(a, requested, BigDecimal.ZERO, "SKIPPED_INVALID", "The calculated amount is zero.")); continue; }
            if (remainingLimit != null && requested.compareTo(remainingLimit) > 0) { out.add(o(a, requested, BigDecimal.ZERO, "SKIPPED_LIMIT", "Would exceed this rule's per-run limit.")); continue; }
            if (requested.compareTo(remainingFunds) > 0) { out.add(o(a, requested, BigDecimal.ZERO, "SKIPPED_INSUFFICIENT", "Not enough unallocated money in the source account.")); continue; }
            remainingFunds = remainingFunds.subtract(requested);
            if (remainingLimit != null) remainingLimit = remainingLimit.subtract(requested);
            out.add(o(a, requested, requested, "PLANNED", null));
        }
        return out;
    }

    public static String overallStatus(List<Outcome> outcomes) {
        long planned = outcomes.stream().filter(x -> x.status().equals("PLANNED")).count();
        long problems = outcomes.stream().filter(x -> x.status().startsWith("SKIPPED") && !x.status().equals("SKIPPED_DONE")).count();
        if (problems == 0) return "SUCCESS";
        return planned > 0 ? "PARTIAL" : "FAILED";
    }

    public static BigDecimal total(List<Outcome> outcomes) {
        return outcomes.stream().map(x -> new BigDecimal(x.allocated())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Outcome o(Action a, BigDecimal requested, BigDecimal allocated, String status, String msg) {
        return new Outcome(a.position(), a.targetType(), a.targetId(), a.targetName(), (requested == null ? BigDecimal.ZERO : requested).toPlainString(), allocated.toPlainString(), status, msg);
    }
}

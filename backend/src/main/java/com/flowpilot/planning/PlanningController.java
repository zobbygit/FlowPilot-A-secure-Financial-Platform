package com.flowpilot.planning;

import com.flowpilot.finance.FinanceDtos.TransactionView;
import com.flowpilot.planning.PlanningDtos.*;
import com.flowpilot.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('USER')")
public class PlanningController {
    private final BudgetService budgets;
    private final BillService bills;
    private final GoalService goals;

    public PlanningController(BudgetService budgets, BillService bills, GoalService goals) {
        this.budgets = budgets; this.bills = bills; this.goals = goals;
    }

    @GetMapping("/budgets") public List<BudgetView> budgets(@AuthenticationPrincipal AuthPrincipal p) { return budgets.list(p.userId()); }
    @PostMapping("/budgets") @ResponseStatus(HttpStatus.CREATED)
    public BudgetView createBudget(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody BudgetRequest r) { return budgets.create(p.userId(), r); }
    @DeleteMapping("/budgets/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveBudget(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { budgets.archive(p.userId(), id); }

    @GetMapping("/bills") public BillsOverview bills(@AuthenticationPrincipal AuthPrincipal p) { return bills.overview(p.userId()); }
    @GetMapping("/bills/upcoming") public List<BillView> upcoming(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(defaultValue = "4") int limit) { return bills.upcoming(p.userId(), limit); }
    @PostMapping("/bills") @ResponseStatus(HttpStatus.CREATED)
    public BillView createBill(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody BillRequest r) { return bills.create(p.userId(), r); }
    @PutMapping("/bills/{id}")
    public BillView updateBill(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody BillRequest r) { return bills.update(p.userId(), id, r); }
    @PatchMapping("/bills/{id}/status")
    public BillView billStatus(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody BillStatusRequest r) { return bills.setStatus(p.userId(), id, r.status()); }
    @PostMapping("/bills/{id}/pay") @ResponseStatus(HttpStatus.CREATED)
    public TransactionView payBill(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody PayBillRequest r) { return bills.pay(p.userId(), id, r.accountId()); }
    @DeleteMapping("/bills/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveBill(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { bills.archive(p.userId(), id); }

    @GetMapping("/goals") public List<GoalView> goals(@AuthenticationPrincipal AuthPrincipal p) { return goals.list(p.userId()); }
    @PostMapping("/goals") @ResponseStatus(HttpStatus.CREATED)
    public GoalView createGoal(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody GoalRequest r) { return goals.create(p.userId(), r); }
    @PostMapping("/goals/{id}/allocations")
    public GoalView allocate(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody AllocationRequest r) { return goals.allocate(p.userId(), id, r.amount()); }
    @PostMapping("/goals/{id}/transfers")
    public GoalView transfer(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody GoalTransferRequest r) { return goals.transfer(p.userId(), id, r); }
    @DeleteMapping("/goals/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveGoal(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { goals.archive(p.userId(), id); }
}

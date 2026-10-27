package com.flowpilot.households;

import com.flowpilot.households.HouseholdDtos.*;
import com.flowpilot.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/households")
@PreAuthorize("hasRole('USER')")
public class HouseholdController {
    private final HouseholdService service;

    public HouseholdController(HouseholdService service) { this.service = service; }

    @GetMapping public List<HouseholdSummary> mine(@AuthenticationPrincipal AuthPrincipal p) { return service.mine(p.userId()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public HouseholdSummary create(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody CreateHousehold r) { return service.create(p.userId(), r); }
    @GetMapping("/invitations") public List<Invitation> incoming(@AuthenticationPrincipal AuthPrincipal p) { return service.incoming(p.userId()); }
    @PostMapping("/invitations/{id}/accept") @ResponseStatus(HttpStatus.NO_CONTENT) public void accept(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.respond(p.userId(), id, true); }
    @PostMapping("/invitations/{id}/decline") @ResponseStatus(HttpStatus.NO_CONTENT) public void decline(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.respond(p.userId(), id, false); }
    @GetMapping("/{id}") public Detail detail(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.detail(p.userId(), id); }
    @PostMapping("/{id}/archive") @ResponseStatus(HttpStatus.NO_CONTENT) public void archive(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.archive(p.userId(), id); }
    @PostMapping("/{id}/invitations") @ResponseStatus(HttpStatus.CREATED) public void invite(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody InviteRequest r) { service.invite(p.userId(), id, r.handle()); }
    @DeleteMapping("/{id}/invitations/{inv}") @ResponseStatus(HttpStatus.NO_CONTENT) public void cancel(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @PathVariable UUID inv) { service.cancelInvitation(p.userId(), id, inv); }
    @DeleteMapping("/{id}/members/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void remove(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @PathVariable UUID userId) { service.removeMember(p.userId(), id, userId); }
    @GetMapping("/{id}/expenses") public PageOf<ExpenseView> expenses(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) { return service.expenses(p.userId(), id, page, size); }
    @PostMapping("/{id}/expenses") @ResponseStatus(HttpStatus.CREATED) public ExpenseView add(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody ExpenseRequest r) { return service.addExpense(p.userId(), id, r); }
    @DeleteMapping("/{id}/expenses/{eid}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteExpense(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @PathVariable UUID eid) { service.deleteExpense(p.userId(), id, eid); }
    @PostMapping("/{id}/settlements") @ResponseStatus(HttpStatus.CREATED) public void settle(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody SettlementRequest r) { service.settle(p.userId(), id, r); }
    @PostMapping("/{id}/budgets") @ResponseStatus(HttpStatus.CREATED) public void budget(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody BudgetRequest r) { service.addBudget(p.userId(), id, r); }
    @DeleteMapping("/{id}/budgets/{bid}") @ResponseStatus(HttpStatus.NO_CONTENT) public void archiveBudget(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @PathVariable UUID bid) { service.archiveBudget(p.userId(), id, bid); }
}

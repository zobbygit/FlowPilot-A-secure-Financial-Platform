package com.flowpilot.sandbox;

import com.flowpilot.sandbox.SandboxService.*;
import com.flowpilot.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sandbox")
@PreAuthorize("hasRole('USER')")
public class SandboxController {
    private final SandboxService service;

    public SandboxController(SandboxService service) { this.service = service; }

    @PostMapping("/simulate") public Simulation simulate(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody SimulateRequest r) { return service.simulate(p.userId(), r); }
    @GetMapping("/scenarios") public List<ScenarioView> scenarios(@AuthenticationPrincipal AuthPrincipal p) { return service.scenarios(p.userId()); }
    @PostMapping("/scenarios") @ResponseStatus(HttpStatus.CREATED) public ScenarioView save(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody ScenarioRequest r) { return service.save(p.userId(), r); }
    @DeleteMapping("/scenarios/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.delete(p.userId(), id); }
}

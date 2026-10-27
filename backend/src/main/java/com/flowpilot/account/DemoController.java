package com.flowpilot.account;

import com.flowpilot.security.AuthPrincipal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me/demo-data")
@PreAuthorize("hasRole('USER')")
public class DemoController {
    private final DemoDataService demo;

    public DemoController(DemoDataService demo) { this.demo = demo; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> load(@AuthenticationPrincipal AuthPrincipal p) { return Map.of("transactions", demo.seed(p.userId())); }
}

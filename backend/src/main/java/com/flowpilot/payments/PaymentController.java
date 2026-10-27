package com.flowpilot.payments;

import com.flowpilot.config.AppProperties;
import com.flowpilot.payments.PaymentDtos.*;
import com.flowpilot.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasRole('USER')")
public class PaymentController {
    private final PaymentService service;
    private final AppProperties props;
    private final JdbcTemplate jdbc;

    public PaymentController(PaymentService service, AppProperties props, JdbcTemplate jdbc) { this.service = service; this.props = props; this.jdbc = jdbc; }

    @GetMapping("/profile") public PaymentProfile profile(@AuthenticationPrincipal AuthPrincipal p) { return service.profile(p.userId()); }
    @PutMapping("/profile/receive-account") public PaymentProfile receive(@AuthenticationPrincipal AuthPrincipal p, @RequestBody ReceiveAccountRequest r) { return service.setReceiveAccount(p.userId(), r.accountId()); }
    @GetMapping("/lookup/{handle}") public Lookup lookup(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String handle) { return service.lookup(p.userId(), handle.toLowerCase()); }

    @GetMapping public PageOf<PaymentView> list(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.list(p.userId(), status, page, size); }
    @GetMapping("/{id}") public PaymentDetail get(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.get(p.userId(), id); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public PaymentView initiate(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody InitiateRequest r, @RequestHeader(value = "Idempotency-Key", required = false) String key) { return service.initiate(p.userId(), r, key); }
    @PostMapping("/{id}/confirm") public PaymentView confirm(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.confirm(p.userId(), id); }
    @PostMapping("/{id}/cancel") public PaymentView cancel(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.cancel(p.userId(), id); }
    @PostMapping("/{id}/refund") public PaymentView refund(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody ReasonRequest r) { return service.refund(p.userId(), id, r.reason()); }
    @PostMapping("/{id}/dispute") @ResponseStatus(HttpStatus.NO_CONTENT) public void dispute(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody ReasonRequest r) { service.openDispute(p.userId(), id, r.reason()); }
    @DeleteMapping("/{id}/dispute") @ResponseStatus(HttpStatus.NO_CONTENT) public void withdraw(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.withdrawDispute(p.userId(), id); }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) {
        PaymentDetail d = service.get(p.userId(), id);
        if (!d.payment().status().equals("SUCCESSFUL") && !d.payment().status().equals("REFUNDED")) throw new com.flowpilot.common.ApiException(HttpStatus.CONFLICT, "NO_RECEIPT", "A receipt exists only for completed payments.");
        String you = jdbc.queryForObject("select full_name from user_profiles where user_id = ?", String.class, p.userId());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowpilot-simulated-" + d.payment().reference() + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(PaymentReceiptPdf.render(d.payment(), you, d.events()));
    }

    // ---- requests
    @PostMapping("/requests") @ResponseStatus(HttpStatus.CREATED) public RequestView createRequest(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody RequestCreate r) { return service.createRequest(p.userId(), r); }
    @GetMapping("/requests") public List<RequestView> requests(@AuthenticationPrincipal AuthPrincipal p) { return service.requests(p.userId()); }
    @GetMapping("/requests/by-reference/{ref}") public RequestView byRef(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ref) { return service.requestByReference(p.userId(), ref.toUpperCase()); }
    @DeleteMapping("/requests/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void cancelRequest(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.cancelRequest(p.userId(), id); }
    @GetMapping(value = "/requests/{ref}/qr", produces = "image/svg+xml")
    public ResponseEntity<String> qr(@AuthenticationPrincipal AuthPrincipal p, @PathVariable String ref) {
        return ResponseEntity.ok().header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'").body(service.qr(p.userId(), ref.toUpperCase(), props.publicBaseUrl()));
    }
}

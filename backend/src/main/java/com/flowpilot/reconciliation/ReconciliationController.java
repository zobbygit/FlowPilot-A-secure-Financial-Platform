package com.flowpilot.reconciliation;

import com.flowpilot.csv.CsvMapping;
import com.flowpilot.csv.RecurringDetector;
import com.flowpilot.reconciliation.ReconciliationDtos.*;
import com.flowpilot.security.AuthPrincipal;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('USER')")
public class ReconciliationController {
    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) { this.service = service; }

    @PostMapping(value = "/csv/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Preview preview(@RequestPart("file") MultipartFile file) { return service.preview(bytes(file)); }

    @PostMapping(value = "/reconciliation/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public ImportSummary create(@AuthenticationPrincipal AuthPrincipal p, @RequestPart("file") MultipartFile file, @RequestParam UUID accountId, @RequestParam boolean hasHeader,
                                @RequestParam int dateCol, @RequestParam int descCol, @RequestParam(required = false) Integer amountCol, @RequestParam(required = false) Integer debitCol,
                                @RequestParam(required = false) Integer creditCol, @RequestParam(required = false) Integer refCol, @RequestParam String dateFormat, @RequestParam(defaultValue = ".") String decimal) {
        return service.importStatement(p.userId(), accountId, bytes(file), file.getOriginalFilename(), new CsvMapping(hasHeader, dateCol, descCol, amountCol, debitCol, creditCol, refCol, dateFormat, decimal));
    }

    @GetMapping("/reconciliation/imports") public List<ImportSummary> imports(@AuthenticationPrincipal AuthPrincipal p) { return service.imports(p.userId()); }
    @GetMapping("/reconciliation/imports/{id}") public RowPage rows(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestParam(required = false) String status, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) { return service.rows(p.userId(), id, status, page, size); }
    @DeleteMapping("/reconciliation/imports/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void discard(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.discard(p.userId(), id); }
    @PostMapping("/reconciliation/rows/{id}/confirm") public RowView confirm(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestBody(required = false) ConfirmRequest r) { return service.confirm(p.userId(), id, r == null ? null : r.transactionId()); }
    @PostMapping("/reconciliation/rows/{id}/reject") public RowView reject(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.reject(p.userId(), id); }
    @PostMapping("/reconciliation/rows/{id}/undo") public RowView undo(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.undo(p.userId(), id); }
    @PostMapping("/reconciliation/rows/{id}/record") @ResponseStatus(HttpStatus.CREATED) public RowView record(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.recordMissing(p.userId(), id); }

    @PostMapping(value = "/recurring/detect", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<RecurringDetector.Candidate> detect(@RequestPart("file") MultipartFile file, @RequestParam boolean hasHeader, @RequestParam int dateCol, @RequestParam int descCol, @RequestParam(required = false) Integer amountCol,
                                                    @RequestParam(required = false) Integer debitCol, @RequestParam(required = false) Integer creditCol, @RequestParam String dateFormat, @RequestParam(defaultValue = ".") String decimal) {
        return service.detectRecurring(bytes(file), new CsvMapping(hasHeader, dateCol, descCol, amountCol, debitCol, creditCol, null, dateFormat, decimal));
    }

    private static byte[] bytes(MultipartFile f) {
        try { return f.getBytes(); } catch (IOException e) { throw com.flowpilot.common.ApiException.badRequest("FILE_UNREADABLE", "The file could not be read."); }
    }
}

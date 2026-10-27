package com.flowpilot.templates;

import com.flowpilot.security.AuthPrincipal;
import com.flowpilot.templates.TemplateDtos.*;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/templates")
@PreAuthorize("hasRole('USER')")
public class TemplateController {
    private final TemplateService service;

    public TemplateController(TemplateService service) { this.service = service; }

    @GetMapping public List<TemplateView> list(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(defaultValue = "false") boolean archived) { return service.list(p.userId(), archived); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public TemplateView create(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody TemplateRequest r) { return service.create(p.userId(), r); }
    @PutMapping("/{id}") public TemplateView update(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody TemplateRequest r) { return service.update(p.userId(), id, r); }
    @PostMapping("/{id}/duplicate") @ResponseStatus(HttpStatus.CREATED) public TemplateView duplicate(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.duplicate(p.userId(), id); }
    @PostMapping("/{id}/archive") @ResponseStatus(HttpStatus.NO_CONTENT) public void archive(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.setArchived(p.userId(), id, true); }
    @PostMapping("/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT) public void restore(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.setArchived(p.userId(), id, false); }

    /** multipart/form-data: one form field per template field key, plus an optional "file" part. */
    @PostMapping(value = "/{id}/records", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public CreatedRecord createRecord(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestParam Map<String, String> values,
                                      @RequestPart(value = "file", required = false) MultipartFile file) {
        java.util.Map<String, String> clean = new java.util.LinkedHashMap<>(values);
        clean.remove("_csrf");   // CSRF token sent as a form field by the SPA for uploads
        return service.createRecord(p.userId(), id, clean, file);
    }
}
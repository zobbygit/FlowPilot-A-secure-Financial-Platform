package com.flowpilot.documents;

import com.flowpilot.documents.DocumentDtos.*;
import com.flowpilot.security.AuthPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@PreAuthorize("hasRole('USER')")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @GetMapping("/templates") public List<TemplateView> list(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(defaultValue = "false") boolean archived) { return service.list(p.userId(), archived); }
    @PostMapping("/templates") @ResponseStatus(HttpStatus.CREATED) public TemplateView create(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody TemplateRequest r) { return service.create(p.userId(), r); }
    @PutMapping("/templates/{id}") public TemplateView update(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody TemplateRequest r) { return service.update(p.userId(), id, r); }
    @PostMapping("/templates/{id}/duplicate") @ResponseStatus(HttpStatus.CREATED) public TemplateView duplicate(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.duplicate(p.userId(), id); }
    @PostMapping("/templates/{id}/archive") @ResponseStatus(HttpStatus.NO_CONTENT) public void archive(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.setArchived(p.userId(), id, true); }
    @PostMapping("/templates/{id}/restore") @ResponseStatus(HttpStatus.NO_CONTENT) public void restore(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { service.setArchived(p.userId(), id, false); }
    @PostMapping(value = "/templates/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) public TemplateView logo(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestPart("file") MultipartFile file) { return service.setLogo(p.userId(), id, file); }
    @DeleteMapping("/templates/{id}/logo") public TemplateView removeLogo(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { return service.removeLogo(p.userId(), id); }
    @PostMapping("/templates/{id}/preview") public Preview preview(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody GenerateRequest r) { return service.preview(p.userId(), id, r); }

    @PostMapping("/templates/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody GenerateRequest r) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flowpilot-document.pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").header("X-Content-Type-Options", "nosniff").body(service.pdf(p.userId(), id, r));
    }
}

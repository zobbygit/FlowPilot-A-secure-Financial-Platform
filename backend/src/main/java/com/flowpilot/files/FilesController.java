package com.flowpilot.files;

import com.flowpilot.files.FileStorageService.Content;
import com.flowpilot.files.MemoService.*;
import com.flowpilot.files.ReceiptService.*;
import com.flowpilot.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
@RequestMapping("/api")
public class FilesController {
    public record ReviewRequest(@NotBlank @Pattern(regexp = "UNREVIEWED|VERIFIED|NEEDS_REVIEW") String status, @Size(max = 300) String note) {}

    private final ReceiptService receipts;
    private final MemoService memos;
    private final FileStorageService files;
    private final FileValidator validator;

    public FilesController(ReceiptService receipts, MemoService memos, FileStorageService files, FileValidator validator) {
        this.receipts = receipts; this.memos = memos; this.files = files; this.validator = validator;
    }

    // ---- receipts (USER)
    @PostMapping(value = "/receipts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('USER')")
    public ReceiptView upload(@AuthenticationPrincipal AuthPrincipal p, @RequestPart("file") MultipartFile file,
                              @RequestParam(required = false) String receiptNumber, @RequestParam(required = false) String merchant,
                              @RequestParam(required = false) BigDecimal subtotal, @RequestParam(required = false) BigDecimal tax, @RequestParam(required = false) BigDecimal total,
                              @RequestParam(required = false) String currency, @RequestParam(required = false) UUID transactionId) {
        return receipts.upload(p.userId(), file, new Meta(receiptNumber, merchant, subtotal, tax, total, currency, transactionId));
    }

    @GetMapping("/receipts") @PreAuthorize("hasRole('USER')")
    public List<ReceiptView> list(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(required = false) UUID transactionId) { return receipts.list(p.userId(), transactionId); }

    @PutMapping("/receipts/{id}") @PreAuthorize("hasRole('USER')")
    public ReceiptView update(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestBody Meta m) { return receipts.update(p.userId(), id, m); }

    @PostMapping("/receipts/{id}/review") @PreAuthorize("hasRole('USER')")
    public ReceiptView review(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody ReviewRequest r) { return receipts.review(p.userId(), id, r.status(), r.note()); }

    @DeleteMapping("/receipts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('USER')")
    public void delete(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { receipts.archive(p.userId(), id); }

    /** Streams through the backend so the object store is never exposed; hardened headers stop browsers sniffing or running content. */
    @GetMapping("/receipts/{id}/file") @PreAuthorize("hasRole('USER')")
    public ResponseEntity<byte[]> file(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean download) {
        Content c = receipts.file(p.userId(), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(c.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, (download ? "attachment" : "inline") + "; filename=\"" + c.originalName().replace("\"", "") + "\"")
                .header("X-Content-Type-Options", "nosniff").header("Content-Security-Policy", "default-src 'none'; sandbox").header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(c.bytes());
    }

    // ---- memos (USER)
    @GetMapping("/memos") @PreAuthorize("hasRole('USER')")
    public List<MemoView> memos(@AuthenticationPrincipal AuthPrincipal p, @RequestParam(required = false) String q, @RequestParam(required = false) String tag,
                                @RequestParam(defaultValue = "false") boolean pinned, @RequestParam(required = false) UUID transactionId) { return memos.list(p.userId(), q, tag, pinned, transactionId); }

    @PostMapping("/memos") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('USER')")
    public MemoView createMemo(@AuthenticationPrincipal AuthPrincipal p, @Valid @RequestBody MemoRequest r) { return memos.create(p.userId(), r); }

    @PutMapping("/memos/{id}") @PreAuthorize("hasRole('USER')")
    public MemoView updateMemo(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id, @Valid @RequestBody MemoRequest r) { return memos.update(p.userId(), id, r); }

    @DeleteMapping("/memos/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('USER')")
    public void deleteMemo(@AuthenticationPrincipal AuthPrincipal p, @PathVariable UUID id) { memos.archive(p.userId(), id); }

    // ---- avatar (USER or ADMIN)
    @PostMapping(value = "/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> avatar(@AuthenticationPrincipal AuthPrincipal p, @RequestPart("file") MultipartFile file) {
        var v = validator.validate(file, Set.of(FileValidator.Kind.JPEG, FileValidator.Kind.PNG, FileValidator.Kind.WEBP), 2L * 1024 * 1024);
        String url = files.storeAvatar(p.userId(), v, files.currentAvatarKey(p.userId()));
        return Map.of("avatarUrl", url == null ? "" : url);
    }

    @DeleteMapping("/users/me/avatar") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeAvatar(@AuthenticationPrincipal AuthPrincipal p) { files.removeAvatar(p.userId()); }
}
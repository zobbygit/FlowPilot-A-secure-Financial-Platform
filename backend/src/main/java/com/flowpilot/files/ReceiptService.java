package com.flowpilot.files;

import com.flowpilot.common.ApiException;
import com.flowpilot.files.FileStorageService.Content;
import com.flowpilot.files.FileValidator.Kind;
import com.flowpilot.files.FileValidator.Validated;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReceiptService {
    public static final long MAX_BYTES = 8L * 1024 * 1024;
    public static final Set<Kind> ALLOWED = Set.of(Kind.JPEG, Kind.PNG, Kind.WEBP, Kind.PDF);

    public record ReceiptView(UUID id, UUID transactionId, UUID fileId, String receiptNumber, String merchant, String subtotal, String tax, String total,
                              String currency, String reviewStatus, String mimeType, String fileName, long sizeBytes, Instant createdAt, List<HistoryItem> history) {}
    public record HistoryItem(String fromStatus, String toStatus, String note, Instant changedAt) {}
    public record Meta(String receiptNumber, String merchant, BigDecimal subtotal, BigDecimal tax, BigDecimal total, String currency, UUID transactionId) {}

    private final JdbcTemplate jdbc;
    private final FileStorageService files;
    private final FileValidator validator;

    public ReceiptService(JdbcTemplate jdbc, FileStorageService files, FileValidator validator) { this.jdbc = jdbc; this.files = files; this.validator = validator; }

    @Transactional
    public ReceiptView upload(UUID userId, MultipartFile file, Meta m) {
        checkMeta(userId, m);
        Validated v = validator.validate(file, ALLOWED, MAX_BYTES);
        if (m.transactionId() != null) {
            Integer dup = jdbc.queryForObject("select count(*) from receipts r join stored_files f on f.id = r.file_id where r.user_id = ? and r.transaction_id = ? and f.sha256 = ? and r.archived_at is null",
                    Integer.class, userId, m.transactionId(), v.sha256());
            if (dup != null && dup > 0) throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RECEIPT", "This exact file is already attached to that transaction.");
        }
        var stored = files.storePrivate(userId, "RECEIPT", v);
        UUID id = jdbc.queryForObject("insert into receipts (user_id, transaction_id, file_id, receipt_number, merchant, subtotal, tax, total, currency) values (?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
                UUID.class, userId, m.transactionId(), stored.id(), blank(m.receiptNumber()), blank(m.merchant()), m.subtotal(), m.tax(), m.total(), m.currency());
        jdbc.update("insert into receipt_status_history (receipt_id, from_status, to_status, note) values (?, null, 'UNREVIEWED', 'Uploaded')", id);
        return one(userId, id);
    }

    public List<ReceiptView> list(UUID userId, UUID transactionId) {
        String sql = BASE + " where r.user_id = ? and r.archived_at is null" + (transactionId == null ? "" : " and r.transaction_id = ?") + " order by r.created_at desc limit 200";
        List<ReceiptView> out = transactionId == null ? jdbc.query(sql, MAPPER, userId) : jdbc.query(sql, MAPPER, userId, transactionId);
        return out.stream().map(r -> withHistory(r)).toList();
    }

    @Transactional
    public ReceiptView update(UUID userId, UUID id, Meta m) {
        checkMeta(userId, m);
        if (jdbc.update("update receipts set receipt_number = ?, merchant = ?, subtotal = ?, tax = ?, total = ?, currency = ?, transaction_id = ? where id = ? and user_id = ? and archived_at is null",
                blank(m.receiptNumber()), blank(m.merchant()), m.subtotal(), m.tax(), m.total(), m.currency(), m.transactionId(), id, userId) == 0) throw ApiException.notFound("Receipt not found.");
        return one(userId, id);
    }

    @Transactional
    public ReceiptView review(UUID userId, UUID id, String status, String note) {
        String from = jdbc.query("select review_status from receipts where id = ? and user_id = ? and archived_at is null for update", (rs, i) -> rs.getString(1), id, userId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Receipt not found."));
        jdbc.update("update receipts set review_status = ? where id = ?", status, id);
        jdbc.update("insert into receipt_status_history (receipt_id, from_status, to_status, note) values (?, ?, ?, ?)", id, from, status, blank(note));
        return one(userId, id);
    }

    public Content file(UUID userId, UUID receiptId) {
        UUID fileId = jdbc.query("select file_id from receipts where id = ? and user_id = ? and archived_at is null", (rs, i) -> rs.getObject(1, UUID.class), receiptId, userId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Receipt not found."));
        return files.read(fileId, userId);
    }

    @Transactional
    public void archive(UUID userId, UUID id) {
        UUID fileId = jdbc.query("select file_id from receipts where id = ? and user_id = ? and archived_at is null", (rs, i) -> rs.getObject(1, UUID.class), id, userId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Receipt not found."));
        jdbc.update("update receipts set archived_at = now() where id = ?", id);
        files.delete(fileId, userId);
    }

    // ------------------------------------------------------------------

    private static final String BASE = "select r.*, f.mime_type, f.original_name, f.size_bytes from receipts r join stored_files f on f.id = r.file_id";
    private static final org.springframework.jdbc.core.RowMapper<ReceiptView> MAPPER = (rs, i) -> new ReceiptView(rs.getObject("id", UUID.class), rs.getObject("transaction_id", UUID.class),
            rs.getObject("file_id", UUID.class), rs.getString("receipt_number"), rs.getString("merchant"), plain(rs.getBigDecimal("subtotal")), plain(rs.getBigDecimal("tax")), plain(rs.getBigDecimal("total")),
            rs.getString("currency"), rs.getString("review_status"), rs.getString("mime_type"), rs.getString("original_name"), rs.getLong("size_bytes"), rs.getTimestamp("created_at").toInstant(), List.of());

    private ReceiptView one(UUID userId, UUID id) {
        return jdbc.query(BASE + " where r.id = ? and r.user_id = ?", MAPPER, id, userId).stream().findFirst().map(this::withHistory).orElseThrow(() -> ApiException.notFound("Receipt not found."));
    }

    private ReceiptView withHistory(ReceiptView r) {
        List<HistoryItem> h = jdbc.query("select from_status, to_status, note, changed_at from receipt_status_history where receipt_id = ? order by changed_at",
                (rs, i) -> new HistoryItem(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), r.id());
        return new ReceiptView(r.id(), r.transactionId(), r.fileId(), r.receiptNumber(), r.merchant(), r.subtotal(), r.tax(), r.total(), r.currency(), r.reviewStatus(), r.mimeType(), r.fileName(), r.sizeBytes(), r.createdAt(), h);
    }

    private void checkMeta(UUID userId, Meta m) {
        if (m.transactionId() != null) {
            Integer n = jdbc.queryForObject("select count(*) from transactions where id = ? and user_id = ?", Integer.class, m.transactionId(), userId);
            if (n == null || n == 0) throw ApiException.notFound("Transaction not found.");
        }
        if (m.currency() != null && !m.currency().matches("[A-Z]{3}")) throw ApiException.badRequest("INVALID_CURRENCY", "Use a 3-letter currency code.");
        for (BigDecimal b : new BigDecimal[]{m.subtotal(), m.tax(), m.total()}) {
            if (b != null && (b.signum() < 0 || b.scale() > 4)) throw ApiException.badRequest("INVALID_AMOUNT", "Amounts must be zero or more with up to 4 decimals.");
        }
        if (m.subtotal() != null && m.tax() != null && m.total() != null && m.subtotal().add(m.tax()).compareTo(m.total()) != 0) {
            throw ApiException.badRequest("TOTAL_MISMATCH", "Subtotal plus tax must equal the total.");
        }
    }

    private static String plain(BigDecimal b) { return b == null ? null : b.toPlainString(); }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}

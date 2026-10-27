package com.flowpilot.reconciliation;

import com.flowpilot.audit.FinancialAudit;
import com.flowpilot.common.ApiException;
import com.flowpilot.csv.CsvMapping;
import com.flowpilot.csv.CsvParser;
import com.flowpilot.csv.RecurringDetector;
import com.flowpilot.finance.FinanceDtos.PostTransactionRequest;
import com.flowpilot.finance.LedgerService;
import com.flowpilot.finance.TransactionType;
import com.flowpilot.reconciliation.ReconciliationDtos.*;
import com.flowpilot.reconciliation.StatementMatcher.Recorded;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconciliation records DECISIONS about statement lines. It never edits, deletes or overwrites an existing transaction;
 * the only way it can create a transaction is the explicit "record this missing line" action, through the normal ledger.
 */
@Service
public class ReconciliationService {
    private final JdbcTemplate jdbc;
    private final LedgerService ledger;

    private final FinancialAudit fin;

    public ReconciliationService(JdbcTemplate jdbc, LedgerService ledger, FinancialAudit fin) {
        this.fin = fin; this.jdbc = jdbc; this.ledger = ledger; }

    // ------------------------------------------------------------------ preview (stateless)

    public Preview preview(byte[] file) {
        List<List<String>> rows = CsvParser.parse(file);
        List<String> first = rows.get(0);
        boolean header = first.stream().anyMatch(c -> c.matches(".*[A-Za-z].*")) && first.stream().noneMatch(c -> c.matches("^[-+(]?[\\p{Sc}\\s]*\\d[\\d.,]*\\)?$"));
        return new Preview(header ? first : null, rows.subList(header ? 1 : 0, Math.min(rows.size(), (header ? 1 : 0) + 8)), rows.size() - (header ? 1 : 0), guess(first, rows, header), header);
    }

    // ------------------------------------------------------------------ import

    @Transactional
    public ImportSummary importStatement(UUID userId, UUID accountId, byte[] file, String fileName, CsvMapping m) {
        var acct = jdbc.queryForList("select name from accounts where id = ? and user_id = ? and archived_at is null", accountId, userId);
        if (acct.isEmpty()) throw ApiException.notFound("Account not found.");
        String sha = sha256(file);
        if (jdbc.queryForObject("select count(*) from statement_imports where user_id = ? and account_id = ? and file_sha256 = ? and status = 'ACTIVE'", Integer.class, userId, accountId, sha) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_IMPORT", "This exact file was already imported for this account. Discard that import first if you want to redo it.");
        }
        List<List<String>> all = CsvParser.parse(file);
        if (!m.valid(all.get(0).size())) throw ApiException.badRequest("INVALID_MAPPING", "Check the column mapping: choose either one amount column or debit/credit columns, valid column numbers, and a date format.");
        List<List<String>> data = all.subList(m.hasHeader() ? 1 : 0, all.size());
        if (data.isEmpty()) throw ApiException.badRequest("CSV_EMPTY", "The file has a header but no rows.");

        UUID importId = jdbc.queryForObject("insert into statement_imports (user_id, account_id, file_sha256, original_name, row_count) values (?, ?, ?, ?, ?) returning id", UUID.class,
                userId, accountId, sha, safe(fileName), data.size());
        Set<String> seen = new HashSet<>();
        Set<UUID> taken = new HashSet<>(jdbc.query("select matched_transaction_id from statement_rows r join statement_imports i on i.id = r.import_id where i.user_id = ? and r.matched_transaction_id is not null", (rs, i) -> rs.getObject(1, UUID.class), userId));
        Set<String> priorRefs = new HashSet<>(jdbc.query("select r.reference from statement_rows r join statement_imports i on i.id = r.import_id where i.user_id = ? and i.account_id = ? and i.status = 'ACTIVE' and r.reference is not null and i.id <> ?", (rs, i) -> rs.getString(1), userId, accountId, importId));

        // candidate pool: recorded transactions on this account across the statement's date span (+/- window)
        List<LocalDate> dates = new ArrayList<>();
        for (List<String> r : data) { LocalDate d = m.parseDate(CsvMapping.cell(r, m.dateCol())); if (d != null) dates.add(d); }
        List<Recorded> pool = dates.isEmpty() ? List.of() : candidates(userId, accountId, Collections.min(dates).minusDays(StatementMatcher.WINDOW_DAYS), Collections.max(dates).plusDays(StatementMatcher.WINDOW_DAYS));

        int no = 0;
        for (List<String> r : data) {
            no++;
            LocalDate date = m.parseDate(CsvMapping.cell(r, m.dateCol()));
            BigDecimal amount = m.signedAmount(r);
            String desc = trunc(CsvMapping.cell(r, m.descCol()), 300), ref = m.refCol() == null ? null : trunc(CsvMapping.cell(r, m.refCol()), 80);
            if (ref != null && ref.isBlank()) ref = null;
            String error = date == null ? "Date not recognised in the chosen format." : amount == null ? "Amount is missing, malformed or ambiguous." : amount.signum() == 0 ? "Amount is zero." : date.isAfter(LocalDate.now(ZoneOffset.UTC).plusDays(1)) ? "Date is in the future." : null;
            if (error != null) { insertRow(importId, no, date, amount, desc, ref, "INVALID", error, null, null, null); continue; }

            String dupKey = date + "|" + amount.stripTrailingZeros().toPlainString() + "|" + desc.toLowerCase() + "|" + (ref == null ? "" : ref);
            boolean repeatInFile = !seen.add(dupKey), repeatRef = ref != null && priorRefs.contains(ref);
            if (repeatInFile || repeatRef) { insertRow(importId, no, date, amount, desc, ref, "POSSIBLE_DUPLICATE", null, null, null, repeatRef ? "Reference already appears in an earlier import." : "Identical line appears more than once in this file."); continue; }

            var match = StatementMatcher.best(date, amount, desc, ref, pool, taken);
            if (match.isPresent()) { taken.add(match.get().transactionId()); insertRow(importId, no, date, amount, desc, ref, "SUGGESTED", null, match.get().score(), match.get().transactionId(), match.get().why()); }
            else insertRow(importId, no, date, amount, desc, ref, "UNMATCHED", null, null, null, "No recorded transaction matches. It may be missing from FlowPilot.");
        }
        fin.record(userId, userId, "STATEMENT_IMPORTED", "IMPORT", importId, safe(fileName) + ": " + data.size() + " lines", null, "ACTIVE");
        return summary(userId, importId);
    }

    // ------------------------------------------------------------------ review

    public List<ImportSummary> imports(UUID userId) {
        return jdbc.query("select id from statement_imports where user_id = ? order by created_at desc limit 50", (rs, i) -> rs.getObject(1, UUID.class), userId).stream().map(id -> summary(userId, id)).toList();
    }

    public RowPage rows(UUID userId, UUID importId, String status, int page, int size) {
        ImportSummary s = summary(userId, importId);
        int sz = Math.min(Math.max(size, 1), 100), pg = Math.max(page, 0);
        boolean f = status != null && status.matches("INVALID|UNMATCHED|SUGGESTED|CONFIRMED|REJECTED|POSSIBLE_DUPLICATE");
        Long total = jdbc.queryForObject("select count(*) from statement_rows where import_id = ?" + (f ? " and match_status = ?" : ""), Long.class, f ? new Object[]{importId, status} : new Object[]{importId});
        String sql = "select r.*, coalesce(t.merchant, t.description, lower(t.type)) as sug_label from statement_rows r left join transactions t on t.id = r.suggested_transaction_id where r.import_id = ?" + (f ? " and r.match_status = ?" : "") + " order by r.row_no limit ? offset ?";
        List<RowView> rows = jdbc.query(sql, (rs, i) -> new RowView(rs.getObject("id", UUID.class), rs.getInt("row_no"), rs.getObject("entry_date", LocalDate.class), plain(rs.getBigDecimal("amount")), rs.getString("description"), rs.getString("reference"),
                rs.getString("match_status"), rs.getString("validation_error"), (Integer) rs.getObject("score"), rs.getString("note"), rs.getObject("suggested_transaction_id", UUID.class), rs.getString("sug_label"), rs.getObject("matched_transaction_id", UUID.class)),
                f ? new Object[]{importId, status, sz, (long) pg * sz} : new Object[]{importId, sz, (long) pg * sz});
        return new RowPage(s, rows, pg, sz, total == null ? 0 : total, pg == 0 ? missing(userId, importId) : List.of());
    }

    /** Link a statement row to a recorded transaction. Never changes the transaction itself. */
    @Transactional
    public RowView confirm(UUID userId, UUID rowId, UUID transactionId) {
        var row = ownedRow(userId, rowId);
        if (!List.of("SUGGESTED", "UNMATCHED", "REJECTED", "POSSIBLE_DUPLICATE").contains(row.get("match_status"))) throw new ApiException(HttpStatus.CONFLICT, "NOT_CONFIRMABLE", "This line can't be confirmed in its current state.");
        UUID tx = transactionId != null ? transactionId : (UUID) row.get("suggested_transaction_id");
        if (tx == null) throw ApiException.badRequest("TRANSACTION_REQUIRED", "Choose which recorded transaction this line matches.");
        UUID account = (UUID) row.get("account_id");
        if (jdbc.queryForObject("select count(*) from ledger_entries e join transactions t on t.id = e.transaction_id where t.id = ? and t.user_id = ? and e.account_id = ? and e.amount = ?", Integer.class, tx, userId, account, row.get("amount")) == 0) {
            throw ApiException.badRequest("NO_MATCH", "That transaction doesn't have the same amount on this account.");
        }
        try { jdbc.update("update statement_rows set match_status = 'CONFIRMED', matched_transaction_id = ?, decided_at = now(), note = 'Confirmed by you' where id = ?", tx, rowId); }
        catch (org.springframework.dao.DataIntegrityViolationException e) { throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MATCHED", "That transaction is already matched to another statement line."); }
        fin.record(userId, userId, "RECONCILIATION_CONFIRMED", "STATEMENT_ROW", rowId, "Matched to transaction " + tx, String.valueOf(row.get("match_status")), "CONFIRMED");
        return one(userId, rowId);
    }

    @Transactional
    public RowView reject(UUID userId, UUID rowId) {
        var row = ownedRow(userId, rowId);
        if (row.get("match_status").equals("CONFIRMED")) throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CONFIRMED", "Undo the confirmation first.");
        jdbc.update("update statement_rows set match_status = 'REJECTED', suggested_transaction_id = null, decided_at = now(), note = 'Rejected by you' where id = ?", rowId);
        fin.record(userId, userId, "RECONCILIATION_REJECTED", "STATEMENT_ROW", rowId, null, String.valueOf(row.get("match_status")), "REJECTED");
        return one(userId, rowId);
    }

    @Transactional
    public RowView undo(UUID userId, UUID rowId) {
        var row = ownedRow(userId, rowId);
        if (!List.of("CONFIRMED", "REJECTED").contains(row.get("match_status"))) throw new ApiException(HttpStatus.CONFLICT, "NOTHING_TO_UNDO", "Only confirmed or rejected lines can be reopened.");
        jdbc.update("update statement_rows set match_status = 'UNMATCHED', matched_transaction_id = null, decided_at = now(), note = 'Reopened by you' where id = ?", rowId);
        return one(userId, rowId);
    }

    /** Explicit, user-initiated: record a statement line that FlowPilot is missing, via the normal ledger rules. */
    @Transactional
    public RowView recordMissing(UUID userId, UUID rowId) {
        var row = ownedRow(userId, rowId);
        if (!List.of("UNMATCHED", "REJECTED").contains(row.get("match_status"))) throw new ApiException(HttpStatus.CONFLICT, "NOT_RECORDABLE", "Only unmatched lines can be recorded as new transactions.");
        BigDecimal amount = (BigDecimal) row.get("amount");
        var tx = ledger.post(userId, new PostTransactionRequest(amount.signum() > 0 ? TransactionType.INCOME : TransactionType.EXPENSE, amount.abs(), ((java.sql.Date) row.get("entry_date")).toLocalDate(), (UUID) row.get("account_id"), null, null,
                trunc((String) row.get("description"), 200), null, "statement", "Recorded from statement import"), null);
        jdbc.update("update statement_rows set match_status = 'CONFIRMED', matched_transaction_id = ?, decided_at = now(), note = 'Recorded from the statement line' where id = ?", tx.id(), rowId);
        fin.record(userId, userId, "RECONCILIATION_RECORDED", "STATEMENT_ROW", rowId, "Recorded new transaction " + tx.id(), "UNMATCHED", "CONFIRMED");
        return one(userId, rowId);
    }

    @Transactional
    public void discard(UUID userId, UUID importId) {
        if (jdbc.update("update statement_imports set status = 'DISCARDED' where id = ? and user_id = ? and status = 'ACTIVE'", importId, userId) == 0) throw ApiException.notFound("Import not found.");
        jdbc.update("update statement_rows set match_status = 'UNMATCHED', matched_transaction_id = null where import_id = ? and match_status = 'CONFIRMED'", importId);   // frees transactions; the transactions themselves are untouched
    }

    // ------------------------------------------------------------------ recurring detection (stateless, creates nothing)

    public List<RecurringDetector.Candidate> detectRecurring(byte[] file, CsvMapping m) {
        List<List<String>> all = CsvParser.parse(file);
        if (!m.valid(all.get(0).size())) throw ApiException.badRequest("INVALID_MAPPING", "Check the column mapping.");
        List<RecurringDetector.Entry> entries = new ArrayList<>();
        for (List<String> r : all.subList(m.hasHeader() ? 1 : 0, all.size())) {
            LocalDate d = m.parseDate(CsvMapping.cell(r, m.dateCol())); BigDecimal a = m.signedAmount(r);
            if (d != null && a != null) entries.add(new RecurringDetector.Entry(d, a, CsvMapping.cell(r, m.descCol())));
        }
        return RecurringDetector.detect(entries);
    }

    // ------------------------------------------------------------------ internals

    private List<Recorded> candidates(UUID userId, UUID accountId, LocalDate from, LocalDate to) {
        return jdbc.query("select t.id, t.occurred_on, e.amount, coalesce(t.merchant, '') || ' ' || coalesce(t.description, '') as text from ledger_entries e join transactions t on t.id = e.transaction_id "
                + "where t.user_id = ? and e.account_id = ? and t.occurred_on between ? and ? and t.status = 'POSTED' and t.archived_at is null and t.type <> 'OPENING_BALANCE'",
                (rs, i) -> new Recorded(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getBigDecimal(3), rs.getString(4)), userId, accountId, from, to);
    }

    private List<MissingView> missing(UUID userId, UUID importId) {
        return jdbc.query("select t.id, t.occurred_on, coalesce(t.merchant, t.description, lower(t.type)) as label, e.amount from statement_imports i "
                + "join ledger_entries e on e.account_id = i.account_id join transactions t on t.id = e.transaction_id "
                + "where i.id = ? and i.user_id = ? and t.user_id = i.user_id and t.status = 'POSTED' and t.archived_at is null and t.type <> 'OPENING_BALANCE' "
                + "and t.occurred_on between (select min(entry_date) from statement_rows where import_id = i.id) and (select max(entry_date) from statement_rows where import_id = i.id) "
                + "and not exists (select 1 from statement_rows r where r.import_id = i.id and (r.matched_transaction_id = t.id or r.suggested_transaction_id = t.id)) order by t.occurred_on limit 100",
                (rs, i) -> new MissingView(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getString(3), rs.getBigDecimal(4).toPlainString()), importId, userId);
    }

    private ImportSummary summary(UUID userId, UUID importId) {
        return jdbc.query("select i.*, a.name as account_name, "
                + "count(r.id) filter (where r.match_status = 'INVALID') as inv, count(r.id) filter (where r.match_status = 'SUGGESTED') as sug, count(r.id) filter (where r.match_status = 'UNMATCHED') as unm, "
                + "count(r.id) filter (where r.match_status = 'POSSIBLE_DUPLICATE') as dup, count(r.id) filter (where r.match_status = 'CONFIRMED') as con, count(r.id) filter (where r.match_status = 'REJECTED') as rej "
                + "from statement_imports i join accounts a on a.id = i.account_id left join statement_rows r on r.import_id = i.id where i.id = ? and i.user_id = ? group by i.id, a.name",
                (rs, n) -> new ImportSummary(rs.getObject("id", UUID.class), rs.getObject("account_id", UUID.class), rs.getString("account_name"), rs.getString("original_name"), rs.getInt("row_count"), rs.getInt("inv"), rs.getInt("sug"), rs.getInt("unm"),
                        rs.getInt("dup"), rs.getInt("con"), rs.getInt("rej"), rs.getTimestamp("created_at").toInstant(), rs.getString("status")), importId, userId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Import not found."));
    }

    private Map<String, Object> ownedRow(UUID userId, UUID rowId) {
        var rows = jdbc.queryForList("select r.*, i.account_id from statement_rows r join statement_imports i on i.id = r.import_id where r.id = ? and i.user_id = ? and i.status = 'ACTIVE' for update of r", rowId, userId);
        if (rows.isEmpty()) throw ApiException.notFound("Statement line not found.");
        return rows.get(0);
    }

    private RowView one(UUID userId, UUID rowId) {
        UUID importId = jdbc.queryForObject("select import_id from statement_rows where id = ?", UUID.class, rowId);
        return rows(userId, importId, null, 0, 100).rows().stream().filter(r -> r.id().equals(rowId)).findFirst().orElseGet(() -> {
            var all = jdbc.query("select r.*, null as sug_label from statement_rows r where r.id = ?", (rs, i) -> new RowView(rs.getObject("id", UUID.class), rs.getInt("row_no"), rs.getObject("entry_date", LocalDate.class), plain(rs.getBigDecimal("amount")), rs.getString("description"), rs.getString("reference"),
                    rs.getString("match_status"), rs.getString("validation_error"), (Integer) rs.getObject("score"), rs.getString("note"), rs.getObject("suggested_transaction_id", UUID.class), null, rs.getObject("matched_transaction_id", UUID.class)), rowId);
            return all.get(0);
        });
    }

    private void insertRow(UUID importId, int no, LocalDate date, BigDecimal amount, String desc, String ref, String status, String error, Integer score, UUID suggested, String note) {
        jdbc.update("insert into statement_rows (import_id, row_no, entry_date, amount, description, reference, validation_error, match_status, suggested_transaction_id, score, note) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                importId, no, date, amount, desc, ref, error, status, suggested, score, note);
    }

    static ReconciliationDtos.GuessedMapping guess(List<String> first, List<List<String>> rows, boolean header) {
        Integer date = null, desc = null, amount = null, debit = null, credit = null, ref = null;
        if (header) for (int i = 0; i < first.size(); i++) {
            String h = first.get(i).toLowerCase(Locale.ROOT);
            if (date == null && h.contains("date")) date = i;
            else if (desc == null && (h.contains("desc") || h.contains("narration") || h.contains("details") || h.contains("payee") || h.contains("merchant") || h.contains("memo"))) desc = i;
            else if (debit == null && (h.contains("debit") || h.contains("withdraw"))) debit = i;
            else if (credit == null && (h.contains("credit") || h.contains("deposit"))) credit = i;
            else if (amount == null && h.contains("amount")) amount = i;
            else if (ref == null && (h.contains("ref") || h.contains("id") || h.contains("check"))) ref = i;
        }
        if (amount != null) { debit = null; credit = null; }
        String sample = rows.size() > (header ? 1 : 0) ? CsvMapping.cell(rows.get(header ? 1 : 0), date) : "";
        String fmt = sample.matches("\\d{4}-\\d{2}-\\d{2}") ? "YMD_DASH" : sample.matches("\\d{2}\\.\\d{2}\\.\\d{4}") ? "DMY_DOT" : sample.matches("\\d{2}-\\d{2}-\\d{4}") ? "DMY_DASH" : sample.matches("\\d{2}/\\d{2}/\\d{4}") ? "DMY_SLASH" : "YMD_DASH";
        return new ReconciliationDtos.GuessedMapping(date, desc, amount, debit, credit, ref, fmt, ".");
    }

    private static String sha256(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static String safe(String n) { return n == null ? null : trunc(n.replaceAll("[^A-Za-z0-9._ -]", "_"), 120); }
    private static String trunc(String s, int n) { return s == null ? "" : s.length() > n ? s.substring(0, n) : s; }
    private static String plain(BigDecimal b) { return b == null ? null : b.toPlainString(); }
}

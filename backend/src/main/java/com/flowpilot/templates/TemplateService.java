package com.flowpilot.templates;

import com.flowpilot.common.ApiException;
import com.flowpilot.files.MemoService;
import com.flowpilot.files.ReceiptService;
import com.flowpilot.finance.FinanceDtos.PostTransactionRequest;
import com.flowpilot.finance.FinanceDtos.TransactionView;
import com.flowpilot.finance.LedgerService;
import com.flowpilot.finance.TransactionType;
import com.flowpilot.planning.BillService;
import com.flowpilot.planning.PlanningDtos.BillRequest;
import com.flowpilot.templates.TemplateDtos.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Templates are reusable recording FORMS. Creating a record from a template is validated twice: against the template's
 * own field rules, then by the normal domain services (ledger, memos, bills) - the template can never bypass them.
 */
@Service
public class TemplateService {
    private static final Map<String, Set<String>> BINDINGS = Map.of(
            "TRANSACTION", Set.of("type", "amount", "tax", "description", "merchant", "categoryId", "paymentMethod", "accountId", "occurredOn", "reference", "notes", "receiptFile"),
            "MEMO", Set.of("title", "body", "tags", "followUpDate", "transactionId"),
            "BILL", Set.of("name", "amount", "frequency", "nextDueDate", "accountId", "reminderDays", "notes", "categoryId"));
    private static final Map<String, Set<String>> REQUIRED_BINDINGS = Map.of(
            "TRANSACTION", Set.of("amount", "accountId"), "MEMO", Set.of("title"), "BILL", Set.of("name", "amount", "frequency", "nextDueDate", "accountId"));
    private static final Pattern AMOUNT = Pattern.compile("^\\d{1,15}(\\.\\d{1,4})?$");

    private final JdbcTemplate jdbc;
    private final LedgerService ledger;
    private final MemoService memos;
    private final BillService bills;
    private final ReceiptService receipts;

    public TemplateService(JdbcTemplate jdbc, LedgerService ledger, MemoService memos, BillService bills, ReceiptService receipts) {
        this.jdbc = jdbc; this.ledger = ledger; this.memos = memos; this.bills = bills; this.receipts = receipts;
    }

    public List<TemplateView> list(UUID userId, boolean includeArchived) {
        return jdbc.query("select * from finance_templates where (user_id is null or user_id = ?)" + (includeArchived ? "" : " and archived_at is null") + " order by (user_id is null) desc, sort_order, name",
                (rs, i) -> load(rs), userId);
    }

    @Transactional
    public TemplateView create(UUID userId, TemplateRequest r) {
        validateDefinition(r);
        UUID id = jdbc.queryForObject("insert into finance_templates (user_id, name, description, target_type, fixed_type) values (?, ?, ?, ?, ?) returning id", UUID.class,
                userId, r.name().trim(), blank(r.description()), r.targetType(), "TRANSACTION".equals(r.targetType()) ? r.fixedType() : null);
        writeFields(id, r.fields());
        return get(userId, id);
    }

    @Transactional
    public TemplateView update(UUID userId, UUID id, TemplateRequest r) {
        validateDefinition(r);
        if (jdbc.update("update finance_templates set name = ?, description = ?, target_type = ?, fixed_type = ?, updated_at = now() where id = ? and user_id = ? and archived_at is null",
                r.name().trim(), blank(r.description()), r.targetType(), "TRANSACTION".equals(r.targetType()) ? r.fixedType() : null, id, userId) == 0) throw ApiException.notFound("Template not found (built-in templates are read-only; duplicate them to edit).");
        jdbc.update("delete from template_fields where template_id = ?", id);
        writeFields(id, r.fields());
        return get(userId, id);
    }

    @Transactional
    public TemplateView duplicate(UUID userId, UUID id) {
        TemplateView t = get(userId, id);
        return create(userId, new TemplateRequest(t.name() + " (copy)", t.description(), t.targetType(), t.fixedType(), t.fields()));
    }

    @Transactional
    public void setArchived(UUID userId, UUID id, boolean archived) {
        if (jdbc.update("update finance_templates set archived_at = " + (archived ? "now()" : "null") + " where id = ? and user_id = ?", id, userId) == 0) throw ApiException.notFound("Template not found.");
    }

    /** Validates the submitted values against the template, then hands off to the domain service. */
    @Transactional
    public CreatedRecord createRecord(UUID userId, UUID templateId, Map<String, String> raw, MultipartFile file) {
        TemplateView t = get(userId, templateId);
        if (t.archived()) throw ApiException.badRequest("TEMPLATE_ARCHIVED", "Restore this template before using it.");
        Map<String, FieldDef> byKey = new LinkedHashMap<>();
        t.fields().forEach(f -> byKey.put(f.key(), f));
        for (String k : raw.keySet()) if (!byKey.containsKey(k)) throw ApiException.badRequest("UNKNOWN_FIELD", "Unknown field: " + k);

        Map<String, String> byBinding = new HashMap<>();
        for (FieldDef f : t.fields()) {
            String v = raw.get(f.key());
            if ((v == null || v.isBlank()) && f.defaultValue() != null && !f.defaultValue().isBlank() && !"FILE".equals(f.type())) v = "today".equals(f.defaultValue()) ? LocalDate.now(ZoneOffset.UTC).toString() : f.defaultValue();
            v = clean(f, v, file);
            if (v != null) byBinding.put(f.binding(), v);
        }
        return switch (t.targetType()) {
            case "TRANSACTION" -> transaction(userId, t, byBinding, file);
            case "MEMO" -> memo(userId, byBinding);
            default -> bill(userId, byBinding);
        };
    }

    // ------------------------------------------------------------------ record creation

    private CreatedRecord transaction(UUID userId, TemplateView t, Map<String, String> b, MultipartFile file) {
        TransactionType type = t.fixedType() != null ? TransactionType.valueOf(t.fixedType()) : parseType(b.get("type"));
        StringBuilder notes = new StringBuilder(b.getOrDefault("notes", ""));
        if (b.get("tax") != null) notes.append(notes.length() > 0 ? "\n" : "").append("Tax included: ").append(b.get("tax"));
        if (b.get("reference") != null) notes.append(notes.length() > 0 ? "\n" : "").append("Reference: ").append(b.get("reference"));
        String notesText = notes.length() == 0 ? null : notes.length() > 2000 ? notes.substring(0, 2000) : notes.toString();
        LocalDate date = b.get("occurredOn") == null ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(b.get("occurredOn"));
        TransactionView tx = ledger.post(userId, new PostTransactionRequest(type, new BigDecimal(b.get("amount")), date, UUID.fromString(b.get("accountId")), null,
                b.get("categoryId") == null ? null : UUID.fromString(b.get("categoryId")), trunc(b.get("description"), 200), trunc(b.get("merchant"), 120), trunc(b.get("paymentMethod"), 40), notesText), null);
        if (file != null && !file.isEmpty() && t.fields().stream().anyMatch(f -> "receiptFile".equals(f.binding()))) {
            receipts.upload(userId, file, new ReceiptService.Meta(null, b.get("merchant"), null, b.get("tax") == null ? null : new BigDecimal(b.get("tax")), new BigDecimal(b.get("amount")), null, tx.id()));
        }
        return new CreatedRecord("TRANSACTION", tx.id());
    }

    private CreatedRecord memo(UUID userId, Map<String, String> b) {
        var m = memos.create(userId, new MemoService.MemoRequest(b.get("title"), b.get("body"), b.get("tags"), false,
                b.get("followUpDate") == null ? null : LocalDate.parse(b.get("followUpDate")), b.get("transactionId") == null ? null : UUID.fromString(b.get("transactionId")), null));
        return new CreatedRecord("MEMO", m.id());
    }

    private CreatedRecord bill(UUID userId, Map<String, String> b) {
        UUID account = UUID.fromString(b.get("accountId"));
        String currency = jdbc.query("select currency from accounts where id = ? and user_id = ? and archived_at is null", (rs, i) -> rs.getString(1), account, userId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Account not found."));
        var v = bills.create(userId, new BillRequest(b.get("name"), "BILL", new BigDecimal(b.get("amount")), currency, b.get("frequency"), LocalDate.parse(b.get("nextDueDate")),
                b.get("categoryId") == null ? null : UUID.fromString(b.get("categoryId")), account, b.get("reminderDays") == null ? 3 : Integer.parseInt(b.get("reminderDays")), b.get("notes")));
        return new CreatedRecord("BILL", v.id());
    }

    // ------------------------------------------------------------------ validation

    /** Returns the cleaned value or null when optional and empty. Throws a field-specific error otherwise. */
    String clean(FieldDef f, String v, MultipartFile file) {
        boolean empty = v == null || v.isBlank();
        if ("FILE".equals(f.type())) {
            if (f.required() && (file == null || file.isEmpty())) throw fieldError(f, "is required");
            return null;   // the file itself is validated by FileValidator when stored
        }
        if (empty) {
            if ("CHECKBOX".equals(f.type())) return "false";
            if (f.required()) throw fieldError(f, "is required");
            return null;
        }
        v = v.trim();
        switch (f.type()) {
            case "TEXT" -> {
                int max = f.max() == null ? 200 : f.max().intValue(), min = f.min() == null ? 0 : f.min().intValue();
                if (v.length() > max) throw fieldError(f, "must be at most " + max + " characters");
                if (v.length() < min) throw fieldError(f, "must be at least " + min + " characters");
            }
            case "AMOUNT" -> {
                if (!AMOUNT.matcher(v).matches()) throw fieldError(f, "must be a valid amount (up to 4 decimals)");
                BigDecimal n = new BigDecimal(v);
                if (f.min() != null && n.compareTo(f.min()) < 0) throw fieldError(f, "must be at least " + f.min().toPlainString());
                if (f.max() != null && n.compareTo(f.max()) > 0) throw fieldError(f, "must be at most " + f.max().toPlainString());
            }
            case "NUMBER" -> {
                if (!v.matches("^\\d{1,9}$")) throw fieldError(f, "must be a whole number");
                long n = Long.parseLong(v);
                if (f.min() != null && n < f.min().longValue()) throw fieldError(f, "must be at least " + f.min().toPlainString());
                if (f.max() != null && n > f.max().longValue()) throw fieldError(f, "must be at most " + f.max().toPlainString());
            }
            case "DATE" -> { try { LocalDate.parse(v); } catch (RuntimeException e) { throw fieldError(f, "must be a valid date (YYYY-MM-DD)"); } }
            case "DROPDOWN" -> { if (!optionValues(f.options()).contains(v)) throw fieldError(f, "must be one of the listed options"); }
            case "CHECKBOX" -> { if (!v.equals("true") && !v.equals("false")) throw fieldError(f, "must be true or false"); }
            case "TAGS" -> { if (v.length() > 300) throw fieldError(f, "is too long"); }
            case "CATEGORY", "ACCOUNT", "TRANSACTION" -> { try { UUID.fromString(v); } catch (IllegalArgumentException e) { throw fieldError(f, "is not valid"); } }
            default -> throw fieldError(f, "has an unsupported type");
        }
        return v;
    }

    static List<String> optionValues(String options) {
        if (options == null || options.isBlank()) return List.of();
        return Arrays.stream(options.split(",")).map(o -> o.contains("|") ? o.substring(0, o.indexOf('|')) : o).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    void validateDefinition(TemplateRequest r) {
        Set<String> allowed = BINDINGS.get(r.targetType());
        Set<String> keys = new HashSet<>(), bindings = new HashSet<>();
        for (FieldDef f : r.fields()) {
            if (!keys.add(f.key())) throw ApiException.badRequest("DUPLICATE_FIELD", "Each field needs a unique key: " + f.key());
            if (!allowed.contains(f.binding())) throw ApiException.badRequest("INVALID_BINDING", "“" + f.label() + "” can't fill “" + f.binding() + "” in a " + r.targetType().toLowerCase() + " template.");
            if (!bindings.add(f.binding())) throw ApiException.badRequest("DUPLICATE_BINDING", "Two fields fill the same target (" + f.binding() + ").");
            if ("DROPDOWN".equals(f.type()) && optionValues(f.options()).isEmpty()) throw ApiException.badRequest("OPTIONS_REQUIRED", "“" + f.label() + "” needs at least one option.");
            if ("DROPDOWN".equals(f.type()) && "type".equals(f.binding()) && !new HashSet<>(List.of("INCOME", "EXPENSE")).containsAll(optionValues(f.options()))) throw ApiException.badRequest("INVALID_OPTIONS", "The transaction type can only be INCOME or EXPENSE.");
            if ("DROPDOWN".equals(f.type()) && "frequency".equals(f.binding()) && !new HashSet<>(List.of("WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY")).containsAll(optionValues(f.options()))) throw ApiException.badRequest("INVALID_OPTIONS", "Frequency options must be WEEKLY, MONTHLY, QUARTERLY or YEARLY.");
            if (f.min() != null && f.max() != null && f.min().compareTo(f.max()) > 0) throw ApiException.badRequest("INVALID_RANGE", "“" + f.label() + "”: minimum is above maximum.");
            if (f.defaultValue() != null && !f.defaultValue().isBlank() && !"today".equals(f.defaultValue())) clean(f, f.defaultValue(), null);   // a default must itself be valid
            if ("FILE".equals(f.type()) && !"receiptFile".equals(f.binding())) throw ApiException.badRequest("INVALID_BINDING", "File fields can only attach a receipt.");
        }
        for (String need : REQUIRED_BINDINGS.get(r.targetType())) if (!bindings.contains(need)) throw ApiException.badRequest("MISSING_FIELD", "A " + r.targetType().toLowerCase() + " template needs a field for “" + need + "”.");
        if ("TRANSACTION".equals(r.targetType()) && r.fixedType() == null && !bindings.contains("type")) throw ApiException.badRequest("MISSING_FIELD", "Fix the type to income or expense, or add a field that lets the user choose.");
        for (FieldDef f : r.fields()) {   // required-ness of binding-critical fields
            if (REQUIRED_BINDINGS.get(r.targetType()).contains(f.binding()) && !f.required()) throw ApiException.badRequest("MUST_BE_REQUIRED", "“" + f.label() + "” must be marked required.");
        }
    }

    private TemplateView get(UUID userId, UUID id) {
        return jdbc.query("select * from finance_templates where id = ? and (user_id is null or user_id = ?)", (rs, i) -> load(rs), id, userId).stream().findFirst().orElseThrow(() -> ApiException.notFound("Template not found."));
    }

    private TemplateView load(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<FieldDef> fields = jdbc.query("select * from template_fields where template_id = ? order by position", (r, i) -> new FieldDef(r.getString("field_key"), r.getString("label"), r.getString("field_type"),
                r.getString("binding"), r.getBoolean("required"), r.getString("options"), r.getString("default_value"), r.getBigDecimal("min_value"), r.getBigDecimal("max_value")), id);
        return new TemplateView(id, rs.getString("name"), rs.getString("description"), rs.getString("target_type"), rs.getString("fixed_type"), rs.getObject("user_id") == null, rs.getTimestamp("archived_at") != null, fields);
    }

    private void writeFields(UUID id, List<FieldDef> fields) {
        int pos = 1;
        for (FieldDef f : fields) {
            jdbc.update("insert into template_fields (template_id, position, field_key, label, field_type, binding, required, options, default_value, min_value, max_value) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    id, pos++, f.key(), f.label().trim(), f.type(), f.binding(), f.required(), blank(f.options()), blank(f.defaultValue()), f.min(), f.max());
        }
    }

    private static ApiException fieldError(FieldDef f, String msg) { return ApiException.badRequest("INVALID_FIELD", "“" + f.label() + "” " + msg + "."); }
    private static TransactionType parseType(String s) {
        if (!"INCOME".equals(s) && !"EXPENSE".equals(s)) throw ApiException.badRequest("INVALID_FIELD", "Choose whether this is income or an expense.");
        return TransactionType.valueOf(s);
    }
    private static String blank(String s) { return s == null || s.isBlank() ? null : s.trim(); }
    private static String trunc(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
}
